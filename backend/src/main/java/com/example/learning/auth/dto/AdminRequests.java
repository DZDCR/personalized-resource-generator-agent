package com.example.learning.auth.dto;

import com.example.learning.auth.entity.ConstraintType;
import com.example.learning.auth.entity.PermissionType;
import com.example.learning.auth.entity.UserStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * RBAC 管理端请求体。
 */
public final class AdminRequests {

    private AdminRequests() {
    }

    public record UserCreate(
            @NotBlank(message = "请输入用户名")
            @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{3,31}$",
                    message = "用户名需以字母开头，4-32 位字母/数字/下划线")
            String username,
            @NotBlank(message = "请输入初始密码")
            @Size(min = 10, max = 72, message = "密码长度需为 10-72 位")
            String password,
            @Size(max = 64, message = "昵称不能超过 64 字") String displayName,
            @Pattern(regexp = "^$|^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "邮箱格式不正确")
            String email,
            @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
            String phone,
            List<Long> roleIds) {
    }

    public record UserStatusUpdate(UserStatus status) {
    }

    public record UserRolesUpdate(List<Long> roleIds) {
    }

    public record AdminPasswordReset(
            @NotBlank(message = "请输入新密码")
            @Size(min = 10, max = 72, message = "密码长度需为 10-72 位")
            String newPassword) {
    }

    public record RoleUpsert(
            @NotBlank(message = "请输入角色编码")
            @Pattern(regexp = "^ROLE_[A-Z0-9_]{2,40}$", message = "角色编码需形如 ROLE_XXX")
            String code,
            @NotBlank(message = "请输入角色名称") @Size(max = 64) String name,
            @Size(max = 255) String description,
            Long parentId,
            Integer maxMembers,
            Long requiredRoleId,
            Integer sortOrder) {
    }

    public record PermissionUpsert(
            @NotBlank(message = "请输入权限编码")
            @Pattern(regexp = "^[a-z][a-z0-9_-]*(:[a-z0-9_-]+){1,3}$",
                    message = "权限编码需形如 resource:action")
            String code,
            @NotBlank(message = "请输入权限名称") @Size(max = 64) String name,
            PermissionType type,
            Long parentId,
            @Size(max = 255) String description,
            Integer sortOrder) {
    }

    public record PermissionAssign(
            @NotEmpty(message = "请选择权限") List<Long> permissionIds) {
    }

    public record ConstraintUpsert(
            @NotBlank(message = "请输入约束编码")
            @Pattern(regexp = "^[A-Z][A-Z0-9_]{2,63}$", message = "约束编码需为大写字母/数字/下划线")
            String code,
            @NotBlank(message = "请输入约束名称") @Size(max = 64) String name,
            ConstraintType type,
            Integer maxRoles,
            @Size(max = 255) String description,
            Boolean enabled,
            @NotEmpty(message = "请选择约束涉及的角色") List<Long> roleIds) {
    }
}
