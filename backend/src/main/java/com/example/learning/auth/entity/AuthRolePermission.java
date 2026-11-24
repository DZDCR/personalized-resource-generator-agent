package com.example.learning.auth.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 角色-权限授予关系（RBAC0 核心关系）。
 */
@Data
@Entity
@Table(name = "auth_role_permission", uniqueConstraints = {
        @UniqueConstraint(name = "uk_auth_role_permission", columnNames = {"role_id", "permission_id"})
})
public class AuthRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;
}
