package com.example.learning.auth.entity;

/**
 * RBAC2 职责分离约束类型：
 * <ul>
 *   <li>SSD：静态职责分离，用户直接拥有的角色集合中，同一约束内角色数不得超过 maxRoles。</li>
 *   <li>DSD：动态职责分离，单次会话激活的角色集合中，同一约束内角色数不得超过 maxRoles。</li>
 * </ul>
 * 角色基数（maxMembers）与角色前置依赖（requiredRoleId）作为角色属性实现。
 */
public enum ConstraintType {
    SSD,
    DSD
}
