package com.example.learning.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 认证用户（RBAC 主体）。
 *
 * <p>密码不以明文或可逆形式存储：password_salt 与 password_hash 分列保存，
 * 由 {@code PasswordService} 使用 PBKDF2-HMAC-SHA256 慢哈希重算比对。</p>
 */
@Data
@Entity
@Table(name = "auth_user", indexes = {
        @Index(name = "idx_auth_user_email", columnList = "email"),
        @Index(name = "idx_auth_user_phone", columnList = "phone")
})
public class AuthUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录名，大小写不敏感唯一（写入时统一小写）。 */
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Column(length = 64)
    private String displayName;

    @Column(unique = true, length = 128)
    private String email;

    @Column(unique = true, length = 32)
    private String phone;

    /** Base64 编码的每用户随机盐。 */
    @Column(nullable = false, length = 64)
    private String passwordSalt;

    /** Base64 编码的派生密钥。 */
    @Column(nullable = false, length = 128)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String passwordAlgo = "PBKDF2-SHA256";

    @Column(nullable = false)
    private int passwordIterations;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false)
    private int failedAttempts;

    private Instant lockedUntil;

    /** 令牌版本：角色/密码/状态变更时递增，使旧刷新令牌与短期访问令牌失效。 */
    @Column(nullable = false)
    private int tokenVersion;

    private Instant lastLoginAt;

    @Column(length = 64)
    private String lastLoginIp;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
