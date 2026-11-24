package com.example.learning.auth.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 认证/授权审计日志。登录失败原因、角色变更、约束变更均落库。
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "auth_audit_log", indexes = {
        @Index(name = "idx_auth_audit_created", columnList = "created_at"),
        @Index(name = "idx_auth_audit_user", columnList = "user_id")
})
public class AuthAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String eventType;

    @Column(length = 64)
    private String username;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 64)
    private String ip;

    @Column(length = 255)
    private String userAgent;

    @Column(length = 1000)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
