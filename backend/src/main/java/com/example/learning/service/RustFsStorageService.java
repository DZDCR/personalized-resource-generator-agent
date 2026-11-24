package com.example.learning.service;

import com.example.learning.config.UploadProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

/**
 * RustFS 对象操作。
 * 上传写入由 tusd 的 s3store 完成，这里只做读取、删除与上传终止，
 * 保证「业务服务不参与文件字节流」的分层约束。
 */
@Slf4j
@Service
public class RustFsStorageService {

    private final S3Client s3Client;
    private final UploadProperties properties;
    private final RestClient tusdClient;

    public RustFsStorageService(S3Client s3Client, UploadProperties properties, RestClient.Builder builder) {
        this.s3Client = s3Client;
        this.properties = properties;
        this.tusdClient = builder.baseUrl(properties.getTusd().getBaseUrl()).build();
    }

    /** 打开对象流，由 Controller 直接透传给浏览器（不整块读入内存）。 */
    public ResponseInputStream<GetObjectResponse> openObject(String bucket, String objectKey) {
        return openObject(bucket, objectKey, null);
    }

    /**
     * 打开对象流，可携带 HTTP Range 头。
     *
     * <p>把范围透传给 S3，由存储端只回传需要的字节段：视频拖进度条、大文件断点续传
     * 都依赖这条路径。返回值自带 {@code response()}，调用方可从中取 contentRange /
     * contentLength / eTag 组装 206 响应。
     *
     * @param range 形如 {@code bytes=0-1023}，为 null 表示取全量
     */
    public ResponseInputStream<GetObjectResponse> openObject(String bucket, String objectKey, String range) {
        GetObjectRequest.Builder builder = GetObjectRequest.builder()
                .bucket(defaultBucket(bucket))
                .key(objectKey);
        if (range != null && !range.isBlank()) {
            builder.range(range);
        }
        return s3Client.getObject(builder.build());
    }

    /**
     * 只取对象元数据（大小、ETag、类型），不读任何数据。
     *
     * <p>用途有两个：HEAD 请求不必开一条流；带 {@code If-Range} 的续传请求需要
     * 先拿当前 ETag 才能判断「文件是否还是上次那份」。
     */
    public HeadObjectResponse headObject(String bucket, String objectKey) {
        return s3Client.headObject(HeadObjectRequest.builder()
                .bucket(defaultBucket(bucket))
                .key(objectKey)
                .build());
    }

    /**
     * 直接写入对象（小文件批量导入用）。
     *
     * <p>与 tus 链路的分工：大文件走 tus 分片续传，小文件（≤10MB）一次 PUT 更划算——
     * tus 每传一个文件至少三次往返（POST 建上传、PATCH 写数据、等 post-finish 落库），
     * 批量导几十个小文件时这部分开销比传数据本身还大。
     *
     * @param contentLength 必须显式给出：流式上传没有长度时 AWS SDK 会转成
     *                      chunked 编码，部分 S3 兼容实现不接受
     */
    public void putObject(String bucket, String objectKey, String contentType,
                          InputStream stream, long contentLength) {
        s3Client.putObject(PutObjectRequest.builder()
                        .bucket(defaultBucket(bucket))
                        .key(objectKey)
                        .contentType(contentType)
                        .contentLength(contentLength)
                        .build(),
                RequestBody.fromInputStream(stream, contentLength));
    }

    /** 兜底删除：tusd 终止失败时直接删对象。 */
    public void deleteObject(String bucket, String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(defaultBucket(bucket))
                .key(objectKey)
                .build());
    }

    /**
     * 调 tusd DELETE 终止上传：tusd 会清理已落盘分片与 .info，并回调 post-terminate。
     * 这是比直接删对象更干净的方式。
     */
    public void terminate(String uploadId) {
        tusdClient.delete()
                .uri("/files/{id}", uploadId)
                .header("Tus-Resumable", "1.0.0")
                .retrieve()
                .toBodilessEntity();
        log.info("已请求 tusd 终止上传 uploadId={}", uploadId);
    }

    private String defaultBucket(String bucket) {
        return bucket == null || bucket.isBlank() ? properties.getStorage().getBucket() : bucket;
    }
}
