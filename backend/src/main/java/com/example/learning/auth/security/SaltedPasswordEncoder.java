package com.example.learning.auth.security;

import com.example.learning.auth.entity.AuthUser;
import com.example.learning.auth.service.PasswordService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 把数据库中分开保存的 salt/hash 适配为 Spring Security PasswordEncoder。
 *
 * <p>编码格式：{@code pbkdf2$iterations$base64Salt$base64Hash}。
 * DaoAuthenticationProvider 调用 matches() 时完成“取盐 + 慢哈希重算 + 常量时间比对”。</p>
 */
@Component
public class SaltedPasswordEncoder implements PasswordEncoder {

    private static final String PREFIX = "pbkdf2";

    private final PasswordService passwordService;

    public SaltedPasswordEncoder(PasswordService passwordService) {
        this.passwordService = passwordService;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        PasswordService.PasswordHash hash = passwordService.hash(rawPassword.toString());
        return format(hash);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        String[] parts = encodedPassword.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) {
            return false;
        }
        try {
            int iterations = Integer.parseInt(parts[1]);
            return passwordService.matches(rawPassword.toString(), parts[2], parts[3], iterations);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        if (encodedPassword == null) {
            return true;
        }
        String[] parts = encodedPassword.split("\\$");
        if (parts.length != 4) {
            return true;
        }
        try {
            return Integer.parseInt(parts[1]) < PasswordService.ITERATIONS;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    /** 将数据库中的 salt/hash 字段拼成 Spring Security 可识别的编码串。 */
    public String encodeExisting(AuthUser user) {
        return PREFIX + "$" + user.getPasswordIterations() + "$"
                + user.getPasswordSalt() + "$" + user.getPasswordHash();
    }

    private String format(PasswordService.PasswordHash hash) {
        return PREFIX + "$" + hash.iterations() + "$" + hash.salt() + "$" + hash.hash();
    }
}
