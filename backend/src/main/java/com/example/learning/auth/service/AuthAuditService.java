package com.example.learning.auth.service;

import com.example.learning.auth.dto.AuthViews.AuditView;
import com.example.learning.auth.dto.AuthViews.PageView;
import com.example.learning.auth.entity.AuthAuditLog;
import com.example.learning.auth.entity.AuthUser;
import com.example.learning.auth.repository.AuthAuditLogRepository;
import com.example.learning.auth.security.ClientInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 安全审计：登录成功/失败、令牌刷新、角色与约束变更、口令重置等。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthAuditService {

    private final AuthAuditLogRepository auditLogRepository;

    @Transactional
    public void record(String eventType, AuthUser user, boolean success, ClientInfo client, String detail) {
        record(eventType,
                user == null ? null : user.getUsername(),
                user == null ? null : user.getId(),
                success, client, detail);
    }

    @Transactional
    public void record(String eventType, String username, Long userId, boolean success,
                       ClientInfo client, String detail) {
        try {
            AuthAuditLog entry = new AuthAuditLog();
            entry.setEventType(eventType);
            entry.setUsername(trim(username, 64));
            entry.setUserId(userId);
            entry.setSuccess(success);
            entry.setIp(client == null ? "unknown" : client.ip());
            entry.setUserAgent(client == null ? "unknown" : client.userAgent());
            entry.setDetail(trim(detail, 1000));
            auditLogRepository.save(entry);
        } catch (Exception e) {
            // 审计失败不能阻断业务，但必须留下服务端日志。
            log.error("write audit log failed: event={}, user={}", eventType, username, e);
        }
    }

    @Transactional(readOnly = true)
    public PageView<AuditView> search(String eventType, String username, Boolean success,
                                      Instant from, Instant to, int page, int size) {
        Specification<AuthAuditLog> spec = Specification.where(null);
        if (eventType != null && !eventType.isBlank()) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("eventType"), eventType.trim()));
        }
        if (username != null && !username.isBlank()) {
            String pattern = "%" + username.trim().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.like(cb.lower(root.get("username")), pattern));
        }
        if (success != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("success"), success));
        }
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        if (to != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to));
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 200);
        Page<AuthAuditLog> result = auditLogRepository.findAll(spec,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "id")));
        List<AuditView> items = result.getContent().stream().map(this::toView).toList();
        return new PageView<>(items, result.getTotalElements(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public long count(boolean success, Instant from) {
        Specification<AuthAuditLog> spec = Specification.where(null);
        spec = spec.and((root, query, cb) -> cb.equal(root.get("success"), success));
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        return auditLogRepository.count(spec);
    }

    @Transactional(readOnly = true)
    public long countAll(Instant from) {
        Specification<AuthAuditLog> spec = Specification.where(null);
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        return auditLogRepository.count(spec);
    }

    /** 仅统计登录类事件（eventType 以 LOGIN 开头）。 */
    @Transactional(readOnly = true)
    public long countLogins(boolean success, Instant from) {
        Specification<AuthAuditLog> spec = Specification.where(null);
        spec = spec.and((root, query, cb) -> cb.equal(root.get("success"), success));
        spec = spec.and((root, query, cb) -> cb.like(root.get("eventType"), "LOGIN%"));
        if (from != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from));
        }
        return auditLogRepository.count(spec);
    }

    public List<String> eventTypes() {
        return new ArrayList<>(List.of(
                "LOGIN_PASSWORD_SUCCESS", "LOGIN_PASSWORD_FAIL", "LOGIN_OTP_SUCCESS", "LOGIN_OTP_FAIL",
                "LOGIN_SSO_SUCCESS",
                "LOGIN_LOCKED", "LOGOUT", "TOKEN_REFRESH", "REGISTER", "PASSWORD_RESET", "PASSWORD_CHANGE",
                "ROLE_ASSIGN", "ROLE_CREATE", "ROLE_UPDATE", "ROLE_DELETE", "ROLE_PERMISSION_ASSIGN",
                "PERMISSION_CREATE", "PERMISSION_UPDATE", "PERMISSION_DELETE",
                "CONSTRAINT_CREATE", "CONSTRAINT_UPDATE", "CONSTRAINT_DELETE",
                "USER_CREATE", "USER_STATUS", "USER_UNLOCK", "USER_PASSWORD_RESET", "ROLE_SWITCH"));
    }

    private AuditView toView(AuthAuditLog log) {
        return new AuditView(log.getId(), log.getEventType(), log.getUsername(), log.getUserId(),
                log.isSuccess(), log.getIp(), log.getUserAgent(), log.getDetail(), log.getCreatedAt());
    }

    private String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public static Instant startOfToday() {
        return LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
}
