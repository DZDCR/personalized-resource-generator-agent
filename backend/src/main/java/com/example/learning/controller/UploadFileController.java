package com.example.learning.controller;

import com.example.learning.common.ApiResult;
import com.example.learning.common.BizException;
import com.example.learning.common.RangeNotSatisfiableException;
import com.example.learning.config.UploadProperties;
import com.example.learning.dto.response.UploadConfigResponse;
import com.example.learning.dto.response.BatchUploadResponse;
import com.example.learning.dto.response.UploadFileSummary;
import com.example.learning.dto.response.UploadTokenResponse;
import com.example.learning.entity.UploadFileEntity;
import com.example.learning.service.BatchUploadService;
import com.example.learning.service.CurrentUserService;
import com.example.learning.service.UploadFileService;
import com.example.learning.service.UploadTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件上传业务接口：
 * 下发上传配置与凭证、查询已上传文件、流式下载、删除。
 * 真正的字节传输由 nginx → tusd → RustFS 完成，不经过本服务。
 *
 * <p>身份约定：studentId 一律由 {@link CurrentUserService} 从服务端会话解析，
 * 不再接受前端传参。前端传入的 studentId 只用于 tus 元数据回显，
 * 真正的归属以会话为准（见 UploadFileService#get）。
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class UploadFileController {

    private final UploadFileService uploadFileService;
    private final UploadTokenService uploadTokenService;
    private final BatchUploadService batchUploadService;
    private final CurrentUserService currentUser;
    private final UploadProperties properties;

    /** 前端初始化 Uppy 所需的参数（tus 端点、分片大小、限制），以及批量导入的限额。 */
    @GetMapping("/config")
    public ApiResult<UploadConfigResponse> config() {
        UploadProperties.Batch batch = properties.getBatch();
        return ApiResult.success(new UploadConfigResponse(
                properties.getTusd().getPublicPath(),
                properties.getChunkSize(),
                properties.getSecurity().getMaxFileSize(),
                properties.getSecurity().getAllowedExtensions(),
                properties.getMaxConcurrentUploads(),
                batch.getMaxFiles(),
                batch.getMaxFileSize(),
                batch.getMaxTotalSize()));
    }

    /**
     * 签发上传凭证。
     *
     * <p>不再接收请求体里的 studentId：身份来自登录态，服务端据此判定归属。
     * 响应里回传 studentId 供前端写入 tus 元数据（pre-create 会拿它与凭证比对）。
     */
    @PostMapping("/token")
    public ApiResult<UploadTokenResponse> token(HttpServletRequest request, HttpServletResponse response) {
        long studentId = currentUser.resolveOrCreate(request, response);
        UploadTokenService.IssuedToken issued = uploadTokenService.issue(studentId);
        return ApiResult.success(new UploadTokenResponse(studentId, issued.token(), issued.expiresAt()));
    }

    @GetMapping("/files")
    public ApiResult<List<UploadFileSummary>> files(HttpServletRequest request) {
        return ApiResult.success(uploadFileService.listByStudent(currentUser.require(request)));
    }

    /**
     * 小文件批量导入：一次请求多个文件，直写对象存储 + 一次批量 INSERT。
     *
     * <p>与 tus 的分工：单文件超过 10MB、或需要断点续传的，走 {@code /files/} 的 tus 通道；
     * 课件、图片、文档这类小文件用这个接口，省掉每个文件三次握手。
     *
     * <p>返回逐项结果：部分成功是常态（一两个格式不对不该让整批重挑）。
     */
    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResult<BatchUploadResponse> batchUpload(
            @RequestParam("files") List<MultipartFile> files,
            HttpServletRequest request) {
        long studentId = currentUser.require(request);
        return ApiResult.success(batchUploadService.upload(files, studentId));
    }

    /**
     * 流式下载：从 RustFS 读取并透传，支持大文件不占堆内存。
     *
     * <p>支持 HTTP Range：浏览器播放器拖动进度条、下载工具断点续传都依赖它。
     * Range 由 {@code RangeHeader} 解析后交给存储端，服务端不做二次切分。
     *
     * <p>同时支持 {@code HEAD}：下载器与浏览器的续传探测都会先问元数据，按 GET 处理会
     * 白开一条 S3 流，并发下载时等于浪费一半连接。
     *
     * @param inline 为 true 时用 Content-Disposition: inline，浏览器内联预览（视频/PDF）
     */
    @RequestMapping(value = "/files/{id}/download", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<Resource> download(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            @RequestHeader(value = HttpHeaders.IF_RANGE, required = false) String ifRange,
            @RequestParam(value = "inline", defaultValue = "false") boolean inline,
            HttpServletRequest request) {

        UploadFileService.DownloadPayload payload;
        try {
            long studentId = currentUser.require(request);
            if (HttpMethod.HEAD.matches(request.getMethod())) {
                return headResponse(uploadFileService.statDownload(id, studentId), inline);
            }
            payload = uploadFileService.openDownload(id, studentId, range, ifRange);
        } catch (BizException ex) {
            // 下载链接由浏览器直接打开（window.open / <video src>），不是 axios。
            // 若这里返回 HTTP 200 + JSON 业务码，浏览器会把那段 JSON 当成文件内容
            // 或空白页展示，用户完全不知道发生了什么。故失败一律用真实状态码。
            return bizErrorResponse(ex);
        }
        UploadFileEntity file = payload.file();

        // 全量请求才做缓存协商；带 Range 的请求必须返回数据段，不能回 304
        if (range == null && payload.eTag() != null && payload.eTag().equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, quoteETag(payload.eTag()))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .build();
        }

        ContentDisposition disposition = disposition(file.getFileName(), inline);

        ResponseEntity.BodyBuilder builder = ResponseEntity
                .status(payload.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .contentType(mediaType(file.getContentType()))
                .contentLength(payload.contentLength());

        if (payload.contentRange() != null) {
            builder.header(HttpHeaders.CONTENT_RANGE, payload.contentRange());
        }
        if (payload.eTag() != null) {
            builder.header(HttpHeaders.ETAG, quoteETag(payload.eTag()));
        }
        // 分段响应内容随 Range 变化，不能被中间层合成一份完整对象缓存
        builder.header(HttpHeaders.CACHE_CONTROL, payload.partial() ? "private, no-store" : "private, max-age=0");

        return builder.body(new InputStreamResource(payload.stream()));
    }

    /** HEAD 响应：只回元数据，不打开对象流。 */
    private ResponseEntity<Resource> headResponse(UploadFileService.DownloadStat stat, boolean inline) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition(stat.file().getFileName(), inline).toString())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=0")
                .contentType(mediaType(stat.file().getContentType()))
                .contentLength(stat.totalSize());
        if (stat.eTag() != null) {
            builder.header(HttpHeaders.ETAG, quoteETag(stat.eTag()));
        }
        return builder.<Resource>build();
    }

    /** 业务异常到真实 HTTP 状态码的映射；未覆盖的码交给全局处理器。 */
    private ResponseEntity<Resource> bizErrorResponse(BizException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            default -> null;
        };
        if (status == null) {
            throw ex;
        }
        return ResponseEntity.status(status).body(null);
    }

    private ContentDisposition disposition(String fileName, boolean inline) {
        return (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
    }

    /** 删除：优先让 tusd 终止上传并清理分片，失败则直接删对象。 */
    @DeleteMapping("/files/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        uploadFileService.delete(id, currentUser.require(request));
        return ApiResult.success();
    }

    /**
     * Range 越界（HTTP 416）。
     *
     * <p>必须由本 Controller 处理而不是交给全局异常处理器：后者会把异常包成
     * HTTP 200 + 业务码，而浏览器播放器只认真正的 416 + Content-Range，
     * 否则会把错误响应当成文件内容渲染，表现为画面卡死。
     */
    @ExceptionHandler(RangeNotSatisfiableException.class)
    public ResponseEntity<Void> handleRangeNotSatisfiable(RangeNotSatisfiableException ex) {
        ResponseEntity.BodyBuilder builder = ResponseEntity
                .status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes");
        if (ex.getContentRange() != null) {
            builder.header(HttpHeaders.CONTENT_RANGE, ex.getContentRange());
        }
        return builder.build();
    }

    private MediaType mediaType(String raw) {
        if (raw == null || raw.isBlank()) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        try {
            return MediaType.parseMediaType(raw);
        } catch (Exception ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    /** S3 的 ETag 通常已带引号，个别实现不带，这里统一补齐。 */
    private String quoteETag(String raw) {
        return raw.startsWith("\"") ? raw : "\"" + raw + "\"";
    }
}
