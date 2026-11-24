package com.example.learning.auth.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 请求来源信息（审计/限流用）。
 */
public record ClientInfo(String ip, String userAgent) {

    public static ClientInfo of(HttpServletRequest request) {
        if (request == null) {
            return new ClientInfo("unknown", "unknown");
        }
        String ip = firstNonBlank(
                request.getHeader("X-Forwarded-For"),
                request.getHeader("X-Real-IP"),
                request.getRemoteAddr());
        if (ip != null && ip.contains(",")) {
            ip = ip.substring(0, ip.indexOf(',')).trim();
        }
        String ua = request.getHeader("User-Agent");
        return new ClientInfo(truncate(ip, 64), truncate(ua, 255));
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "unknown";
    }

    private static String truncate(String value, int max) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
