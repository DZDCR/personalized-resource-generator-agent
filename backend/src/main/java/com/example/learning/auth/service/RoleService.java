package com.example.learning.auth.service;

import com.example.learning.auth.dto.AdminRequests;
import com.example.learning.auth.dto.AuthViews.RoleNode;
import com.example.learning.auth.dto.AuthViews.RoleView;
import com.example.learning.auth.entity.AuthPermission;
import com.example.learning.auth.entity.AuthRole;
import com.example.learning.auth.entity.AuthRoleClosure;
import com.example.learning.auth.entity.AuthRolePermission;
import com.example.learning.auth.repository.AuthPermissionRepository;
import com.example.learning.auth.repository.AuthRoleClosureRepository;
import com.example.learning.auth.repository.AuthRolePermissionRepository;
import com.example.learning.auth.repository.AuthRoleRepository;
import com.example.learning.auth.repository.AuthUserRepository;
import com.example.learning.auth.repository.AuthUserRoleRepository;
import com.example.learning.auth.repository.AuthConstraintRoleRepository;
import com.example.learning.auth.security.AuthCodes;
import com.example.learning.auth.security.ClientInfo;
import com.example.learning.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 角色管理：RBAC1 角色继承树 + RBAC2 基数/前置角色 + 角色-权限授予。
 *
 * <p>角色继承使用闭包表。任何一次父角色变更都会重建闭包，并递增所有用户 tokenVersion，
 * 使已签发的刷新令牌失效，避免旧权限长期驻留。</p>
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final AuthRoleRepository roleRepository;
    private final AuthPermissionRepository permissionRepository;
    private final AuthRolePermissionRepository rolePermissionRepository;
    private final AuthRoleClosureRepository closureRepository;
    private final AuthUserRoleRepository userRoleRepository;
    private final AuthUserRepository userRepository;
    private final AuthConstraintRoleRepository constraintRoleRepository;
    private final RbacQueryService queryService;
    private final AuthAuditService auditService;

    @Transactional(readOnly = true)
    public List<RoleView> list() {
        Map<Long, AuthRole> roles = queryService.roleMap();
        Map<Long, Long> counts = queryService.memberCounts();
        return queryService.allRoles().stream()
                .map(role -> toView(role, roles, counts))
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleView get(Long id) {
        AuthRole role = requireRole(id);
        Map<Long, AuthRole> roles = queryService.roleMap();
        return toView(role, roles, queryService.memberCounts());
    }

    @Transactional(readOnly = true)
    public List<RoleNode> tree() {
        return queryService.roleTree();
    }

    @Transactional
    public RoleView create(AdminRequests.RoleUpsert request, String actor, ClientInfo client) {
        String code = request.code().trim().toUpperCase();
        if (roleRepository.existsByCode(code)) {
            throw new BizException(AuthCodes.CONFLICT, "角色编码已存在：" + code);
        }
        validateParent(null, request.parentId());
        validateRequiredRole(null, request.requiredRoleId());

        AuthRole role = new AuthRole();
        role.setCode(code);
        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setParentId(request.parentId());
        role.setMaxMembers(normalizeMaxMembers(request.maxMembers()));
        role.setRequiredRoleId(request.requiredRoleId());
        role.setBuiltIn(false);
        role.setSortOrder(request.sortOrder() == null ? 100 : request.sortOrder());
        roleRepository.save(role);
        rebuildClosure();
        bumpAllTokenVersions();
        auditService.record("ROLE_CREATE", actor, null, true, client,
                "创建角色 " + code + "，父角色=" + request.parentId());
        return get(role.getId());
    }

    @Transactional
    public RoleView update(Long id, AdminRequests.RoleUpsert request, String actor, ClientInfo client) {
        AuthRole role = requireRole(id);
        String code = request.code().trim().toUpperCase();
        if (!role.getCode().equals(code)) {
            throw new BizException(AuthCodes.BAD_REQUEST, "角色编码创建后不可修改");
        }
        validateParent(id, request.parentId());
        validateRequiredRole(id, request.requiredRoleId());
        Integer maxMembers = normalizeMaxMembers(request.maxMembers());
        if (maxMembers != null) {
            long members = userRoleRepository.countByRoleId(id);
            if (members > maxMembers) {
                throw new BizException(AuthCodes.CONSTRAINT_VIOLATION,
                        "该角色当前已有 " + members + " 名成员，超过设定的基数上限 " + maxMembers);
            }
        }
        boolean hierarchyChanged = !java.util.Objects.equals(role.getParentId(), request.parentId());

        role.setName(request.name().trim());
        role.setDescription(request.description());
        role.setParentId(request.parentId());
        role.setMaxMembers(maxMembers);
        role.setRequiredRoleId(request.requiredRoleId());
        role.setSortOrder(request.sortOrder() == null ? role.getSortOrder() : request.sortOrder());
        roleRepository.save(role);
        if (hierarchyChanged) {
            rebuildClosure();
            bumpAllTokenVersions();
        }
        auditService.record("ROLE_UPDATE", actor, null, true, client,
                "更新角色 " + code + "，父角色=" + request.parentId() + "，上限=" + maxMembers);
        return get(id);
    }

    @Transactional
    public void delete(Long id, String actor, ClientInfo client) {
        AuthRole role = requireRole(id);
        if (role.isBuiltIn()) {
            throw new BizException(AuthCodes.CONSTRAINT_VIOLATION, "内置角色不可删除");
        }
        if (roleRepository.countByParentId(id) > 0) {
            throw new BizException(AuthCodes.CONSTRAINT_VIOLATION, "请先删除或迁移子角色");
        }
        if (userRoleRepository.countByRoleId(id) > 0) {
            throw new BizException(AuthCodes.CONSTRAINT_VIOLATION, "该角色仍有成员，无法删除");
        }
        if (roleRepository.countByRequiredRoleId(id) > 0) {
            throw new BizException(AuthCodes.CONSTRAINT_VIOLATION, "该角色被其他角色设为前置角色，无法删除");
        }
        if (!constraintRoleRepository.findByRoleId(id).isEmpty()) {
            throw new BizException(AuthCodes.CONSTRAINT_VIOLATION, "该角色仍被职责分离约束引用，无法删除");
        }
        rolePermissionRepository.deleteByRoleId(id);
        roleRepository.delete(role);
        rebuildClosure();
        bumpAllTokenVersions();
        auditService.record("ROLE_DELETE", actor, null, true, client, "删除角色 " + role.getCode());
    }

    @Transactional(readOnly = true)
    public List<Long> permissionIds(Long roleId) {
        requireRole(roleId);
        return rolePermissionRepository.findPermissionIdsByRoleId(roleId);
    }

    @Transactional
    public RoleView assignPermissions(Long roleId, List<Long> permissionIds, String actor, ClientInfo client) {
        AuthRole role = requireRole(roleId);
        Set<Long> unique = new LinkedHashSet<>(permissionIds == null ? List.of() : permissionIds);
        List<AuthPermission> permissions = permissionRepository.findByIdIn(unique);
        if (permissions.size() != unique.size()) {
            throw new BizException(AuthCodes.BAD_REQUEST, "存在无效的权限 ID");
        }
        rolePermissionRepository.deleteByRoleId(roleId);
        List<AuthRolePermission> links = permissions.stream().map(permission -> {
            AuthRolePermission link = new AuthRolePermission();
            link.setRoleId(roleId);
            link.setPermissionId(permission.getId());
            return link;
        }).toList();
        rolePermissionRepository.saveAll(links);
        bumpAllTokenVersions();
        auditService.record("ROLE_PERMISSION_ASSIGN", actor, null, true, client,
                "角色 " + role.getCode() + " 权限数=" + permissions.size());
        return get(roleId);
    }

    /**
     * 重建整棵角色继承闭包。角色规模通常很小（<1000），整表重建比增量维护更不易出错。
     */
    @Transactional
    public void rebuildClosure() {
        List<AuthRole> roles = roleRepository.findAll();
        Map<Long, Long> parentById = new java.util.HashMap<>();
        for (AuthRole role : roles) {
            parentById.put(role.getId(), role.getParentId());
        }
        List<AuthRoleClosure> rows = new ArrayList<>();
        for (AuthRole role : roles) {
            // row = (role, inheritedRole)：用户拥有 role 时，沿 parentId 链继承其基线角色。
            Long current = role.getId();
            int depth = 0;
            Set<Long> seen = new HashSet<>();
            while (current != null && seen.add(current)) {
                rows.add(new AuthRoleClosure(role.getId(), current, depth++));
                current = parentById.get(current);
            }
        }
        closureRepository.deleteAllInBatch();
        closureRepository.saveAll(rows);
    }

    private void validateParent(Long roleId, Long parentId) {
        if (parentId == null) {
            return;
        }
        if (parentId.equals(roleId)) {
            throw new BizException(AuthCodes.CONSTRAINT_VIOLATION, "角色不能以自己为父角色");
        }
        AuthRole parent = roleRepository.findById(parentId)
                .orElseThrow(() -> new BizException(AuthCodes.BAD_REQUEST, "父角色不存在"));
        Long cursor = parent.getParentId();
        Set<Long> seen = new HashSet<>();
        seen.add(parent.getId());
        while (cursor != null && seen.add(cursor)) {
            if (cursor.equals(roleId)) {
                throw new BizException(AuthCodes.CONSTRAINT_VIOLATION, "角色继承不能成环");
            }
            cursor = roleRepository.findById(cursor).map(AuthRole::getParentId).orElse(null);
        }
    }

    private void validateRequiredRole(Long roleId, Long requiredRoleId) {
        if (requiredRoleId == null) {
            return;
        }
        if (requiredRoleId.equals(roleId)) {
            throw new BizException(AuthCodes.CONSTRAINT_VIOLATION, "角色不能以自己为前置角色");
        }
        if (!roleRepository.existsById(requiredRoleId)) {
            throw new BizException(AuthCodes.BAD_REQUEST, "前置角色不存在");
        }
    }

    private Integer normalizeMaxMembers(Integer maxMembers) {
        if (maxMembers == null) {
            return null;
        }
        if (maxMembers < 1) {
            throw new BizException(AuthCodes.BAD_REQUEST, "角色成员上限必须大于 0");
        }
        return maxMembers;
    }

    private AuthRole requireRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("角色"));
    }

    private void bumpAllTokenVersions() {
        userRepository.bumpAllTokenVersions();
    }

    private RoleView toView(AuthRole role, Map<Long, AuthRole> roles, Map<Long, Long> counts) {
        AuthRole parent = role.getParentId() == null ? null : roles.get(role.getParentId());
        AuthRole required = role.getRequiredRoleId() == null ? null : roles.get(role.getRequiredRoleId());
        return new RoleView(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getDescription(),
                role.getParentId(),
                parent == null ? null : parent.getName(),
                role.getMaxMembers(),
                counts.getOrDefault(role.getId(), 0L),
                role.getRequiredRoleId(),
                required == null ? null : required.getName(),
                role.isBuiltIn(),
                role.getSortOrder(),
                rolePermissionRepository.findPermissionIdsByRoleId(role.getId()));
    }
}
