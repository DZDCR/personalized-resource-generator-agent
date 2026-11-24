package com.example.learning.auth.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * 从 JWT 中读取当前会话信息的工具。
 */
public final class JwtClaims {

    private JwtClaims() {
    }

    public static Long userId(Jwt jwt) {
        Object value = jwt.getClaim("uid");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return Long.parseLong(text);
        }
        throw new IllegalStateException("JWT 缺少 uid");
    }

    public static String username(Jwt jwt) {
        return jwt.getSubject();
    }

    public static List<String> activeRoles(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }

    public static List<String> permissions(Jwt jwt) {
        List<String> permissions = jwt.getClaimAsStringList("perms");
        return permissions == null ? List.of() : permissions;
    }
}
