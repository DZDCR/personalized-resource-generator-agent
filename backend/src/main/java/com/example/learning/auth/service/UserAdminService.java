package com.example.learning.auth.service;

import com.example.learning.auth.dto.AdminRequests;
import com.example.learning.auth.dto.AuthViews.PageView;
import com.example.learning.auth.dto.AuthViews.RoleBrief;
import com.example.learning.auth.dto.AuthViews.UserView;
import com.example.learning.auth.entity.AuthRole;
import com.example.learning.auth.entity.AuthUser;
import com.example.learning.auth.entity.AuthUserRole;
import com.example.learning.auth.entity.UserStatus;
import com.example.learning.auth.repository.AuthRefreshTokenRepository;
import com.example.learning.auth.repository.AuthRoleRepository;
import com.example.learning.auth.repository.AuthUserRepository;
import com.example.learning.auth.repository.AuthUserRoleRepository;
import com.example.learning.auth.security.AuthCodes;
import com.example.learning.auth.security.ClientInfo;
import com.example.learning.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 用户管理（RBAC 主体）：查询、创建、启停、角色授予、解锁、重置密码。
 * 角色授予必须通过 ConstraintService 的 SSD/基数/前置角色校验。
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final AuthUserRepository userRepository;
    private final AuthRoleRepository roleRepository;
    private final AuthUserRoleRepository userRoleRepository;
    private final AuthRefreshTokenRepository refreshTokenRepository;
    private final PasswordService passwordService;
    private final ConstraintService constraintService;
    private final RbacQueryService queryService;
    private final AuthAuditService auditService;

    @Transactional(readOnly = true)
    public PageView<UserView> search(String keyword, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<AuthUser> result;
        if (keyword == null || keyword.isBlank()) {
            result = userRepository.findAll(pageable);
        } else {
            String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            result = userRepository.search(pattern, pageable);
        }
        List<UserView> items = result.getContent().stream().map(this::toView).toList();
        return new PageView<>(items, result.getTotalElements(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public UserView get(Long id) {
        return toView(requireUser(id));
    }

    @Transactional
    public UserView create(AdminRequests.UserCreate request, String actor, ClientInfo client) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email() == null || request.email().isBlank()
                ? null : request.email().trim().toLowerCase(Locale.ROOT);
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BizException(AuthCodes.CONFLICT, "用户名已存在");
        }
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new BizException(AuthCodes.CONFLICT, "邮箱已存在");
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new BizException(AuthCodes.CONFLICT, "手机号已存在");
        }
        passwordService.validateStrength(request.password(), username, email);

        AuthUser user = new AuthUser();
        user.setUsername(username);
        user.setDisplayName(request.displayName() == null || request.displayName().isBlank()
                ? username : request.displayName().trim());
        user.setEmail(email);
        user.setPhone(phone);
        PasswordService.PasswordHash hash = passwordService.hash(request.password());
        user.setPasswordSalt(hash.salt());
        user.setPasswordHash(hash.hash());
        user.setPasswordIterations(hash.iterations());
        user.setPasswordAlgo(hash.algorithm());
        user.setStatus(UserStatus.ACTIVE);
        user.setTokenVersion(1);
        userRepository.save(user);

        if (request.roleIds() != null) {
            assignRoles(user.getId(), request.roleIds(), actor, client);
        } else {
            auditService.record("USER_CREATE", actor, user.getId(), true, client,
                    "创建用户 " + username + "（未分配角色）");
        }
        return get(user.getId());
    }

    @Transactional
    public UserView updateStatus(Long id, UserStatus status, Long actorId, String actor, ClientInfo client) {
        if (status == null) {
            throw new BizException(AuthCodes.BAD_REQUEST, "缺少账号状态");
        }
        AuthUser user = requireUser(id);
        if (actorId != null && actorId.equals(id) && status == UserStatus.DISABLED) {
            throw new BizException(AuthCodes.CONSTRAINT_VIOLATION, "不能禁用当前登录账号");
        }
        user.setStatus(status);
        if (status == UserStatus.DISABLED) {
            user.setTokenVersion(user.getTokenVersion() + 1);
            refreshTokenRepository.revokeAllByUserId(id);
        }
        userRepository.save(user);
        auditService.record("USER_STATUS", actor, id, true, client,
                "用户 " + user.getUsername() + " 状态改为 " + status);
        return toView(user);
    }

    @Transactional
    public UserView assignRoles(Long id, List<Long> roleIds, String actor, ClientInfo client) {
        AuthUser user = requireUser(id);
        List<Long> distinct = roleIds == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(roleIds));
        List<AuthRole> roles = roleRepository.findByIdIn(distinct);
        if (roles.size() != distinct.size()) {
            throw new BizException(AuthCodes.BAD_REQUEST, "存在无效的角色 ID");
        }
        constraintService.validateAssignment(id, distinct);

        userRoleRepository.deleteByUserId(id);
        List<AuthUserRole> links = distinct.stream().map(roleId -> {
            AuthUserRole link = new AuthUserRole();
            link.setUserId(id);
            link.setRoleId(roleId);
            link.setGrantedBy(actor);
            return link;
        }).toList();
        userRoleRepository.saveAll(links);

        // 角色变化立即失效旧刷新令牌；访问令牌最长 15 分钟后自然过期。
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(id);

        auditService.record("ROLE_ASSIGN", actor, id, true, client,
                "用户 " + user.getUsername() + " 角色变更为 " + roles.stream()
                        .map(AuthRole::getCode).toList());
        return toView(user);
    }

    @Transactional
    public UserView unlock(Long id, String actor, ClientInfo client) {
        AuthUser user = requireUser(id);
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        auditService.record("USER_UNLOCK", actor, id, true, client, "解锁用户 " + user.getUsername());
        return toView(user);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword, String actor, ClientInfo client) {
        AuthUser user = requireUser(id);
        passwordService.validateStrength(newPassword, user.getUsername(), user.getEmail());
        PasswordService.PasswordHash hash = passwordService.hash(newPassword);
        user.setPasswordSalt(hash.salt());
        user.setPasswordHash(hash.hash());
        user.setPasswordIterations(hash.iterations());
        user.setPasswordAlgo(hash.algorithm());
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        refreshTokenRepository.revokeAllByUserId(id);
        auditService.record("USER_PASSWORD_RESET", actor, id, true, client,
                "管理员重置用户 " + user.getUsername() + " 密码");
    }

    public UserView toView(AuthUser user) {
        List<RoleBrief> roles = queryService.briefs(queryService.effectiveRoleIds(user.getId()));
        boolean locked = user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
                user.getPhone(), user.getStatus().name(), locked, user.getFailedAttempts(),
                user.getLastLoginAt(), user.getLastLoginIp(), user.getCreatedAt(), roles);
    }

    private AuthUser requireUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("用户"));
    }
}
