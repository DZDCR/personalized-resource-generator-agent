package com.example.learning.service;

import com.example.learning.common.BizException;
import com.example.learning.config.UploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

/**
 * 上传凭证签发与校验（无状态 HMAC，不依赖会话）。
 *
 * 格式：v1.{studentId}.{expEpochSecond}.{base64url(HMAC-SHA256)}
 * 该凭证只证明「某学生获得了在有效期内上传的许可」，
 * 真正的权限校验发生在 tusd pre-create hook 回调时。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadTokenService {

    private static final String VERSION = "v1";
    private static final String ALGORITHM = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final UploadProperties properties;

    public IssuedToken issue(Long studentId) {
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(properties.getSecurity().getTokenTtlMinutes()));
        String payload = VERSION + "." + studentId + "." + expiresAt.getEpochSecond();
        return new IssuedToken(payload + "." + sign(payload), expiresAt);
    }

    /**
     * 校验凭证并返回 studentId。
     *
     * @throws BizException 401 凭证非法/过期
     */
    public long verify(String token) {
        if (token == null || token.isBlank()) {
            throw new BizException(401, "缺少上传凭证");
        }
        String[] parts = token.split("\\.");
        if (parts.length != 4 || !VERSION.equals(parts[0])) {
            throw new BizException(401, "上传凭证格式错误");
        }
        long studentId;
        long expiresAt;
        try {
            studentId = Long.parseLong(parts[1]);
            expiresAt = Long.parseLong(parts[2]);
        } catch (NumberFormatException ex) {
            throw new BizException(401, "上传凭证格式错误");
        }
        if (Instant.now().getEpochSecond() > expiresAt) {
            throw new BizException(401, "上传凭证已过期，请重新获取");
        }
        String expected = sign(parts[0] + "." + parts[1] + "." + parts[2]);
        boolean valid = MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                parts[3].getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            throw new BizException(401, "上传凭证签名无效");
        }
        return studentId;
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.getSecurity().getSigningKey().getBytes(StandardCharsets.UTF_8), ALGORITHM));
            return ENCODER.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            // 密钥配置错误属于服务端问题，不应退化为「校验通过」
            log.error("上传凭证签名失败", ex);
            throw new IllegalStateException("上传凭证签名失败", ex);
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
