package com.example.learning.service;

import com.example.learning.common.BizException;
import com.example.learning.config.UploadProperties;
import com.example.learning.dto.response.BatchUploadResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 小文件批量导入。
 *
 * <p>为什么不复用 tus：tus 每传一个文件至少三次往返（POST 建上传 → PATCH 写数据 →
 * post-finish 落库），传 20 个 1MB 的小文件时，握手开销比数据本身还大。批量导入走
 * 「一次 multipart 请求 + 一次 PUT + 一次批量 INSERT」。
 *
 * <p>为什么用 JdbcTemplate 而不是 {@code repository.saveAll}：实体的主键是
 * {@code GenerationType.IDENTITY}，Hibernate 必须逐条 INSERT 才能拿回自增 id，
 * 批量插入会被静默降级成 N 条独立语句。这里用一条 prepared statement 插入多行，
 * 才是真正的批量——代价是要手写列名，所以这段 SQL 与实体字段必须一起改。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchUploadService {

    /** 与 tusd 的 -s3-object-prefix 同前缀，生命周期规则才能一并覆盖。 */
    private static final String BATCH_DIR = "batch/";

    private static final String INSERT_SQL = """
            insert into upload_files
                (upload_id, student_id, file_name, content_type, size_bytes, offset_bytes,
                 bucket, object_key, storage_type, status, created_at, updated_at, finished_at)
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final RustFsStorageService storageService;
    private final UploadFileService uploadFileService;
    private final UploadProperties properties;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 批量写入。
     *
     * @param files     多部分表单里的文件
     * @param studentId 归属学生，来自登录态
     */
    public BatchUploadResponse upload(List<MultipartFile> files, long studentId) {
        UploadProperties.Batch config = properties.getBatch();
        if (files == null || files.isEmpty()) {
            throw new BizException(400, "没有收到文件");
        }
        if (files.size() > config.getMaxFiles()) {
            throw new BizException(400, "单次最多导入 " + config.getMaxFiles() + " 个文件");
        }
        long totalSize = files.stream().mapToLong(MultipartFile::getSize).sum();
        if (totalSize > config.getMaxTotalSize()) {
            throw new BizException(413, "单次总大小超过上限 " + humanSize(config.getMaxTotalSize())
                    + "，请分批导入");
        }

        String bucket = properties.getStorage().getBucket();
        List<BatchUploadResponse.Item> items = new ArrayList<>();
        List<PendingRow> rows = new ArrayList<>();

        for (MultipartFile file : files) {
            String originalName = file.getOriginalFilename();
            try {
                String fileName = prepare(file, config);
                String contentType = file.getContentType() == null
                        ? "application/octet-stream" : file.getContentType();
                String objectKey = buildObjectKey(studentId, fileName);

                try (InputStream stream = file.getInputStream()) {
                    storageService.putObject(bucket, objectKey, contentType, stream, file.getSize());
                }
                rows.add(new PendingRow(UUID.randomUUID().toString(), studentId, fileName,
                        contentType, file.getSize(), bucket, objectKey));
                items.add(BatchUploadResponse.Item.success(fileName, file.getSize()));
            } catch (BizException ex) {
                items.add(BatchUploadResponse.Item.failure(originalName, ex.getMessage()));
            } catch (Exception ex) {
                log.warn("批量写入对象失败 file={}: {}", originalName, ex.getMessage());
                items.add(BatchUploadResponse.Item.failure(originalName, "写入对象存储失败"));
            }
        }

        if (!rows.isEmpty()) {
            persist(rows);
        }

        int succeeded = rows.size();
        log.info("批量导入完成 studentId={} 提交 {} 成功 {} 失败 {} 总大小 {}",
                studentId, files.size(), succeeded, items.size() - succeeded, humanSize(totalSize));
        return new BatchUploadResponse(files.size(), succeeded, items.size() - succeeded, items);
    }

    /** 单文件校验：大小、文件名、扩展名。规则与 tus 路径共用同一套。 */
    private String prepare(MultipartFile file, UploadProperties.Batch config) {
        if (file.isEmpty()) {
            throw new BizException(400, "文件内容为空");
        }
        if (file.getSize() > config.getMaxFileSize()) {
            throw new BizException(413, "超过单文件上限 " + humanSize(config.getMaxFileSize())
                    + "，大文件请使用分片上传");
        }
        String fileName = uploadFileService.sanitizeFileName(file.getOriginalFilename());
        uploadFileService.validateExtension(fileName);
        return fileName;
    }

    /**
     * 批量落库。
     *
     * <p>写对象在前、落库在后：反过来的话，INSERT 成功而 PUT 失败会留下一条「有记录没文件」
     * 的假数据，列表里能看到却下载不了。现在的顺序若 INSERT 失败，清理已写入的对象即可。
     */
    private void persist(List<PendingRow> rows) {
        Timestamp now = Timestamp.from(Instant.now());
        List<Object[]> args = rows.stream().map(row -> new Object[]{
                "batch-" + row.uploadId(), row.studentId(), row.fileName(), row.contentType(),
                row.size(), row.size(), row.bucket(), row.objectKey(), "batch", "COMPLETED",
                now, now, now
        }).toList();
        try {
            long start = System.nanoTime();
            jdbcTemplate.batchUpdate(INSERT_SQL, args);
            log.info("批量落库 {} 条，耗时 {} ms", args.size(), (System.nanoTime() - start) / 1_000_000);
        } catch (Exception ex) {
            log.error("批量落库失败，回滚已写入的 {} 个对象", rows.size(), ex);
            for (PendingRow row : rows) {
                try {
                    storageService.deleteObject(row.bucket(), row.objectKey());
                } catch (Exception inner) {
                    log.warn("回滚删除对象失败 key={}: {}", row.objectKey(), inner.getMessage());
                }
            }
            throw new BizException(500, "批量保存失败，请重试");
        }
    }

    private String buildObjectKey(long studentId, String fileName) {
        String prefix = properties.getStorage().getObjectPrefix();
        if (prefix == null || prefix.isBlank()) {
            prefix = "uploads/";
        }
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        int dot = fileName.lastIndexOf('.');
        String ext = (dot >= 0 && dot < fileName.length() - 1)
                ? fileName.substring(dot).toLowerCase(Locale.ROOT) : "";
        return prefix + BATCH_DIR + studentId + "/" + UUID.randomUUID() + ext;
    }

    private String humanSize(long bytes) {
        if (bytes >= 1024L * 1024 * 1024) {
            return String.format(Locale.ROOT, "%.0fGB", bytes / 1024.0 / 1024 / 1024);
        }
        if (bytes >= 1024L * 1024) {
            return String.format(Locale.ROOT, "%.0fMB", bytes / 1024.0 / 1024);
        }
        return String.format(Locale.ROOT, "%.0fKB", bytes / 1024.0);
    }

    private record PendingRow(String uploadId, long studentId, String fileName, String contentType,
                              long size, String bucket, String objectKey) {
    }
}
