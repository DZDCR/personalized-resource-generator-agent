package com.example.learning.auth.config;

import com.example.learning.auth.entity.AuthConstraint;
import com.example.learning.auth.entity.AuthConstraintRole;
import com.example.learning.auth.entity.AuthIdentity;
import com.example.learning.auth.entity.AuthPermission;
import com.example.learning.auth.entity.AuthRole;
import com.example.learning.auth.entity.AuthRoleClosure;
import com.example.learning.auth.entity.AuthRolePermission;
import com.example.learning.auth.entity.AuthUser;
import com.example.learning.auth.entity.AuthUserRole;
import com.example.learning.auth.entity.ConstraintType;
import com.example.learning.auth.entity.PermissionType;
import com.example.learning.auth.entity.UserStatus;
import com.example.learning.auth.repository.AuthConstraintRepository;
import com.example.learning.auth.repository.AuthConstraintRoleRepository;
import com.example.learning.auth.repository.AuthIdentityRepository;
import com.example.learning.auth.repository.AuthPermissionRepository;
import com.example.learning.auth.repository.AuthRoleClosureRepository;
import com.example.learning.auth.repository.AuthRolePermissionRepository;
import com.example.learning.auth.repository.AuthRoleRepository;
import com.example.learning.auth.repository.AuthUserRepository;
import com.example.learning.auth.repository.AuthUserRoleRepository;
import com.example.learning.auth.service.PasswordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 内置 RBAC3 数据初始化。
 *
 * <p>仅在 auth_role 为空时执行，避免覆盖线上授权。演示口令只用于本地开发，生产必须删除/重置。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthDataInitializer implements ApplicationRunner {

    private final AuthUserRepository userRepository;
    private final AuthRoleRepository roleRepository;
    private final AuthPermissionRepository permissionRepository;
    private final AuthUserRoleRepository userRoleRepository;
    private final AuthRolePermissionRepository rolePermissionRepository;
    private final AuthRoleClosureRepository closureRepository;
    private final AuthConstraintRepository constraintRepository;
    private final AuthConstraintRoleRepository constraintRoleRepository;
    private final AuthIdentityRepository identityRepository;
    private final PasswordService passwordService;
    private final AuthProperties properties;

    private final Map<String, AuthRole> roles = new HashMap<>();
    private final Map<String, AuthPermission> permissions = new HashMap<>();

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isSeedEnabled()) {
            log.info("auth seed disabled");
            return;
        }
        if (roleRepository.count() > 0) {
            log.info("auth seed skipped: roles already exist");
            return;
        }
        log.info("initializing RBAC3 seed data (roles/permissions/demo users)");
        seedPermissions();
        seedRoles();
        seedRolePermissions();
        rebuildClosure();
        seedUsers();
        seedConstraints();
        log.info("RBAC3 seed completed: {} roles, {} permissions, {} users",
                roleRepository.count(), permissionRepository.count(), userRepository.count());
    }

    private void seedPermissions() {
        permission("menu:learning", "学习中心", PermissionType.MENU, null, 10);
        permission("menu:admin", "权限中心", PermissionType.MENU, null, 20);

        permission("resource:read", "查看学习资源", PermissionType.API, "menu:learning", 110);
        permission("resource:generate", "生成学习资源", PermissionType.BUTTON, "menu:learning", 120);
        permission("resource:review", "审核学习资源", PermissionType.BUTTON, "menu:learning", 130);
        permission("resource:publish", "发布学习资源", PermissionType.BUTTON, "menu:learning", 140);
        permission("resource:delete", "删除学习资源", PermissionType.BUTTON, "menu:learning", 150);
        permission("path:read", "查看学习路径", PermissionType.API, "menu:learning", 160);
        permission("path:write", "调整学习路径", PermissionType.BUTTON, "menu:learning", 170);
        permission("profile:read", "查看学习画像", PermissionType.API, "menu:learning", 180);
        permission("profile:write", "更新学习画像", PermissionType.BUTTON, "menu:learning", 190);

        permission("system:ops", "系统运营", PermissionType.API, "menu:admin", 210);
        permission("rbac:user:read", "查看用户", PermissionType.API, "menu:admin", 220);
        permission("rbac:user:write", "管理用户", PermissionType.BUTTON, "menu:admin", 230);
        permission("rbac:role:read", "查看角色", PermissionType.API, "menu:admin", 240);
        permission("rbac:role:write", "管理角色", PermissionType.BUTTON, "menu:admin", 250);
        permission("rbac:permission:read", "查看权限", PermissionType.API, "menu:admin", 260);
        permission("rbac:permission:write", "管理权限", PermissionType.BUTTON, "menu:admin", 270);
        permission("rbac:constraint:read", "查看职责分离约束", PermissionType.API, "menu:admin", 280);
        permission("rbac:constraint:write", "管理职责分离约束", PermissionType.BUTTON, "menu:admin", 290);
        permission("rbac:audit:read", "查看审计日志", PermissionType.API, "menu:admin", 300);
        permission("rbac:admin", "进入权限中心", PermissionType.MENU, "menu:admin", 310);
    }

    private AuthPermission permission(String code, String name, PermissionType type,
                                      String parentCode, int sort) {
        AuthPermission entity = new AuthPermission();
        entity.setCode(code);
        entity.setName(name);
        entity.setType(type);
        entity.setParentId(parentCode == null ? null : permissions.get(parentCode).getId());
        entity.setSortOrder(sort);
        entity.setBuiltIn(true);
        entity.setDescription(code);
        permissionRepository.save(entity);
        permissions.put(code, entity);
        return entity;
    }

    private void seedRoles() {
        role("ROLE_STUDENT", "学生", "基础学习角色", null, null, null, 10);
        role("ROLE_TEACHER", "教师", "继承学生权限，可生成资源与调整路径", "ROLE_STUDENT", null, null, 20);
        role("ROLE_CONTENT_REVIEWER", "内容审核员", "审核资源质量，继承教师权限", "ROLE_TEACHER", null, null, 30);
        role("ROLE_PUBLISHER", "资源发布员", "发布审核通过的资源", "ROLE_TEACHER", null, "ROLE_TEACHER", 40);
        role("ROLE_OPS_ADMIN", "运营管理员", "运营与用户支持", "ROLE_CONTENT_REVIEWER", null, null, 50);
        role("ROLE_SYS_ADMIN", "系统管理员", "RBAC 管理", null, 3, null, 60);
        role("ROLE_SUPER_ADMIN", "超级管理员", "全部权限，继承系统管理员", "ROLE_SYS_ADMIN", 2, null, 70);
        role("ROLE_SECURITY_AUDITOR", "安全审计员", "独立审计角色", null, 2, null, 80);
    }

    private AuthRole role(String code, String name, String description, String parentCode,
                          Integer maxMembers, String requiredRoleCode, int sort) {
        AuthRole entity = new AuthRole();
        entity.setCode(code);
        entity.setName(name);
        entity.setDescription(description);
        entity.setParentId(parentCode == null ? null : roles.get(parentCode).getId());
        entity.setMaxMembers(maxMembers);
        entity.setRequiredRoleId(requiredRoleCode == null ? null : roles.get(requiredRoleCode).getId());
        entity.setSortOrder(sort);
        entity.setBuiltIn(true);
        roleRepository.save(entity);
        roles.put(code, entity);
        return entity;
    }

    private void seedRolePermissions() {
        grant("ROLE_STUDENT", "resource:read", "path:read", "profile:read", "profile:write");
        grant("ROLE_TEACHER", "resource:generate", "path:write");
        grant("ROLE_CONTENT_REVIEWER", "resource:review");
        grant("ROLE_PUBLISHER", "resource:publish");
        grant("ROLE_OPS_ADMIN", "system:ops", "resource:delete", "rbac:user:read",
                "rbac:audit:read", "rbac:admin");
        grant("ROLE_SYS_ADMIN", "rbac:user:read", "rbac:user:write", "rbac:role:read", "rbac:role:write",
                "rbac:permission:read", "rbac:permission:write", "rbac:constraint:read",
                "rbac:constraint:write", "rbac:audit:read", "rbac:admin", "system:ops",
                "resource:delete");
        grant("ROLE_SUPER_ADMIN", permissions.keySet().toArray(String[]::new));
        grant("ROLE_SECURITY_AUDITOR", "rbac:audit:read", "rbac:constraint:read",
                "rbac:user:read", "rbac:permission:read", "rbac:role:read", "rbac:admin");
    }

    private void grant(String roleCode, String... permissionCodes) {
        AuthRole role = roles.get(roleCode);
        for (String code : permissionCodes) {
            AuthPermission permission = permissions.get(code);
            if (permission == null) {
                throw new IllegalStateException("未知权限编码：" + code);
            }
            AuthRolePermission link = new AuthRolePermission();
            link.setRoleId(role.getId());
            link.setPermissionId(permission.getId());
            rolePermissionRepository.save(link);
        }
    }

    private void rebuildClosure() {
        Map<Long, Long> parentById = new HashMap<>();
        roles.values().forEach(role -> parentById.put(role.getId(), role.getParentId()));
        List<AuthRoleClosure> rows = new ArrayList<>();
        for (AuthRole role : roles.values()) {
            Long current = role.getId();
            int depth = 0;
            Set<Long> seen = new LinkedHashSet<>();
            while (current != null && seen.add(current)) {
                rows.add(new AuthRoleClosure(role.getId(), current, depth++));
                current = parentById.get(current);
            }
        }
        closureRepository.saveAll(rows);
    }

    private void seedUsers() {
        AuthUser admin = user("admin", "Admin@12345", "系统管理员", "admin@example.com", "13800000001",
                "ROLE_SUPER_ADMIN");
        user("sysadmin", "SysAdmin@12345", "系统管理员（RBAC）", "sysadmin@example.com", "13800000002",
                "ROLE_SYS_ADMIN");
        user("auditor", "Auditor@12345", "安全审计员", "auditor@example.com", "13800000003",
                "ROLE_SECURITY_AUDITOR");
        user("teacher", "Teacher@12345", "王老师", "teacher@example.com", "13800000004",
                "ROLE_TEACHER");
        user("student", "Student@12345", "李同学", "student@example.com", "13800000005",
                "ROLE_STUDENT");
        user("reviewer", "Reviewer@12345", "张审核", "reviewer@example.com", "13800000006",
                "ROLE_TEACHER", "ROLE_CONTENT_REVIEWER", "ROLE_PUBLISHER");
        user("ops", "Ops@12345", "运营小赵", "ops@example.com", "13800000007", "ROLE_OPS_ADMIN");
        AuthUser ssoUser = user("sso_user", "Sso@12345", "统一身份用户", "sso@example.com", "13800000008",
                "ROLE_STUDENT");

        AuthIdentity identity = new AuthIdentity();
        identity.setProvider("OIDC-DEMO");
        identity.setSubject("sso-demo-001");
        identity.setUserId(ssoUser.getId());
        identityRepository.save(identity);

        log.info("demo accounts seeded: admin / sysadmin / auditor / teacher / student / reviewer / ops / sso_user");
        log.info("demo admin password: Admin@12345");
    }

    private AuthUser user(String username, String rawPassword, String displayName,
                          String email, String phone, String... roleCodes) {
        AuthUser user = new AuthUser();
        user.setUsername(username);
        user.setDisplayName(displayName);
        user.setEmail(email);
        user.setPhone(phone);
        PasswordService.PasswordHash hash = passwordService.hash(rawPassword);
        user.setPasswordSalt(hash.salt());
        user.setPasswordHash(hash.hash());
        user.setPasswordIterations(hash.iterations());
        user.setPasswordAlgo(hash.algorithm());
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(1);
        user.setLastLoginAt(null);
        userRepository.save(user);

        for (String roleCode : roleCodes) {
            AuthRole role = roles.get(roleCode);
            AuthUserRole link = new AuthUserRole();
            link.setUserId(user.getId());
            link.setRoleId(role.getId());
            link.setGrantedBy("SYSTEM_SEED");
            userRoleRepository.save(link);
        }
        return user;
    }

    private void seedConstraints() {
        constraint("SSD_ADMIN_AUDITOR", "管理员与审计员互斥", ConstraintType.SSD, 1,
                "同一用户不能同时拥有系统管理员与安全审计员角色（静态职责分离）。",
                "ROLE_SYS_ADMIN", "ROLE_SECURITY_AUDITOR");
        constraint("DSD_MAKER_CHECKER", "审核与发布分离", ConstraintType.DSD, 1,
                "单次会话不能同时激活内容审核员与资源发布员（动态职责分离，模拟 maker-checker）。",
                "ROLE_CONTENT_REVIEWER", "ROLE_PUBLISHER");
    }

    private void constraint(String code, String name, ConstraintType type, int maxRoles,
                            String description, String... roleCodes) {
        AuthConstraint entity = new AuthConstraint();
        entity.setCode(code);
        entity.setName(name);
        entity.setType(type);
        entity.setMaxRoles(maxRoles);
        entity.setDescription(description);
        entity.setEnabled(true);
        constraintRepository.save(entity);
        for (String roleCode : roleCodes) {
            AuthConstraintRole link = new AuthConstraintRole(entity.getId(), roles.get(roleCode).getId());
            constraintRoleRepository.save(link);
        }
    }
}
