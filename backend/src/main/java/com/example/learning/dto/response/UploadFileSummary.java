package com.example.learning.dto.response;

import java.time.Instant;

/**
 * 已上传文件（对外视图）。
 */
public record UploadFileSummary(
        Long id,
        String uploadId,
        String fileName,
        String contentType,
        Long sizeBytes,
        String status,
        Instant createdAt,
        Instant finishedAt,
        String downloadUrl
) {
}
