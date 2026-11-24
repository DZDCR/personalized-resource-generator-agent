package com.example.learning.auth.service;

import com.example.learning.auth.config.AuthProperties;
import com.example.learning.auth.dto.AuthRequests;
import com.example.learning.auth.dto.AuthViews.MeView;
import com.example.learning.auth.dto.AuthViews.RoleBrief;
import com.example.learning.auth.dto.AuthViews.RoleChoiceView;
import com.example.learning.auth.dto.AuthViews.SendCodeView;
import com.example.learning.auth.dto.AuthViews.TokenResponse;
import com.example.learning.auth.dto.AuthViews.UserView;
import com.example.learning.auth.entity.AuthRefreshToken;
import com.example.learning.auth.entity.AuthRole;
import com.example.learning.auth.entity.AuthUser;
import com.example.learning.auth.entity.AuthUserRole;
import com.example.learning.auth.entity.UserStatus;
import com.example.learning.auth.repository.AuthRefreshTokenRepository;
import com.example.learning.auth.repository.AuthRoleRepository;
import com.example.learning.auth.repository.AuthUserRepository;
import com.example.learning.auth.repository.AuthUserRoleRepository;
import com.example.learning.auth.security.AuthCodes;
import com.example.learning.auth.security.AuthUserDetailsService;
import com.example.learning.auth.security.ClientInfo;
import com.example.learning.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证主流程（图片 5 条要求的落点）：
 * <ol>
 *   <li>账号密码：验证码 → Spring Security AuthenticationManager → 取盐+慢哈希比对 → JWT+刷新令牌；失败统一提示；</li>
 *   <li>邮箱/短信验证码：HMAC 摘要、5 分钟、一次有效；</li>
 *   <li>统一身份认证：见 SsoService（OIDC 授权码 + PKCE 演示）；</li>
 *   <li>注册：图形验证码 + 邮箱验证码 + 口令强度；</li>
 *   <li>找回密码：统一响应防枚举 + 一次性验证码 + 重置后吊销全部会话。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final AuthUserRepository userRepository;
    private final AuthRoleRepository roleRepository;
    private final AuthUserRoleRepository userRoleRepository;
    private final AuthRefreshTokenRepository refreshTokenRepository;
    private final AuthUserDetailsService userDetailsService;
    private final PasswordService passwordService;
    private final TokenService tokenService;
    private final CaptchaService captchaService;
    private final VerificationCodeService codeService;
    private final RbacQueryService queryService;
    private final ConstraintService constraintService;
    private final AuthAuditService auditService;
    private final AuthProperties properties;

    // ------------------------------------------------------------------
    // 登录
    // ------------------------------------------------------------------

    @Transactional
    public IssuedSession loginWithPassword(AuthRequests.PasswordLogin request, ClientInfo client) {
        // 先校验但不销毁：密码校验失败时销毁；DSD 角色选择重试时保留，避免重复识别验证码。
        captchaService.verify(request.captchaId(), request.captchaCode(), false);
        AuthUser known = userDetailsService.byAccount(request.account()).orElse(null);
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(
                    request.account().trim(), request.password()));
        } catch (LockedException e) {
            captchaService.consume(request.captchaId());
            auditService.record("LOGIN_LOCKED", known, false, client, "账号锁定中");
            throw new BizException(AuthCodes.ACCOUNT_LOCKED,
                    "账号已临时锁定，请 " + properties.getLock().getLockMinutes() + " 分钟后再试");
        } catch (DisabledException e) {
            captchaService.consume(request.captchaId());
            auditService.record("LOGIN_PASSWORD_FAIL", known, false, client, "账号已禁用");
            throw new BizException(AuthCodes.ACCOUNT_DISABLED, "账号已被禁用，请联系管理员");
        } catch (BadCredentialsException e) {
            registerFailure(known, client, "密码错误或账号不存在");
            captchaService.consume(request.captchaId());
            throw new BizException(AuthCodes.INVALID_CREDENTIALS, "账号或密码错误");
        } catch (AuthenticationException e) {
            registerFailure(known, client, "认证失败：" + e.getClass().getSimpleName());
            captchaService.consume(request.captchaId());
            throw new BizException(AuthCodes.INVALID_CREDENTIALS, "账号或密码错误");
        }

        AuthUser user = userRepository.findById(known == null ? -1L : known.getId())
                .orElseThrow(() -> {
                    captchaService.consume(request.captchaId());
                    return new BizException(AuthCodes.INVALID_CREDENTIALS, "账号或密码错误");
                });
        if (user.getStatus() != UserStatus.ACTIVE) {
            captchaService.consume(request.captchaId());
            auditService.record("LOGIN_PASSWORD_FAIL", user, false, client, "账号已禁用");
            throw new BizException(AuthCodes.ACCOUNT_DISABLED, "账号已被禁用，请联系管理员");
        }
        resetFailure(user);
        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(client.ip());
        userRepository.save(user);
        try {
            IssuedSession session = issueSessionForUser(user, request.activeRoleCodes(), client,
                    "LOGIN_PASSWORD_SUCCESS", "账号密码登录");
            captchaService.consume(request.captchaId());
            return session;
        } catch (BizException e) {
            // 仅 DSD 需要用户选择角色；保留验证码供同一会话重试。
            if (e.getCode() != AuthCodes.ROLE_CHOICE_REQUIRED) {
                captchaService.consume(request.captchaId());
            }
            throw e;
        }
    }

    @Transactional
    public IssuedSession loginWithOtp(AuthRequests.OtpLogin request, ClientInfo client) {
        String channel = VerificationCodeService.normalizeChannel(request.channel());
        String target = VerificationCodeService.normalizeTarget(channel, request.target());
        AuthUser user = findUserByTarget(channel, target).orElse(null);
        if (user == null) {
            auditService.record("LOGIN_OTP_FAIL", null, false, client,
                    "验证码登录失败：账号不存在 target=" + VerificationCodeService.mask(target));
            throw new BizException(AuthCodes.INVALID_CREDENTIALS, "账号或验证码错误");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            auditService.record("LOGIN_OTP_FAIL", user, false, client, "账号已禁用");
            throw new BizException(AuthCodes.ACCOUNT_DISABLED, "账号已被禁用，请联系管理员");
        }
        if (isLocked(user)) {
            auditService.record("LOGIN_LOCKED", user, false, client, "验证码登录：账号锁定中");
            throw new BizException(AuthCodes.ACCOUNT_LOCKED, "账号已临时锁定，请稍后再试");
        }
        // 先校验激活角色（可能触发 DSD 选择），通过后再消费一次性验证码，避免用户重新获取验证码。
        resolveActiveRoles(user, request.activeRoleCodes());
        codeService.verify(VerificationCodeService.SCENE_LOGIN, channel, target, request.code());
        resetFailure(user);
        user.setLastLoginAt(Instant.now());
        user.setLastLoginIp(client.ip());
        userRepository.save(user);
        return issueSessionForUser(user, request.activeRoleCodes(), client,
                "LOGIN_OTP_SUCCESS", channel + " 验证码登录");
    }

    // ------------------------------------------------------------------
    // 注册 / 找回密码
    // ------------------------------------------------------------------

    @Transactional
    public IssuedSession register(AuthRequests.Register request, ClientInfo client) {
        captchaService.verify(request.captchaId(), request.captchaCode());
        if (!request.password().equals(request.confirmPassword())) {
            throw new BizException(AuthCodes.BAD_REQUEST, "两次输入的密码不一致");
        }
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
        passwordService.validateStrength(request.password(), username, email);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BizException(AuthCodes.CONFLICT, "用户名已被占用");
        }
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new BizException(AuthCodes.CONFLICT, "邮箱已被注册");
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw new BizException(AuthCodes.CONFLICT, "手机号已被注册");
        }
        codeService.verify(VerificationCodeService.SCENE_REGISTER,
                VerificationCodeService.CHANNEL_EMAIL, email, request.emailCode());

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

        AuthRole studentRole = roleRepository.findByCode("ROLE_STUDENT")
                .orElseThrow(() -> new BizException(AuthCodes.SERVER_ERROR, "系统未初始化学生角色"));
        AuthUserRole relation = new AuthUserRole();
        relation.setUserId(user.getId());
        relation.setRoleId(studentRole.getId());
        relation.setGrantedBy("SELF_REGISTER");
        userRoleRepository.save(relation);

        auditService.record("REGISTER", user, true, client, "自助注册，默认角色 " + studentRole.getCode());
        return issueSessionForUser(user, List.of(studentRole.getCode()), client,
                "REGISTER", "注册后自动登录");
    }

    @Transactional
    public SendCodeView sendOtp(AuthRequests.SendCode request, ClientInfo client) {
        String scene = VerificationCodeService.normalizeScene(request.scene());
        String channel = VerificationCodeService.normalizeChannel(request.channel());
        String target = VerificationCodeService.normalizeTarget(channel, request.target());
        if (VerificationCodeService.SCENE_LOGIN.equals(scene)
                || VerificationCodeService.SCENE_FORGOT.equals(scene)) {
            boolean exists = findUserByTarget(channel, target).isPresent();
            if (!exists) {
                return new SendCodeView(true, genericCodeMessage(), null, ttl());
            }
            try {
                SendCodeView sent = codeService.send(scene, channel, target);
                return new SendCodeView(true, genericCodeMessage(), sent.debugCode(), sent.expiresIn());
            } catch (BizException e) {
                if (e.getCode() == AuthCodes.RATE_LIMITED) {
                    return new SendCodeView(true, genericCodeMessage(), null, ttl());
                }
                throw e;
            }
        }
        return codeService.send(scene, channel, target);
    }

    @Transactional
    public SendCodeView forgot(AuthRequests.Forgot request, ClientInfo client) {
        captchaService.verify(request.captchaId(), request.captchaCode());
        AuthUser user = userDetailsService.byAccount(request.account()).orElse(null);
        String debugCode = null;
        if (user != null) {
            ChannelTarget channelTarget = resolveChannel(user, request.account());
            if (channelTarget != null) {
                try {
                    SendCodeView sent = codeService.send(VerificationCodeService.SCENE_FORGOT,
                            channelTarget.channel(), channelTarget.target());
                    debugCode = sent.debugCode();
                    auditService.record("PASSWORD_RESET", user, true, client,
                            "发送找回密码验证码至 " + VerificationCodeService.mask(channelTarget.target()));
                } catch (BizException e) {
                    log.info("forgot password send skipped: {}", e.getMessage());
                }
            }
        }
        // 无论账号是否存在都返回同一提示，避免账号枚举。
        return new SendCodeView(true,
                "如果该账号存在，我们已向绑定的邮箱/手机号发送验证码，5 分钟内有效。",
                properties.isDevMode() ? debugCode : null, ttl());
    }

    @Transactional
    public void resetPassword(AuthRequests.Reset request, ClientInfo client) {
        AuthUser user = userDetailsService.byAccount(request.account())
                .orElseThrow(() -> new BizException(AuthCodes.OTP_INVALID, "验证码错误或已过期"));
        ChannelTarget channelTarget = resolveChannel(user, request.account());
        if (channelTarget == null) {
            throw new BizException(AuthCodes.OTP_INVALID, "验证码错误或已过期");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BizException(AuthCodes.BAD_REQUEST, "两次输入的新密码不一致");
        }
        passwordService.validateStrength(request.newPassword(), user.getUsername(), user.getEmail());
        codeService.verify(VerificationCodeService.SCENE_FORGOT,
                channelTarget.channel(), channelTarget.target(), request.code());
        if (passwordService.matches(request.newPassword(), user.getPasswordSalt(),
                user.getPasswordHash(), user.getPasswordIterations())) {
            throw new BizException(AuthCodes.BAD_REQUEST, "新密码不能与原密码相同");
        }
        applyNewPassword(user, request.newPassword());
        refreshTokenRepository.revokeAllByUserId(user.getId());
        auditService.record("PASSWORD_RESET", user, true, client, "通过验证码重置密码，吊销全部刷新令牌");
    }

    @Transactional
    public void changePassword(Long userId, AuthRequests.ChangePassword request, ClientInfo client) {
        AuthUser user = requireUser(userId);
        if (!passwordService.matches(request.oldPassword(), user.getPasswordSalt(),
                user.getPasswordHash(), user.getPasswordIterations())) {
            auditService.record("PASSWORD_CHANGE", user, false, client, "原密码错误");
            throw new BizException(AuthCodes.INVALID_CREDENTIALS, "原密码错误");
        }
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BizException(AuthCodes.BAD_REQUEST, "两次输入的新密码不一致");
        }
        passwordService.validateStrength(request.newPassword(), user.getUsername(), user.getEmail());
        if (passwordService.matches(request.newPassword(), user.getPasswordSalt(),
                user.getPasswordHash(), user.getPasswordIterations())) {
            throw new BizException(AuthCodes.BAD_REQUEST, "新密码不能与原密码相同");
        }
        applyNewPassword(user, request.newPassword());
        refreshTokenRepository.revokeAllByUserId(user.getId());
        auditService.record("PASSWORD_CHANGE", user, true, client, "修改密码，吊销全部刷新令牌");
    }

    // ------------------------------------------------------------------
    // 会话
    // ------------------------------------------------------------------

    @Transactional
    public IssuedSession refresh(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new BizException(AuthCodes.TOKEN_INVALID, "登录状态已失效，请重新登录");
        }
        String hash = tokenService.hashRefreshToken(rawRefreshToken);
        AuthRefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new BizException(AuthCodes.TOKEN_INVALID, "登录状态已失效，请重新登录"));
        if (stored.isRevoked() || stored.getExpiresAt().isBefore(Instant.now())) {
            throw new BizException(AuthCodes.TOKEN_INVALID, "登录状态已失效，请重新登录");
        }
        AuthUser user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new BizException(AuthCodes.TOKEN_INVALID, "登录状态已失效，请重新登录"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BizException(AuthCodes.ACCOUNT_DISABLED, "账号已被禁用，请联系管理员");
        }
        if (user.getTokenVersion() != stored.getTokenVersion()) {
            stored.setRevoked(true);
            refreshTokenRepository.save(stored);
            throw new BizException(AuthCodes.TOKEN_INVALID, "登录状态已失效，请重新登录");
        }
        List<String> activeCodes = parseActiveCodes(stored.getActiveRoleCodes());
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);
        return issueSessionForUser(user, activeCodes, client, "TOKEN_REFRESH", "刷新访问令牌并轮换");
    }

    @Transactional
    public void logout(String rawRefreshToken, ClientInfo client) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(tokenService.hashRefreshToken(rawRefreshToken))
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    AuthUser user = userRepository.findById(token.getUserId()).orElse(null);
                    auditService.record("LOGOUT", user, true, client, "注销当前会话");
                });
    }

    @Transactional
    public IssuedSession switchRoles(Long userId, AuthRequests.SwitchRoles request, ClientInfo client) {
        AuthUser user = requireUser(userId);
        // 切换角色视为新会话：吊销旧刷新令牌，避免旧角色选择被重放。
        refreshTokenRepository.revokeAllByUserId(userId);
        return issueSessionForUser(user, request.roleCodes(), client,
                "ROLE_SWITCH", "切换激活角色为 " + request.roleCodes());
    }

    @Transactional(readOnly = true)
    public MeView me(Long userId, List<String> activeRoleCodes) {
        AuthUser user = requireUser(userId);
        List<Long> effective = queryService.effectiveRoleIds(userId);
        Set<Long> effectiveSet = new LinkedHashSet<>(effective);
        List<AuthRole> active = new ArrayList<>();
        if (activeRoleCodes != null && !activeRoleCodes.isEmpty()) {
            for (AuthRole role : queryService.rolesByCodes(activeRoleCodes)) {
                if (effectiveSet.contains(role.getId())) {
                    active.add(role);
                }
            }
        }
        if (active.isEmpty()) {
            active = queryService.rolesByIds(effective);
        }
        List<String> activeCodes = active.stream().map(AuthRole::getCode).toList();
        List<String> permissions = queryService.permissionCodesForRoleIds(
                active.stream().map(AuthRole::getId).toList());
        List<RoleBrief> assignedRoles = queryService.briefs(effective);
        return new MeView(toView(user, assignedRoles), assignedRoles, activeCodes, permissions);
    }

    /**
     * 供 SSO 登录复用：校验激活角色 + DSD 后签发访问/刷新令牌。
     */
    @Transactional
    public IssuedSession issueSessionForUser(AuthUser user, List<String> requestedRoleCodes,
                                             ClientInfo client, String auditEvent, String detail) {
        List<AuthRole> activeRoles = resolveActiveRoles(user, requestedRoleCodes);
        List<String> activeCodes = activeRoles.stream().map(AuthRole::getCode).toList();
        List<String> permissions = queryService.permissionCodesForRoleIds(
                activeRoles.stream().map(AuthRole::getId).toList());
        TokenService.IssuedToken access = tokenService.issueAccessToken(user, activeCodes, permissions);

        String rawRefresh = tokenService.newRefreshToken();
        AuthRefreshToken refresh = new AuthRefreshToken();
        refresh.setTokenHash(tokenService.hashRefreshToken(rawRefresh));
        refresh.setUserId(user.getId());
        refresh.setActiveRoleCodes(String.join(",", activeCodes));
        refresh.setTokenVersion(user.getTokenVersion());
        refresh.setExpiresAt(Instant.now().plusSeconds(tokenService.refreshTtlSeconds()));
        refresh.setRevoked(false);
        refresh.setIp(client.ip());
        refresh.setUserAgent(client.userAgent());
        refreshTokenRepository.save(refresh);

        List<RoleBrief> assignedRoles = queryService.briefs(queryService.effectiveRoleIds(user.getId()));
        TokenResponse view = new TokenResponse(access.token(), "Bearer", access.expiresIn(),
                toView(user, assignedRoles), assignedRoles, activeCodes, permissions);
        auditService.record(auditEvent, user, true, client,
                detail + "；激活角色=" + activeCodes);
        return new IssuedSession(view, rawRefresh, tokenService.refreshTtlSeconds());
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private List<AuthRole> resolveActiveRoles(AuthUser user, List<String> requestedRoleCodes) {
        List<Long> effective = queryService.effectiveRoleIds(user.getId());
        if (effective.isEmpty()) {
            throw new BizException(AuthCodes.FORBIDDEN, "账号未分配角色，请联系管理员");
        }
        Set<Long> effectiveSet = new LinkedHashSet<>(effective);
        List<AuthRole> active;
        if (requestedRoleCodes == null || requestedRoleCodes.isEmpty()) {
            active = queryService.rolesByIds(effective);
        } else {
            active = queryService.rolesByCodes(requestedRoleCodes);
            for (AuthRole role : active) {
                if (!effectiveSet.contains(role.getId())) {
                    throw new BizException(AuthCodes.FORBIDDEN, "无权激活角色：" + role.getCode());
                }
            }
        }
        if (active.isEmpty()) {
            throw new BizException(AuthCodes.FORBIDDEN, "至少需要激活一个角色");
        }
        RoleChoiceView conflict = constraintService.checkDynamic(user.getId(),
                active.stream().map(AuthRole::getId).toList());
        if (conflict != null) {
            throw new BizException(AuthCodes.ROLE_CHOICE_REQUIRED,
                    "存在动态职责分离冲突，请选择本次会话要激活的角色", conflict);
        }
        return active;
    }

    private void registerFailure(AuthUser user, ClientInfo client, String reason) {
        if (user != null) {
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= properties.getLock().getMaxFailedAttempts()) {
                user.setLockedUntil(Instant.now().plusSeconds(properties.getLock().getLockMinutes() * 60));
                reason = reason + "；连续失败 " + attempts + " 次，已临时锁定";
            }
            userRepository.save(user);
        }
        auditService.record("LOGIN_PASSWORD_FAIL", user, false, client, reason);
    }

    private void resetFailure(AuthUser user) {
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
    }

    private void applyNewPassword(AuthUser user, String rawPassword) {
        PasswordService.PasswordHash hash = passwordService.hash(rawPassword);
        user.setPasswordSalt(hash.salt());
        user.setPasswordHash(hash.hash());
        user.setPasswordIterations(hash.iterations());
        user.setPasswordAlgo(hash.algorithm());
        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
    }

    private AuthUser requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BizException(AuthCodes.UNAUTHORIZED, "用户不存在"));
    }

    private boolean isLocked(AuthUser user) {
        return user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now());
    }

    private java.util.Optional<AuthUser> findUserByTarget(String channel, String target) {
        return VerificationCodeService.CHANNEL_EMAIL.equals(channel)
                ? userRepository.findByEmailIgnoreCase(target)
                : userRepository.findByPhone(target);
    }

    private ChannelTarget resolveChannel(AuthUser user, String account) {
        String normalized = account == null ? "" : account.trim();
        if (normalized.contains("@") && user.getEmail() != null) {
            return new ChannelTarget(VerificationCodeService.CHANNEL_EMAIL, user.getEmail());
        }
        String digits = normalized.replaceAll("[^0-9]", "");
        if (digits.matches("^1[3-9]\\d{9}$") && user.getPhone() != null) {
            return new ChannelTarget(VerificationCodeService.CHANNEL_SMS, user.getPhone());
        }
        if (user.getEmail() != null) {
            return new ChannelTarget(VerificationCodeService.CHANNEL_EMAIL, user.getEmail());
        }
        if (user.getPhone() != null) {
            return new ChannelTarget(VerificationCodeService.CHANNEL_SMS, user.getPhone());
        }
        return null;
    }

    private List<String> parseActiveCodes(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private String genericCodeMessage() {
        return "如果账号存在，验证码已发送，请查看绑定的邮箱/手机号（5 分钟内有效）。";
    }

    private int ttl() {
        return (int) properties.getOtp().getTtlSeconds();
    }

    private UserView toView(AuthUser user, List<RoleBrief> roles) {
        return new UserView(user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(),
                user.getPhone(), user.getStatus().name(), isLocked(user), user.getFailedAttempts(),
                user.getLastLoginAt(), user.getLastLoginIp(), user.getCreatedAt(), roles);
    }

    private record ChannelTarget(String channel, String target) {
    }

    /**
     * 一次登录/刷新的结果：响应体 + 需要写入 HttpOnly Cookie 的刷新令牌。
     */
    public record IssuedSession(TokenResponse view, String refreshToken, long refreshTtlSeconds) {
    }
}
