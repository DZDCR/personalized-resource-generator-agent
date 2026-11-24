package com.example.learning.auth.dto;

import java.time.Instant;
import java.util.List;

/**
 * 认证/RBAC 响应视图。统一使用 record，避免把实体（含密码字段）泄漏到接口。
 */
public final class AuthViews {

    private AuthViews() {
    }

    public record RoleBrief(Long id, String code, String name) {
    }

    public record UserView(
            Long id,
            String username,
            String displayName,
            String email,
            String phone,
            String status,
            boolean locked,
            int failedAttempts,
            Instant lastLoginAt,
            String lastLoginIp,
            Instant createdAt,
            List<RoleBrief> roles) {
    }

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long expiresIn,
            UserView user,
            List<RoleBrief> assignedRoles,
            List<String> activeRoleCodes,
            List<String> permissions) {
    }

    public record MeView(
            UserView user,
            List<RoleBrief> assignedRoles,
            List<String> activeRoleCodes,
            List<String> permissions) {
    }

    public record CaptchaView(String captchaId, String image) {
    }

    public record SendCodeView(boolean sent, String message, String debugCode, int expiresIn) {
    }

    /** 动态职责分离冲突时返回，前端据此弹出角色选择框。 */
    public record RoleChoiceView(
            boolean roleChoiceRequired,
            String reason,
            List<RoleBrief> conflictingRoles,
            List<RoleBrief> options,
            List<String> activeRoleCodes) {
    }

    public record RoleView(
            Long id,
            String code,
            String name,
            String description,
            Long parentId,
            String parentName,
            Integer maxMembers,
            long memberCount,
            Long requiredRoleId,
            String requiredRoleName,
            boolean builtIn,
            int sortOrder,
            List<Long> permissionIds) {
    }

    public record RoleNode(
            Long id,
            String code,
            String name,
            String description,
            Long parentId,
            Integer maxMembers,
            long memberCount,
            boolean builtIn,
            List<RoleNode> children) {
    }

    public record PermissionView(
            Long id,
            String code,
            String name,
            String type,
            Long parentId,
            String description,
            boolean builtIn,
            int sortOrder,
            List<PermissionView> children) {
    }

    public record ConstraintView(
            Long id,
            String code,
            String name,
            String type,
            int maxRoles,
            String description,
            boolean enabled,
            List<RoleBrief> roles) {
    }

    public record AuditView(
            Long id,
            String eventType,
            String username,
            Long userId,
            boolean success,
            String ip,
            String userAgent,
            String detail,
            Instant createdAt) {
    }

    public record PageView<T>(List<T> items, long total, int page, int size) {
    }

    public record OverviewView(
            long users,
            long roles,
            long permissions,
            long constraints,
            long loginSuccessToday,
            long loginFailureToday,
            List<RoleNode> roleTree) {
    }

    public record SsoAuthorizeView(String authorizationUrl, String state) {
    }
}
