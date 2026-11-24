package com.example.learning.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 职责分离约束（RBAC2）。约束通过 auth_constraint_role 关联若干角色。
 */
@Data
@Entity
@Table(name = "auth_constraint", uniqueConstraints = {
        @UniqueConstraint(name = "uk_auth_constraint_code", columnNames = "code")
})
public class AuthConstraint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ConstraintType type;

    /** 同一约束内允许同时拥有/激活的最大角色数。互斥场景通常为 1。 */
    @Column(nullable = false)
    private int maxRoles = 1;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

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
