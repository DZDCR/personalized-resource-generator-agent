package com.example.learning.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 刷新令牌。只保存 SHA-256 摘要，明文仅在签发响应中通过 HttpOnly Cookie 下发一次。
 * 轮换时旧令牌立即标记 revoked，防止重放。
 */
@Data
@Entity
@Table(name = "auth_refresh_token", indexes = {
        @Index(name = "idx_auth_refresh_user", columnList = "user_id")
})
public class AuthRefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 会话激活角色编码，逗号分隔；刷新时恢复并按 DSD 重新校验。 */
    @Column(length = 512)
    private String activeRoleCodes;

    @Column(nullable = false)
    private int tokenVersion;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    @Column(length = 64)
    private String ip;

    @Column(length = 255)
    private String userAgent;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
