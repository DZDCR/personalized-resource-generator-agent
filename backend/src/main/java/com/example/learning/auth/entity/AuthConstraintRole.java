package com.example.learning.auth.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 约束-角色成员关系。
 */
@Data
@Entity
@NoArgsConstructor
@Table(name = "auth_constraint_role", uniqueConstraints = {
        @UniqueConstraint(name = "uk_auth_constraint_role", columnNames = {"constraint_id", "role_id"})
})
public class AuthConstraintRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "constraint_id", nullable = false)
    private Long constraintId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    public AuthConstraintRole(Long constraintId, Long roleId) {
        this.constraintId = constraintId;
        this.roleId = roleId;
    }
}
