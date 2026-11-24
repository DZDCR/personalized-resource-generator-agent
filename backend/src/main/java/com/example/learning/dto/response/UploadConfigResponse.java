package com.example.learning.dto.response;

import java.util.List;

/**
 * 上传能力配置：前端据此初始化 Uppy（分片大小、限制与 tus 端点），
 * 以及渲染批量导入的限额提示。
 *
 * <p>batch* 三个值一并下发，是为了让前端在选文件时就拦下来，而不是等 50MB 传完
 * 才被服务端拒绝——限制应该在最便宜的地方生效。
 */
public record UploadConfigResponse(
        String tusEndpoint,
        long chunkSize,
        long maxFileSize,
        List<String> allowedExtensions,
        int maxConcurrentUploads,
        int batchMaxFiles,
        long batchMaxFileSize,
        long batchMaxTotalSize
) {
}
