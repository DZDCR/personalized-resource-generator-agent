package com.example.learning.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 角色。parent_id 指向本角色继承的基线角色，形成 RBAC1 继承链：
 * 用户拥有派生角色时，沿 parent_id 链自动获得基线角色的权限。
 * max_members / required_role_id 表达 RBAC2 的角色基数与角色前置依赖。
 */
@Data
@Entity
@Table(name = "auth_role", uniqueConstraints = {
        @UniqueConstraint(name = "uk_auth_role_code", columnNames = "code")
})
public class AuthRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 角色编码，统一大写，例如 ROLE_SYS_ADMIN。 */
    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(length = 255)
    private String description;

    /** 继承自的基线角色；为空表示不继承其他角色。 */
    private Long parentId;

    /** 该角色最大直接成员数；null 表示不限制（RBAC2 基数约束）。 */
    private Integer maxMembers;

    /** 授予本角色前用户必须已具备的角色（RBAC2 前置依赖）。 */
    private Long requiredRoleId;

    /** 内置角色禁止删除，避免系统失去管理入口。 */
    @Column(nullable = false)
    private boolean builtIn;

    @Column(nullable = false)
    private int sortOrder;

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
