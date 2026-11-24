package com.example.learning.auth.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色继承闭包表（RBAC1）。每行 (ancestor, descendant, depth) 表示
 * 拥有 ancestor 角色的用户同时获得 descendant 角色（含自身，depth=0）。
 * ancestor 是派生/高级角色，descendant 是其继承的基线角色。
 * 使用闭包表可把任意深度的继承查询降为单表 JOIN，避免递归 SQL 的数据库差异。
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "auth_role_closure", uniqueConstraints = {
        @UniqueConstraint(name = "uk_auth_role_closure", columnNames = {"ancestor_id", "descendant_id"})
}, indexes = {
        @Index(name = "idx_auth_closure_descendant", columnList = "descendant_id")
})
public class AuthRoleClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ancestor_id", nullable = false)
    private Long ancestorId;

    @Column(name = "descendant_id", nullable = false)
    private Long descendantId;

    @Column(nullable = false)
    private int depth;

    public AuthRoleClosure(Long ancestorId, Long descendantId, int depth) {
        this.ancestorId = ancestorId;
        this.descendantId = descendantId;
        this.depth = depth;
    }
}
