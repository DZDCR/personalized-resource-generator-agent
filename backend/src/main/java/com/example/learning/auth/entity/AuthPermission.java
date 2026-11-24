package com.example.learning.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 权限点。code 采用 {@code resource:action} 形式，直接作为 Spring Security Authority。
 * parent_id 仅用于管理端权限树展示，不影响授权判定。
 */
@Data
@Entity
@Table(name = "auth_permission", uniqueConstraints = {
        @UniqueConstraint(name = "uk_auth_permission_code", columnNames = "code")
})
public class AuthPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PermissionType type = PermissionType.API;

    private Long parentId;

    @Column(length = 255)
    private String description;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean builtIn;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now();
    }
}
