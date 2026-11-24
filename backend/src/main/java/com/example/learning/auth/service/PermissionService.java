package com.example.learning.auth.service;

import com.example.learning.auth.dto.AdminRequests;
import com.example.learning.auth.dto.AuthViews.PermissionView;
import com.example.learning.auth.entity.AuthPermission;
import com.example.learning.auth.entity.PermissionType;
import com.example.learning.auth.repository.AuthPermissionRepository;
import com.example.learning.auth.repository.AuthRolePermissionRepository;
import com.example.learning.auth.security.AuthCodes;
import com.example.learning.auth.security.ClientInfo;
import com.example.learning.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限点管理。权限编码即 Spring Security Authority。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private final AuthPermissionRepository permissionRepository;
    private final AuthRolePermissionRepository rolePermissionRepository;
    private final AuthAuditService auditService;

    @Transactional(readOnly = true)
    public List<PermissionView> list() {
        return tree();
    }

    @Transactional(readOnly = true)
    public List<PermissionView> tree() {
        List<AuthPermission> permissions = permissionRepository.findAllByOrderBySortOrderAscIdAsc();
        Map<Long, List<AuthPermission>> children = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        permissions.forEach(permission -> ids.add(permission.getId()));
        List<AuthPermission> roots = new ArrayList<>();
        for (AuthPermission permission : permissions) {
            if (permission.getParentId() == null || !ids.contains(permission.getParentId())) {
                roots.add(permission);
            } else {
                children.computeIfAbsent(permission.getParentId(), key -> new ArrayList<>()).add(permission);
            }
        }
        return roots.stream()
                .sorted(comparator())
                .map(permission -> toView(permission, children, new HashSet<>()))
                .toList();
    }

    @Transactional
    public PermissionView create(AdminRequests.PermissionUpsert request, String actor, ClientInfo client) {
        String code = request.code().trim();
        if (permissionRepository.existsByCode(code)) {
            throw new BizException(AuthCodes.CONFLICT, "权限编码已存在：" + code);
        }
        validateParent(null, request.parentId());
        AuthPermission permission = new AuthPermission();
        permission.setCode(code);
        permission.setName(request.name().trim());
        permission.setType(request.type() == null ? PermissionType.API : request.type());
        permission.setParentId(request.parentId());
        permission.setDescription(request.description());
        permission.setSortOrder(request.sortOrder() == null ? 100 : request.sortOrder());
        permission.setBuiltIn(false);
        permissionRepository.save(permission);
        auditService.record("PERMISSION_CREATE", actor, null, true, client, "创建权限 " + code);
        return findView(permission.getId());
    }

    @Transactional
    public PermissionView update(Long id, AdminRequests.PermissionUpsert request, String actor, ClientInfo client) {
        AuthPermission permission = require(id);
        String code = request.code().trim();
        if (!permission.getCode().equals(code)) {
            throw new BizException(AuthCodes.BAD_REQUEST, "权限编码创建后不可修改");
        }
        validateParent(id, request.parentId());
        permission.setName(request.name().trim());
        permission.setType(request.type() == null ? permission.getType() : request.type());
        permission.setParentId(request.parentId());
        permission.setDescription(request.description());
        permission.setSortOrder(request.sortOrder() == null ? permission.getSortOrder() : request.sortOrder());
        permissionRepository.save(permission);
        auditService.record("PERMISSION_UPDATE", actor, null, true, client, "更新权限 " + code);
        return findView(id);
    }

    @Transactional
    public void delete(Long id, String actor, ClientInfo client) {
        AuthPermission permission = require(id);
        if (permission.isBuiltIn()) {
            throw new BizException(AuthCodes.CONSTRAINT_VIOLATION, "内置权限不可删除");
        }
        if (permissionRepository.countByParentId(id) > 0) {
            throw new BizException(AuthCodes.CONSTRAINT_VIOLATION, "请先删除子权限");
        }
        if (rolePermissionRepository.countByPermissionId(id) > 0) {
            throw new BizException(AuthCodes.CONSTRAINT_VIOLATION, "该权限仍被角色引用，请先解除授权");
        }
        permissionRepository.delete(permission);
        auditService.record("PERMISSION_DELETE", actor, null, true, client, "删除权限 " + permission.getCode());
    }

    @Transactional(readOnly = true)
    public List<AuthPermission> allByIds(List<Long> ids) {
        return permissionRepository.findByIdIn(ids);
    }

    private PermissionView findView(Long id) {
        AuthPermission permission = require(id);
        return new PermissionView(permission.getId(), permission.getCode(), permission.getName(),
                permission.getType().name(), permission.getParentId(), permission.getDescription(),
                permission.isBuiltIn(), permission.getSortOrder(), List.of());
    }

    private void validateParent(Long id, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(id)) {
            throw new BizException(AuthCodes.BAD_REQUEST, "权限不能以自己为父节点");
        }
        AuthPermission parent = require(parentId);
        Long cursor = parent.getParentId();
        Set<Long> seen = new HashSet<>();
        seen.add(parent.getId());
        while (cursor != null && seen.add(cursor)) {
            if (cursor.equals(id)) {
                throw new BizException(AuthCodes.BAD_REQUEST, "权限树不能成环");
            }
            cursor = permissionRepository.findById(cursor).map(AuthPermission::getParentId).orElse(null);
        }
    }

    private AuthPermission require(Long id) {
        return permissionRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("权限"));
    }

    private PermissionView toView(AuthPermission permission, Map<Long, List<AuthPermission>> children, Set<Long> path) {
        path.add(permission.getId());
        List<PermissionView> childViews = children.getOrDefault(permission.getId(), List.of()).stream()
                .sorted(comparator())
                .filter(child -> !path.contains(child.getId()))
                .map(child -> toView(child, children, new HashSet<>(path)))
                .toList();
        return new PermissionView(permission.getId(), permission.getCode(), permission.getName(),
                permission.getType().name(), permission.getParentId(), permission.getDescription(),
                permission.isBuiltIn(), permission.getSortOrder(), childViews);
    }

    private Comparator<AuthPermission> comparator() {
        return Comparator.comparingInt(AuthPermission::getSortOrder).thenComparing(AuthPermission::getId);
    }
}
