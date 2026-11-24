package com.example.learning.auth.service;

import com.example.learning.auth.security.Hashes;
import com.example.learning.common.BizException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 慢哈希口令服务。
 *
 * <p>流程与作业要求逐条对应：</p>
 * <ol>
 *   <li>注册/改密：随机 16 字节盐 + PBKDF2-HMAC-SHA256(210000 次) 派生 256bit 密钥；</li>
 *   <li>登录：按用户名取出 password_salt 与 password_hash；</li>
 *   <li>用同一盐与迭代次数重算，MessageDigest.isEqual 常量时间比对；</li>
 *   <li>未知账号也执行一次等价慢哈希，避免账号枚举与时序侧信道。</li>
 * </ol>
 */
@Service
public class PasswordService {

    public static final String ALGORITHM = "PBKDF2-SHA256";
    public static final int ITERATIONS = 210_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BITS = 256;

    private final byte[] dummySalt;
    private final String dummyHash;

    public PasswordService() {
        this.dummySalt = Hashes.randomBytes(SALT_BYTES);
        this.dummyHash = derive("dummy-password-for-timing", dummySalt, ITERATIONS);
    }

    public PasswordHash hash(String rawPassword) {
        byte[] salt = Hashes.randomBytes(SALT_BYTES);
        String hash = derive(rawPassword, salt, ITERATIONS);
        return new PasswordHash(
                Base64.getEncoder().encodeToString(salt),
                hash,
                ITERATIONS,
                ALGORITHM);
    }

    /**
     * 慢哈希重算比对。salt/hash 均以 Base64 存储。
     */
    public boolean matches(String rawPassword, String saltBase64, String hashBase64, int iterations) {
        if (rawPassword == null || saltBase64 == null || hashBase64 == null || iterations <= 0) {
            return false;
        }
        try {
            byte[] salt = Base64.getDecoder().decode(saltBase64);
            String actual = derive(rawPassword, salt, iterations);
            return MessageDigest.isEqual(
                    actual.getBytes(StandardCharsets.US_ASCII),
                    hashBase64.getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** 未知账号分支：执行一次同成本计算，返回值无意义。 */
    public void burnEquivalentTime(String rawPassword) {
        derive(rawPassword == null ? "" : rawPassword, dummySalt, ITERATIONS);
        MessageDigest.isEqual(dummyHash.getBytes(StandardCharsets.US_ASCII),
                dummyHash.getBytes(StandardCharsets.US_ASCII));
    }

    private String derive(String rawPassword, byte[] salt, int iterations) {
        char[] chars = rawPassword.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, KEY_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] encoded = factory.generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(encoded);
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 不可用", e);
        } finally {
            spec.clearPassword();
            java.util.Arrays.fill(chars, '\0');
        }
    }

    public record PasswordHash(String salt, String hash, int iterations, String algorithm) {
    }

    /**
     * 口令强度：至少 10 位，且大写/小写/数字/特殊字符四类中至少三类。
     */
    public void validateStrength(String password, String username, String email) {
        if (password == null || password.length() < 10 || password.length() > 72) {
            throw new BizException(40010, "密码长度需为 10-72 位");
        }
        int classes = 0;
        if (password.chars().anyMatch(Character::isLowerCase)) classes++;
        if (password.chars().anyMatch(Character::isUpperCase)) classes++;
        if (password.chars().anyMatch(Character::isDigit)) classes++;
        if (password.chars().anyMatch(c -> !Character.isLetterOrDigit(c))) classes++;
        if (classes < 3) {
            throw new BizException(40011, "密码需包含大写、小写、数字、特殊字符中的至少三类");
        }
        String lower = password.toLowerCase();
        if (username != null && !username.isBlank() && lower.contains(username.toLowerCase())) {
            throw new BizException(40012, "密码不能包含用户名");
        }
        if (email != null && email.contains("@")) {
            String prefix = email.substring(0, email.indexOf('@')).toLowerCase();
            if (prefix.length() >= 3 && lower.contains(prefix)) {
                throw new BizException(40013, "密码不能包含邮箱前缀");
            }
        }
    }
}
