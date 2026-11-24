package com.example.learning.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RBAC3 认证模块配置，前缀 app.auth。
 * 生产环境必须通过环境变量覆盖 JWT_SECRET、SSO_CLIENT_SECRET，并关闭 dev-mode。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** 开发模式：验证码/邮件/短信验证码直接返回并写入 dev outbox；生产必须 false。 */
    private boolean devMode = false;

    /** 首次启动是否写入内置角色、权限与演示账号。 */
    private boolean seedEnabled = true;

    /** 生产环境（HTTPS）置为 true，刷新令牌 Cookie 仅走加密连接。 */
    private boolean secureCookie = false;

    private Jwt jwt = new Jwt();
    private Captcha captcha = new Captcha();
    private Otp otp = new Otp();
    private Lock lock = new Lock();
    private Sso sso = new Sso();
    private Rbac rbac = new Rbac();
    private Cors cors = new Cors();

    @Data
    public static class Jwt {
        private String secret = "learning-agent-rbac3-dev-secret-change-me-2026";
        private String issuer = "learning-agent";
        private long accessTtlMinutes = 15;
        private long refreshTtlDays = 7;
    }

    @Data
    public static class Captcha {
        private boolean enabled = true;
        private long ttlSeconds = 120;
        private int length = 4;
    }

    @Data
    public static class Otp {
        private long ttlSeconds = 300;
        private long resendSeconds = 60;
        private int maxAttempts = 5;
        private int maxSendsPerHour = 8;
    }

    @Data
    public static class Lock {
        private int maxFailedAttempts = 5;
        private long lockMinutes = 15;
    }

    @Data
    public static class Sso {
        private String provider = "OIDC-DEMO";
        private String clientId = "learning-web";
        private String clientSecret = "sso-demo-secret";
        private String issuer = "http://localhost:8080/api/auth/sso";
        private String demoSubject = "sso-demo-001";
        private long codeTtlSeconds = 60;
        private List<String> redirectUris = new ArrayList<>(List.of(
                "http://localhost:5173/sso/callback",
                "http://127.0.0.1:5173/sso/callback",
                "http://localhost:8081/sso/callback"
        ));
    }

    @Data
    public static class Rbac {
        private int maxRolesPerUser = 8;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>(List.of(
                "http://localhost:5173", "http://127.0.0.1:5173"
        ));
    }
}
