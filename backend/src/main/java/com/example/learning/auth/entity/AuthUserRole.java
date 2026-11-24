package com.example.learning.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 用户-角色授予关系（RBAC0 核心关系）。
 */
@Data
@Entity
@Table(name = "auth_user_role", uniqueConstraints = {
        @UniqueConstraint(name = "uk_auth_user_role", columnNames = {"user_id", "role_id"})
})
public class AuthUserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(length = 64)
    private String grantedBy;

    @Column(nullable = false)
    private Instant grantedAt;

    @PrePersist
    void prePersist() {
        this.grantedAt = Instant.now();
    }
}
