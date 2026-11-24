package com.example.learning.service;

import com.example.learning.common.BizException;
import com.example.learning.config.UploadProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 当前访问者的身份来源（登录体系落地前的过渡实现）。
 *
 * <p>身份由服务端签发、放在带签名的 HttpOnly Cookie 里，前端既无法声明也无法篡改。
 * Cookie 值格式：{@code v1.<studentId>.<expiresEpochSecond>.<hmacHex>}，签名覆盖前三段，
 * 任何一段被改动都会验签失败。
 *
 * <p>为什么不存 Redis：会话信息只有「一个 ID + 一个过期时间」，签名后放进 Cookie 就够，
 * 服务端不需要任何存储就能校验真伪。少一个必须常驻的中间件，也少一类
 * 「Redis 挂了上传全不可用」的故障面。
 *
 * <p>匿名 ID 取<b>负数</b>：真实用户 ID 恒为正，负号一眼可辨，且与将来的自增主键永不冲突。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    /** 会话 Cookie 名。 */
    private static final String COOKIE_NAME = "learning_sid";
    /** 格式版本，便于将来换签名算法时平滑过渡。 */
    private static final String VERSION = "v1";
    /** 匿名 ID 范围：[-1e10, -1e6)，约 90 亿个取值，碰撞概率可忽略。 */
    private static final long ANONYMOUS_BASE = 1_000_000L;
    private static final long ANONYMOUS_SPAN = 9_000_000_000L;
    /** 会话有效期：与「上传凭证 2 小时」解耦，取 30 天方便长期续传。 */
    private static final Duration SESSION_TTL = Duration.ofDays(30);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final UploadProperties properties;

    /**
     * 取当前身份；没有有效会话则新建一个（仅在申请上传凭证时调用）。
     * <p>新建时必须写回 Set-Cookie，否则下次请求又会被当成新访客。
     */
    public long resolveOrCreate(HttpServletRequest request, HttpServletResponse response) {
        Long existing = find(request);
        if (existing != null) {
            return existing;
        }
        long studentId = -(ANONYMOUS_BASE + ThreadLocalRandom.current().nextLong(ANONYMOUS_SPAN));
        long expiresAt = Instant.now().plus(SESSION_TTL).getEpochSecond();

        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, encode(studentId, expiresAt))
                .httpOnly(true)
                .path("/")
                .maxAge(SESSION_TTL)
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.info("建立匿名上传会话 studentId={}", studentId);
        return studentId;
    }

    /**
     * 取当前身份；没有有效会话直接 401（列表、下载、删除都走这里）。
     *
     * <p>不在这些接口上自动建新会话：否则浏览器预检、爬虫、误访问都会让客户端无端拿到
     * 一个新身份，而那个身份下没有任何文件，语义上只会更困惑。
     */
    public long require(HttpServletRequest request) {
        Long studentId = find(request);
        if (studentId == null) {
            throw new BizException(401, "上传会话已失效，请刷新页面后重试");
        }
        return studentId;
    }

    /** 只读查询，不创建；Cookie 缺失、被篡改或已过期一律视为无身份。 */
    public Long find(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return decode(cookie.getValue());
            }
        }
        return null;
    }

    /** 生成 {@code v1.sid.exp.sig}。 */
    private String encode(long studentId, long expiresAt) {
        String payload = VERSION + "." + studentId + "." + expiresAt;
        return payload + "." + sign(payload);
    }

    /** 校验并解析，失败返回 null。 */
    private Long decode(String value) {
        if (value == null) {
            return null;
        }
        String[] parts = value.split("\\.");
        if (parts.length != 4 || !VERSION.equals(parts[0])) {
            return null;
        }
        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        // 常量时间比对，避免用响应时间差逐字节猜签名
        if (!MessageDigest.isEqual(
                sign(payload).getBytes(StandardCharsets.UTF_8),
                parts[3].getBytes(StandardCharsets.UTF_8))) {
            return null;
        }
        try {
            long expiresAt = Long.parseLong(parts[2]);
            if (expiresAt <= Instant.now().getEpochSecond()) {
                return null;
            }
            return Long.parseLong(parts[1]);
        } catch (NumberFormatException ex) {
            log.warn("会话 Cookie 内容非法，忽略");
            return null;
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    properties.getSecurity().getSigningKey().getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            // 签名失败属于配置问题（如密钥为空），必须显式失败而非放行
            throw new IllegalStateException("会话签名失败，请检查 app.upload.security.signing-key", ex);
        }
    }
}
