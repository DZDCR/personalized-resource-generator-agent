package com.example.learning.auth.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 外部统一身份（OIDC / CAS）与本地用户的映射。
 * 演示环境使用 provider=OIDC-DEMO, subject=sso-demo-001。
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "auth_identity", uniqueConstraints = {
        @UniqueConstraint(name = "uk_auth_identity", columnNames = {"provider", "subject"})
})
public class AuthIdentity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(nullable = false, length = 128)
    private String subject;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
