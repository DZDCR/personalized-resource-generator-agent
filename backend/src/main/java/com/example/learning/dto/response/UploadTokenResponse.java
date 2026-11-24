package com.example.learning.dto.response;

import java.time.Instant;

/**
 * 上传凭证：由业务服务签发，前端放进 tus Upload-Metadata，
 * tusd pre-create hook 回调后端校验，校验通过才真正创建上传。
 */
public record UploadTokenResponse(
        Long studentId,
        String token,
        Instant expiresAt
) {
}
