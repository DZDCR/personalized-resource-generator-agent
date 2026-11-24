package com.example.learning.service;

import com.example.learning.common.BizException;
import com.example.learning.common.RangeHeader;
import com.example.learning.common.RangeNotSatisfiableException;
import com.example.learning.config.UploadProperties;
import com.example.learning.dto.response.UploadFileSummary;
import com.example.learning.dto.upload.TusdHookRequest;
import com.example.learning.dto.upload.TusdHookResponse;
import com.example.learning.entity.UploadFileEntity;
import com.example.learning.repository.UploadFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 上传业务服务：tusd Hook 校验、上传记录落库、文件查询/下载/删除。
 *
 * 职责边界：
 * - 不接收文件字节流（字节由 nginx → tusd → RustFS）；
 * - Hook 内只做校验与元数据落库，耗时操作（抽取/向量化）留待后续异步任务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadFileService {

    private static final int MAX_FILE_NAME_LENGTH = 255;

    private final UploadFileRepository repository;
    private final UploadTokenService tokenService;
    private final RustFsStorageService storageService;
    private final UploadProperties properties;

    // ------------------------------------------------------------------ Hooks

    /**
     * pre-create：tusd 创建上传前的同步校验，失败直接拒绝（客户端收到 4xx）。
     * 通过时可改写元数据，剔除凭证等敏感字段。
     */
    public TusdHookResponse preCreate(TusdHookRequest request) {
        TusdHookRequest.Upload upload = requireUpload(request);
        Map<String, String> meta = safeMeta(upload);
        try {
            long studentId = resolveStudentId(meta, request);
            validateSize(upload);
            String fileName = sanitizeFileName(meta.get("filename"));
            validateExtension(fileName);

            Map<String, String> persisted = new LinkedHashMap<>();
            persisted.put("filename", fileName);
            persisted.put("filetype", Optional.ofNullable(meta.get("filetype"))
                    .filter(v -> !v.isBlank())
                    .orElse("application/octet-stream"));
            persisted.put("studentId", Long.toString(studentId));
            // 不把 uploadToken 回写：凭证只在本次请求里使用，避免长期留在 tusd 的 .info 中
            return TusdHookResponse.changeMeta(persisted);
        } catch (BizException ex) {
            log.warn("拒绝上传: studentId={} file={} reason={}",
                    meta.get("studentId"), meta.get("filename"), ex.getMessage());
            return TusdHookResponse.reject(normalizeStatus(ex.getCode()), ex.getMessage());
        } catch (Exception ex) {
            log.error("pre-create 处理异常", ex);
            return TusdHookResponse.reject(500, "上传校验失败，请稍后重试");
        }
    }

    /** post-create：上传 ID 已生成，先落一条 UPLOADING 记录。 */
    public void postCreate(TusdHookRequest request) {
        TusdHookRequest.Upload upload = requireUpload(request);
        if (upload.getId() == null) {
            return;
        }
        if (repository.findByUploadId(upload.getId()).isPresent()) {
            return; // tusd 重试导致的重复回调
        }
        repository.save(newEntity(upload, UploadFileEntity.UploadStatus.UPLOADING));
        log.info("上传开始 uploadId={} file={} size={}", upload.getId(),
                safeMeta(upload).get("filename"), upload.getSize());
    }

    /** post-finish：服务端分片合并完成，补齐对象位置并置为 COMPLETED。 */
    public void postFinish(TusdHookRequest request) {
        TusdHookRequest.Upload upload = requireUpload(request);
        if (upload.getId() == null) {
            return;
        }
        UploadFileEntity entity = repository.findByUploadId(upload.getId())
                .orElseGet(() -> newEntity(upload, UploadFileEntity.UploadStatus.COMPLETED));

        Map<String, String> storage = upload.getStorage();
        if (storage != null && storage.get("Key") != null) {
            entity.setStorageType(storage.get("Type"));
            entity.setBucket(storage.get("Bucket"));
            entity.setObjectKey(storage.get("Key"));
        }
        if (upload.getSize() != null) {
            entity.setSizeBytes(upload.getSize());
        } else if (upload.getOffset() != null) {
            entity.setSizeBytes(upload.getOffset());
        }
        entity.setOffsetBytes(upload.getOffset());
        if (upload.getSize() != null && upload.getOffset() != null
                && !upload.getSize().equals(upload.getOffset())) {
            log.warn("上传结束但 offset 与 size 不一致，可能存在截断 uploadId={} size={} offset={}",
                    entity.getUploadId(), upload.getSize(), upload.getOffset());
        }
        entity.setStatus(UploadFileEntity.UploadStatus.COMPLETED);
        entity.setFinishedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);

        log.info("上传完成 uploadId={} studentId={} size={} objectKey={}",
                entity.getUploadId(), entity.getStudentId(), entity.getSizeBytes(), entity.getObjectKey());
    }

    /** post-terminate：客户端取消上传，tusd 已清理分片。 */
    @Transactional
    public void postTerminate(TusdHookRequest request) {
        TusdHookRequest.Upload upload = requireUpload(request);
        if (upload.getId() == null) {
            return;
        }
        // 业务删除会主动调用 tusd DELETE，而 tusd 在返回 204 之后才异步回抛 post-terminate；
        // 若此处“先查再改”，会把刚写入的 DELETED 覆盖回 TERMINATED，
        // 导致已删除文件重新出现在列表里。改用条件更新，由 SQL 判断当前状态。
        int updated = repository.updateStatusUnless(upload.getId(),
                UploadFileEntity.UploadStatus.TERMINATED,
                UploadFileEntity.UploadStatus.DELETED,
                Instant.now());
        if (updated > 0) {
            log.info("上传已终止 uploadId={}", upload.getId());
        } else {
            log.debug("忽略 terminate 回调：记录已逻辑删除 uploadId={}", upload.getId());
        }
    }

    // ------------------------------------------------------------- 业务查询接口

    public List<UploadFileSummary> listByStudent(Long studentId) {
        // 列表是「已上传文件」，只展示可下载的完成项：
        // UPLOADING 是断点未续完的残留（续传靠前端 tus uploadUrl，不在列表里操作），
        // DELETED 业务已删除，TERMINATED 客户端中途取消，均无可用对象。
        // 状态过滤下推到 SQL，避免把该学生的全部历史记录取回内存再筛。
        return repository
                .findByStudentIdAndStatusOrderByCreatedAtDesc(studentId, UploadFileEntity.UploadStatus.COMPLETED)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    /**
     * 打开下载流，支持 HTTP Range 与断点续传。
     *
     * <p>Range 不直接透传给 S3，先由 {@link RangeHeader} 解析并做三件事：
     * <ol>
     *   <li>把不支持的形态（多段、非法格式）降级成「无 Range」，避免 S3 返回 400 变成 500；</li>
     *   <li>越界起点提前抛 416，让 Controller 回真正的 416 而不是 500；</li>
     *   <li>校验 {@code If-Range}：文件被替换时必须回全量，否则客户端会把新文件的片段
     *       拼在旧文件后面，得到一个大小正确、内容损坏的文件。</li>
     * </ol>
     *
     * @param rangeHeader 原始 {@code Range} 头，可为 null
     * @param ifRange     原始 {@code If-Range} 头，可为 null
     * @throws RangeNotSatisfiableException 范围起点越过文件末尾（需由 Controller 转 416）
     */
    public DownloadPayload openDownload(Long id, Long studentId, String rangeHeader, String ifRange) {
        UploadFileEntity entity = get(id, studentId);
        if (entity.getStatus() != UploadFileEntity.UploadStatus.COMPLETED || entity.getObjectKey() == null) {
            throw new BizException(409, "文件尚未上传完成，无法下载");
        }

        Long totalSize = entity.getSizeBytes();
        String knownEtag = null;
        boolean hasIfRange = ifRange != null && !ifRange.isBlank();
        // 只有需要拿 ETag（If-Range）或大小未知时才 HEAD；普通下载不为这点元数据多一次往返
        if (hasIfRange || totalSize == null) {
            HeadObjectResponse head = storageService.headObject(entity.getBucket(), entity.getObjectKey());
            if (head.contentLength() != null) {
                totalSize = head.contentLength();
            }
            knownEtag = head.eTag();
        }

        // If-Range 不匹配时把 range 置空 = 回全量 200，客户端会重头下载
        boolean allowRange = !hasIfRange || RangeHeader.ifRangeMatches(ifRange, knownEtag);
        RangeHeader.Range range = allowRange ? RangeHeader.parse(rangeHeader, totalSize) : null;

        String s3Range = range == null ? null : range.toHeaderValue();
        ResponseInputStream<GetObjectResponse> stream;
        try {
            stream = storageService.openObject(entity.getBucket(), entity.getObjectKey(), s3Range);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 416) {
                throw new RangeNotSatisfiableException(totalSize);
            }
            throw ex;
        }
        GetObjectResponse meta = stream.response();
        boolean partial = meta.contentRange() != null && !meta.contentRange().isBlank();
        long contentLength = meta.contentLength() != null
                ? meta.contentLength()
                : (totalSize == null ? 0L : totalSize);
        String contentRange = meta.contentRange() != null
                ? meta.contentRange()
                : (range == null || totalSize == null ? null : range.toContentRange(totalSize));
        String eTag = meta.eTag() != null ? meta.eTag() : knownEtag;
        return new DownloadPayload(entity, stream, partial, contentLength, contentRange, eTag);
    }

    /**
     * 只取下载所需的元数据，用于 HEAD 请求。
     *
     * <p>下载器与浏览器的「续传探测」都先发 HEAD。若按 GET 处理，会为一次不传数据
     * 的请求白开一条 S3 流——并发下载时白白占掉一半连接。
     */
    public DownloadStat statDownload(Long id, Long studentId) {
        UploadFileEntity entity = get(id, studentId);
        if (entity.getStatus() != UploadFileEntity.UploadStatus.COMPLETED || entity.getObjectKey() == null) {
            throw new BizException(409, "文件尚未上传完成，无法下载");
        }
        HeadObjectResponse head = storageService.headObject(entity.getBucket(), entity.getObjectKey());
        long size = head.contentLength() != null
                ? head.contentLength()
                : (entity.getSizeBytes() == null ? 0L : entity.getSizeBytes());
        return new DownloadStat(entity, size, head.eTag());
    }

    /**
     * 删除：优先让 tusd 终止上传（会一并清理 .info），失败再直接删对象。
     * 无论哪种方式，业务记录都置为 DELETED，避免列表出现悬空文件。
     * 不加事务：内部有外部 HTTP/S3 调用，避免长时间占用数据库连接。
     */
    public void delete(Long id, Long studentId) {
        UploadFileEntity entity = get(id, studentId);
        if (entity.getStatus() == UploadFileEntity.UploadStatus.DELETED) {
            return;
        }
        try {
            storageService.terminate(entity.getUploadId());
        } catch (Exception ex) {
            log.warn("tusd 终止失败，改为直接删除对象 uploadId={}: {}", entity.getUploadId(), ex.getMessage());
            storageService.deleteObject(entity.getBucket(), entity.getObjectKey());
        }
        entity.setStatus(UploadFileEntity.UploadStatus.DELETED);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    /**
     * 按 id + 归属查询。
     *
     * <p>刻意合并成一次查询而不是「先按 id 取再比对 studentId」：后者会把
     * “文件存在但不属于你”和“文件不存在”区分成两种响应，等于给攻击者
     * 提供了一个遍历 id 探测数据存在的信道。统一返回 404。
     */
    public UploadFileEntity get(Long id, Long studentId) {
        return repository.findByIdAndStudentId(id, studentId)
                .orElseThrow(() -> BizException.notFound("文件"));
    }

    /**
     * 把超时未续传的上传置为 EXPIRED。
     *
     * <p>单独成方法是为了给 {@code @Modifying} 更新划出事务边界：清理任务里
     * 还夹着调 tusd 的外部 HTTP 请求，不能把整段包进事务占用数据库连接。
     *
     * @return true 表示本次推进成功；false 表示状态已被 post-finish 改成 COMPLETED
     */
    @Transactional
    public boolean expireIfStillUploading(String uploadId) {
        return repository.updateStatusIfCurrent(uploadId,
                UploadFileEntity.UploadStatus.EXPIRED,
                UploadFileEntity.UploadStatus.UPLOADING,
                Instant.now()) > 0;
    }

    // ------------------------------------------------------------------ 校验

    private long resolveStudentId(Map<String, String> meta, TusdHookRequest request) {
        String rawStudentId = meta.get("studentId");
        if (rawStudentId == null || rawStudentId.isBlank()) {
            rawStudentId = request.header("X-Student-Id");
        }
        if (rawStudentId == null || rawStudentId.isBlank()) {
            throw new BizException(401, "缺少 studentId");
        }
        String token = meta.get("uploadToken");
        if (token == null || token.isBlank()) {
            token = request.header("X-Upload-Token");
        }
        long tokenStudentId = tokenService.verify(token);

        long studentId;
        try {
            studentId = Long.parseLong(rawStudentId.trim());
        } catch (NumberFormatException ex) {
            throw new BizException(401, "studentId 非法");
        }
        if (studentId != tokenStudentId) {
            throw new BizException(401, "上传凭证与 studentId 不匹配");
        }
        return studentId;
    }

    private void validateSize(TusdHookRequest.Upload upload) {
        if (upload.isSizeIsDeferred() || upload.getSize() == null) {
            throw new BizException(400, "必须提供文件大小（不支持延迟长度上传）");
        }
        if (upload.getSize() <= 0) {
            throw new BizException(400, "文件大小必须大于 0");
        }
        long max = properties.getSecurity().getMaxFileSize();
        if (upload.getSize() > max) {
            throw new BizException(413, "文件超过上限 " + readableSize(max));
        }
    }

    public void validateExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new BizException(415, "无法识别文件类型，文件名需包含扩展名");
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!properties.getSecurity().getAllowedExtensions().contains(ext)) {
            throw new BizException(415, "不支持的文件类型: " + ext);
        }
    }

    public String sanitizeFileName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new BizException(400, "缺少文件名");
        }
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            throw new BizException(400, "文件名非法");
        }
        return name.length() > MAX_FILE_NAME_LENGTH ? name.substring(0, MAX_FILE_NAME_LENGTH) : name;
    }

    private int normalizeStatus(int code) {
        return code >= 400 && code <= 599 ? code : 400;
    }

    private String readableSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.1f GB", bytes / 1024.0 / 1024 / 1024);
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024);
    }

    // ------------------------------------------------------------------ 组装

    private TusdHookRequest.Upload requireUpload(TusdHookRequest request) {
        if (request == null || request.getEvent() == null || request.getEvent().getUpload() == null) {
            throw new IllegalArgumentException("tusd hook 请求缺少 Upload");
        }
        return request.getEvent().getUpload();
    }

    private Map<String, String> safeMeta(TusdHookRequest.Upload upload) {
        return upload.getMetaData() == null ? Map.of() : upload.getMetaData();
    }

    private UploadFileEntity newEntity(TusdHookRequest.Upload upload, UploadFileEntity.UploadStatus status) {
        Map<String, String> meta = safeMeta(upload);
        Instant now = Instant.now();
        UploadFileEntity entity = new UploadFileEntity();
        entity.setUploadId(upload.getId());
        entity.setStudentId(parseLongOrNull(meta.get("studentId")));
        entity.setFileName(Optional.ofNullable(meta.get("filename")).orElse("unnamed"));
        entity.setContentType(meta.get("filetype"));
        entity.setSizeBytes(upload.getSize());
        entity.setOffsetBytes(upload.getOffset());
        entity.setStatus(status);
        entity.setMetaData(meta);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        if (status == UploadFileEntity.UploadStatus.COMPLETED) {
            entity.setFinishedAt(now);
        }
        return entity;
    }

    private UploadFileSummary toSummary(UploadFileEntity entity) {
        return new UploadFileSummary(
                entity.getId(),
                entity.getUploadId(),
                entity.getFileName(),
                entity.getContentType(),
                entity.getSizeBytes(),
                entity.getStatus().name(),
                entity.getCreatedAt(),
                entity.getFinishedAt(),
                "/api/upload/files/" + entity.getId() + "/download");
    }

    private Long parseLongOrNull(String raw) {
        try {
            return raw == null ? null : Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 下载载荷：实体 + RustFS 对象流 + 组装 200/206 响应所需的元信息。
     *
     * @param partial       是否为范围请求（S3 回带了 Content-Range）
     * @param contentLength 本次响应的字节数（范围请求时是一段，不是整个文件）
     * @param contentRange  S3 回传的 Content-Range，形如 {@code bytes 0-1023/5000}
     * @param eTag          对象 ETag，用于浏览器缓存协商
     */
    public record DownloadPayload(
            UploadFileEntity file,
            InputStream stream,
            boolean partial,
            long contentLength,
            String contentRange,
            String eTag
    ) {
    }

    /**
     * HEAD 请求的元数据。
     *
     * @param totalSize 对象总字节数，作为 {@code Content-Length} 回给客户端
     * @param eTag      对象 ETag，客户端据此发起 {@code If-Range} 续传
     */
    public record DownloadStat(UploadFileEntity file, long totalSize, String eTag) {
    }
}
