package com.example.learning.auth.controller;

import com.example.learning.auth.dto.AdminRequests;
import com.example.learning.auth.dto.AuthViews.AuditView;
import com.example.learning.auth.dto.AuthViews.ConstraintView;
import com.example.learning.auth.dto.AuthViews.OverviewView;
import com.example.learning.auth.dto.AuthViews.PageView;
import com.example.learning.auth.dto.AuthViews.PermissionView;
import com.example.learning.auth.dto.AuthViews.RoleNode;
import com.example.learning.auth.dto.AuthViews.RoleView;
import com.example.learning.auth.dto.AuthViews.UserView;
import com.example.learning.auth.repository.AuthConstraintRepository;
import com.example.learning.auth.repository.AuthPermissionRepository;
import com.example.learning.auth.repository.AuthRoleRepository;
import com.example.learning.auth.repository.AuthUserRepository;
import com.example.learning.auth.security.ClientInfo;
import com.example.learning.auth.security.JwtClaims;
import com.example.learning.auth.service.AuthAuditService;
import com.example.learning.auth.service.ConstraintService;
import com.example.learning.auth.service.PermissionService;
import com.example.learning.auth.service.RbacQueryService;
import com.example.learning.auth.service.RoleService;
import com.example.learning.auth.service.UserAdminService;
import com.example.learning.common.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * RBAC3 管理端接口。所有接口均由 Spring Security @PreAuthorize 做方法级鉴权，
 * 权限编码来自 JWT 的 perms 声明。
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class RbacAdminController {

    private final UserAdminService userAdminService;
    private final RoleService roleService;
    private final PermissionService permissionService;
    private final ConstraintService constraintService;
    private final AuthAuditService auditService;
    private final RbacQueryService queryService;
    private final AuthUserRepository userRepository;
    private final AuthRoleRepository roleRepository;
    private final AuthPermissionRepository permissionRepository;
    private final AuthConstraintRepository constraintRepository;

    // ------------------------------------------------------------------
    // 概览
    // ------------------------------------------------------------------

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('rbac:admin')")
    public ApiResult<OverviewView> overview() {
        Instant today = AuthAuditService.startOfToday();
        return ApiResult.success(new OverviewView(
                userRepository.count(),
                roleRepository.count(),
                permissionRepository.count(),
                constraintRepository.count(),
                auditService.countLogins(true, today),
                auditService.countLogins(false, today),
                queryService.roleTree()));
    }

    // ------------------------------------------------------------------
    // 用户
    // ------------------------------------------------------------------

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('rbac:user:read')")
    public ApiResult<PageView<UserView>> users(@RequestParam(required = false) String keyword,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(userAdminService.search(keyword, page, size));
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("hasAuthority('rbac:user:read')")
    public ApiResult<UserView> user(@PathVariable Long id) {
        return ApiResult.success(userAdminService.get(id));
    }

    @PostMapping("/users")
    @PreAuthorize("hasAuthority('rbac:user:write')")
    public ApiResult<UserView> createUser(@Valid @RequestBody AdminRequests.UserCreate request,
                                          @AuthenticationPrincipal Jwt jwt,
                                          HttpServletRequest servletRequest) {
        return ApiResult.success(userAdminService.create(request, JwtClaims.username(jwt),
                ClientInfo.of(servletRequest)));
    }

    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasAuthority('rbac:user:write')")
    public ApiResult<UserView> updateStatus(@PathVariable Long id,
                                            @RequestBody AdminRequests.UserStatusUpdate request,
                                            @AuthenticationPrincipal Jwt jwt,
                                            HttpServletRequest servletRequest) {
        return ApiResult.success(userAdminService.updateStatus(id, request.status(), JwtClaims.userId(jwt),
                JwtClaims.username(jwt), ClientInfo.of(servletRequest)));
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("hasAuthority('rbac:user:write')")
    public ApiResult<UserView> assignRoles(@PathVariable Long id,
                                           @RequestBody AdminRequests.UserRolesUpdate request,
                                           @AuthenticationPrincipal Jwt jwt,
                                           HttpServletRequest servletRequest) {
        return ApiResult.success(userAdminService.assignRoles(id, request.roleIds(),
                JwtClaims.username(jwt), ClientInfo.of(servletRequest)));
    }

    @PostMapping("/users/{id}/unlock")
    @PreAuthorize("hasAuthority('rbac:user:write')")
    public ApiResult<UserView> unlock(@PathVariable Long id,
                                      @AuthenticationPrincipal Jwt jwt,
                                      HttpServletRequest servletRequest) {
        return ApiResult.success(userAdminService.unlock(id, JwtClaims.username(jwt),
                ClientInfo.of(servletRequest)));
    }

    @PutMapping("/users/{id}/password")
    @PreAuthorize("hasAuthority('rbac:user:write')")
    public ApiResult<Void> resetPassword(@PathVariable Long id,
                                         @Valid @RequestBody AdminRequests.AdminPasswordReset request,
                                         @AuthenticationPrincipal Jwt jwt,
                                         HttpServletRequest servletRequest) {
        userAdminService.resetPassword(id, request.newPassword(), JwtClaims.username(jwt),
                ClientInfo.of(servletRequest));
        return ApiResult.success();
    }

    // ------------------------------------------------------------------
    // 角色（RBAC1/2）
    // ------------------------------------------------------------------

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('rbac:role:read')")
    public ApiResult<List<RoleView>> roles() {
        return ApiResult.success(roleService.list());
    }

    @GetMapping("/roles/tree")
    @PreAuthorize("hasAuthority('rbac:role:read')")
    public ApiResult<List<RoleNode>> roleTree() {
        return ApiResult.success(roleService.tree());
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('rbac:role:write')")
    public ApiResult<RoleView> createRole(@Valid @RequestBody AdminRequests.RoleUpsert request,
                                          @AuthenticationPrincipal Jwt jwt,
                                          HttpServletRequest servletRequest) {
        return ApiResult.success(roleService.create(request, JwtClaims.username(jwt),
                ClientInfo.of(servletRequest)));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('rbac:role:write')")
    public ApiResult<RoleView> updateRole(@PathVariable Long id,
                                          @Valid @RequestBody AdminRequests.RoleUpsert request,
                                          @AuthenticationPrincipal Jwt jwt,
                                          HttpServletRequest servletRequest) {
        return ApiResult.success(roleService.update(id, request, JwtClaims.username(jwt),
                ClientInfo.of(servletRequest)));
    }

    @DeleteMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('rbac:role:write')")
    public ApiResult<Void> deleteRole(@PathVariable Long id,
                                      @AuthenticationPrincipal Jwt jwt,
                                      HttpServletRequest servletRequest) {
        roleService.delete(id, JwtClaims.username(jwt), ClientInfo.of(servletRequest));
        return ApiResult.success();
    }

    @GetMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('rbac:role:read')")
    public ApiResult<List<Long>> rolePermissions(@PathVariable Long id) {
        return ApiResult.success(roleService.permissionIds(id));
    }

    @PutMapping("/roles/{id}/permissions")
    @PreAuthorize("hasAuthority('rbac:role:write')")
    public ApiResult<RoleView> assignPermissions(@PathVariable Long id,
                                                 @Valid @RequestBody AdminRequests.PermissionAssign request,
                                                 @AuthenticationPrincipal Jwt jwt,
                                                 HttpServletRequest servletRequest) {
        return ApiResult.success(roleService.assignPermissions(id, request.permissionIds(),
                JwtClaims.username(jwt), ClientInfo.of(servletRequest)));
    }

    // ------------------------------------------------------------------
    // 权限点
    // ------------------------------------------------------------------

    @GetMapping({"/permissions", "/permissions/tree"})
    @PreAuthorize("hasAuthority('rbac:permission:read')")
    public ApiResult<List<PermissionView>> permissions() {
        return ApiResult.success(permissionService.tree());
    }

    @PostMapping("/permissions")
    @PreAuthorize("hasAuthority('rbac:permission:write')")
    public ApiResult<PermissionView> createPermission(@Valid @RequestBody AdminRequests.PermissionUpsert request,
                                                      @AuthenticationPrincipal Jwt jwt,
                                                      HttpServletRequest servletRequest) {
        return ApiResult.success(permissionService.create(request, JwtClaims.username(jwt),
                ClientInfo.of(servletRequest)));
    }

    @PutMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('rbac:permission:write')")
    public ApiResult<PermissionView> updatePermission(@PathVariable Long id,
                                                      @Valid @RequestBody AdminRequests.PermissionUpsert request,
                                                      @AuthenticationPrincipal Jwt jwt,
                                                      HttpServletRequest servletRequest) {
        return ApiResult.success(permissionService.update(id, request, JwtClaims.username(jwt),
                ClientInfo.of(servletRequest)));
    }

    @DeleteMapping("/permissions/{id}")
    @PreAuthorize("hasAuthority('rbac:permission:write')")
    public ApiResult<Void> deletePermission(@PathVariable Long id,
                                            @AuthenticationPrincipal Jwt jwt,
                                            HttpServletRequest servletRequest) {
        permissionService.delete(id, JwtClaims.username(jwt), ClientInfo.of(servletRequest));
        return ApiResult.success();
    }

    // ------------------------------------------------------------------
    // 职责分离约束（RBAC2）
    // ------------------------------------------------------------------

    @GetMapping("/constraints")
    @PreAuthorize("hasAuthority('rbac:constraint:read')")
    public ApiResult<List<ConstraintView>> constraints() {
        return ApiResult.success(constraintService.list());
    }

    @PostMapping("/constraints")
    @PreAuthorize("hasAuthority('rbac:constraint:write')")
    public ApiResult<ConstraintView> createConstraint(@Valid @RequestBody AdminRequests.ConstraintUpsert request,
                                                      @AuthenticationPrincipal Jwt jwt,
                                                      HttpServletRequest servletRequest) {
        return ApiResult.success(constraintService.create(request, JwtClaims.username(jwt),
                ClientInfo.of(servletRequest)));
    }

    @PutMapping("/constraints/{id}")
    @PreAuthorize("hasAuthority('rbac:constraint:write')")
    public ApiResult<ConstraintView> updateConstraint(@PathVariable Long id,
                                                      @Valid @RequestBody AdminRequests.ConstraintUpsert request,
                                                      @AuthenticationPrincipal Jwt jwt,
                                                      HttpServletRequest servletRequest) {
        return ApiResult.success(constraintService.update(id, request, JwtClaims.username(jwt),
                ClientInfo.of(servletRequest)));
    }

    @DeleteMapping("/constraints/{id}")
    @PreAuthorize("hasAuthority('rbac:constraint:write')")
    public ApiResult<Void> deleteConstraint(@PathVariable Long id,
                                            @AuthenticationPrincipal Jwt jwt,
                                            HttpServletRequest servletRequest) {
        constraintService.delete(id, JwtClaims.username(jwt), ClientInfo.of(servletRequest));
        return ApiResult.success();
    }

    // ------------------------------------------------------------------
    // 审计
    // ------------------------------------------------------------------

    @GetMapping("/audit")
    @PreAuthorize("hasAuthority('rbac:audit:read')")
    public ApiResult<PageView<AuditView>> audit(
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResult.success(auditService.search(eventType, username, success, from, to, page, size));
    }
}
