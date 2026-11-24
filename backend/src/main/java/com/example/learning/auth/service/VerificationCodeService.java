package com.example.learning.auth.service;

import com.example.learning.auth.config.AuthProperties;
import com.example.learning.auth.dto.AuthViews.SendCodeView;
import com.example.learning.auth.security.AuthCodes;
import com.example.learning.auth.security.Hashes;
import com.example.learning.common.BizException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 邮箱/短信一次性验证码。
 *
 * <p>验证码只保存 HMAC-SHA256 摘要；5 分钟有效、60 秒重发冷却、最多 5 次尝试、
 * 每小时最多 8 次发送。开发模式返回 debugCode 并写入发件箱，生产模式仅写日志。</p>
 *
 * <p>生产替换点：EMAIL 使用 JavaMailSender/SMTP 或云邮件服务；SMS 使用阿里云/腾讯云短信网关。
 * 若部署多实例，Caffeine 需替换为 Redis 并保留同样的 TTL/原子计数语义。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeService {

    public static final String SCENE_REGISTER = "REGISTER";
    public static final String SCENE_FORGOT = "FORGOT";
    public static final String SCENE_LOGIN = "LOGIN";
    public static final String CHANNEL_EMAIL = "EMAIL";
    public static final String CHANNEL_SMS = "SMS";

    private final AuthProperties properties;
    private final DevOutboxService outbox;
    private final SecureRandom random = new SecureRandom();

    private final Cache<String, CodeEntry> codes = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();

    private final Cache<String, AtomicInteger> sendCounters = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofHours(1))
            .build();

    public SendCodeView send(String scene, String channel, String target) {
        String normalizedScene = normalizeScene(scene);
        String normalizedChannel = normalizeChannel(channel);
        String normalizedTarget = normalizeTarget(normalizedChannel, target);

        String key = key(normalizedScene, normalizedChannel, normalizedTarget);
        CodeEntry existing = codes.getIfPresent(key);
        Instant now = Instant.now();
        if (existing != null && now.isBefore(existing.createdAt.plusSeconds(properties.getOtp().getResendSeconds()))) {
            throw new BizException(AuthCodes.RATE_LIMITED, "验证码发送过于频繁，请稍后再试");
        }
        AtomicInteger counter = sendCounters.get(key, ignored -> new AtomicInteger());
        if (counter.incrementAndGet() > properties.getOtp().getMaxSendsPerHour()) {
            throw new BizException(AuthCodes.RATE_LIMITED, "该账号验证码发送次数已达上限，请稍后再试");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        Instant expiresAt = now.plusSeconds(properties.getOtp().getTtlSeconds());
        codes.put(key, new CodeEntry(hmac(key, code), now, expiresAt, 0));
        if (properties.isDevMode()) {
            outbox.record(normalizedChannel, normalizedTarget, normalizedScene, code, expiresAt);
            log.info("[DEV] {} 验证码 scene={} target={} code={}", normalizedChannel, normalizedScene,
                    mask(normalizedTarget), code);
        } else {
            // 生产环境在此调用邮件/短信网关；日志不输出验证码。
            log.info("send verification code via {} to {} scene={}", normalizedChannel,
                    mask(normalizedTarget), normalizedScene);
        }
        String message = "验证码已发送，有效期 " + (properties.getOtp().getTtlSeconds() / 60) + " 分钟";
        return new SendCodeView(true, message, properties.isDevMode() ? code : null,
                (int) properties.getOtp().getTtlSeconds());
    }

    /**
     * 校验成功即销毁；失败累计次数，超过阈值销毁。
     */
    public void verify(String scene, String channel, String target, String code) {
        String normalizedScene = normalizeScene(scene);
        String normalizedChannel = normalizeChannel(channel);
        String normalizedTarget = normalizeTarget(normalizedChannel, target);
        String key = key(normalizedScene, normalizedChannel, normalizedTarget);
        CodeEntry entry = codes.getIfPresent(key);
        Instant now = Instant.now();
        if (entry == null || now.isAfter(entry.expiresAt)) {
            codes.invalidate(key);
            throw new BizException(AuthCodes.OTP_INVALID, "验证码错误或已过期");
        }
        if (entry.attempts >= properties.getOtp().getMaxAttempts()) {
            codes.invalidate(key);
            throw new BizException(AuthCodes.OTP_INVALID, "验证码尝试次数过多，请重新获取");
        }
        boolean matched = Hashes.constantTimeEquals(entry.codeHash, hmac(key, code == null ? "" : code.trim()));
        if (!matched) {
            codes.put(key, entry.failed());
            throw new BizException(AuthCodes.OTP_INVALID, "验证码错误或已过期");
        }
        codes.invalidate(key);
    }

    public static String normalizeChannel(String channel) {
        if (channel == null) {
            throw new BizException(AuthCodes.BAD_REQUEST, "请选择验证码渠道");
        }
        String value = channel.trim().toUpperCase(Locale.ROOT);
        if (!CHANNEL_EMAIL.equals(value) && !CHANNEL_SMS.equals(value)) {
            throw new BizException(AuthCodes.BAD_REQUEST, "不支持的验证码渠道");
        }
        return value;
    }

    public static String normalizeScene(String scene) {
        if (scene == null) {
            throw new BizException(AuthCodes.BAD_REQUEST, "缺少验证码场景");
        }
        String value = scene.trim().toUpperCase(Locale.ROOT);
        if (!SCENE_REGISTER.equals(value) && !SCENE_FORGOT.equals(value) && !SCENE_LOGIN.equals(value)) {
            throw new BizException(AuthCodes.BAD_REQUEST, "不支持的验证码场景");
        }
        return value;
    }

    public static String normalizeTarget(String channel, String target) {
        if (target == null || target.isBlank()) {
            throw new BizException(AuthCodes.BAD_REQUEST, "请输入邮箱或手机号");
        }
        String value = target.trim();
        if (CHANNEL_EMAIL.equalsIgnoreCase(channel)) {
            if (!value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
                throw new BizException(AuthCodes.BAD_REQUEST, "邮箱格式不正确");
            }
            return value.toLowerCase(Locale.ROOT);
        }
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.startsWith("86") && digits.length() == 13) {
            digits = digits.substring(2);
        }
        if (!digits.matches("^1[3-9]\\d{9}$")) {
            throw new BizException(AuthCodes.BAD_REQUEST, "手机号格式不正确");
        }
        return digits;
    }

    public static String mask(String target) {
        if (target == null || target.length() < 4) {
            return "***";
        }
        if (target.contains("@")) {
            int at = target.indexOf('@');
            String prefix = target.substring(0, Math.min(2, at));
            return prefix + "***" + target.substring(at);
        }
        return target.substring(0, 3) + "****" + target.substring(target.length() - 4);
    }

    private String key(String scene, String channel, String target) {
        return scene + "|" + channel + "|" + target;
    }

    private String hmac(String key, String code) {
        return Hashes.hmacSha256Hex(properties.getJwt().getSecret(), key + ":" + code);
    }

    private record CodeEntry(String codeHash, Instant createdAt, Instant expiresAt, int attempts) {
        CodeEntry failed() {
            return new CodeEntry(codeHash, createdAt, expiresAt, attempts + 1);
        }
    }
}
