package com.example.learning.auth.service;

import com.example.learning.auth.dto.AuthViews.RoleBrief;
import com.example.learning.auth.dto.AuthViews.RoleNode;
import com.example.learning.auth.entity.AuthPermission;
import com.example.learning.auth.entity.AuthRole;
import com.example.learning.auth.entity.AuthUserRole;
import com.example.learning.auth.repository.AuthPermissionRepository;
import com.example.learning.auth.repository.AuthRoleClosureRepository;
import com.example.learning.auth.repository.AuthRolePermissionRepository;
import com.example.learning.auth.repository.AuthRoleRepository;
import com.example.learning.auth.repository.AuthUserRoleRepository;
import com.example.learning.auth.security.AuthCodes;
import com.example.learning.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RBAC 只读查询服务：角色继承展开、有效权限计算、角色树构建。
 * 所有授权判定都从这里取数，避免多处实现导致权限漂移。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RbacQueryService {

    private final AuthRoleRepository roleRepository;
    private final AuthPermissionRepository permissionRepository;
    private final AuthUserRoleRepository userRoleRepository;
    private final AuthRolePermissionRepository rolePermissionRepository;
    private final AuthRoleClosureRepository closureRepository;

    public List<AuthRole> allRoles() {
        return roleRepository.findAllByOrderBySortOrderAscIdAsc();
    }

    public Map<Long, AuthRole> roleMap() {
        return allRoles().stream().collect(Collectors.toMap(AuthRole::getId, Function.identity()));
    }

    /** 用户直接角色 + 继承得到的后代角色。 */
    public List<Long> effectiveRoleIds(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return closureRepository.findEffectiveRoleIds(userId);
    }

    /** 给定直接角色集合，展开继承闭包后的角色 ID。 */
    public Set<Long> effectiveRoleIdsForDirect(Collection<Long> directRoleIds) {
        Set<Long> result = new LinkedHashSet<>();
        if (directRoleIds == null || directRoleIds.isEmpty()) {
            return result;
        }
        result.addAll(directRoleIds);
        result.addAll(closureRepository.findDescendantIds(directRoleIds));
        return result;
    }

    public List<AuthRole> effectiveRoles(Long userId) {
        return rolesByIds(effectiveRoleIds(userId));
    }

    public List<AuthRole> rolesByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, AuthRole> map = roleMap();
        return ids.stream().map(map::get).filter(Objects::nonNull)
                .sorted(roleComparator())
                .toList();
    }

    /** 用户（所有直接角色）的有效权限编码。 */
    public List<String> permissionCodes(Long userId) {
        return permissionCodesForRoleIds(effectiveRoleIds(userId));
    }

    /** 指定角色集合的有效权限编码（自动展开角色继承）。 */
    public List<String> permissionCodesForRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        Set<Long> effective = new LinkedHashSet<>(roleIds);
        effective.addAll(closureRepository.findDescendantIds(roleIds));
        List<Long> permissionIds = rolePermissionRepository.findPermissionIdsByRoleIds(effective);
        if (permissionIds.isEmpty()) {
            return List.of();
        }
        return permissionRepository.findByIdIn(permissionIds).stream()
                .map(AuthPermission::getCode)
                .distinct()
                .sorted()
                .toList();
    }

    public Set<String> authoritySet(Collection<Long> activeRoleIds) {
        return new HashSet<>(permissionCodesForRoleIds(activeRoleIds));
    }

    public boolean hasPermission(Long userId, String permissionCode) {
        return permissionCodes(userId).contains(permissionCode);
    }

    public List<RoleBrief> briefs(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        Map<Long, AuthRole> map = roleMap();
        return roleIds.stream().map(map::get).filter(Objects::nonNull)
                .sorted(roleComparator())
                .map(this::brief)
                .toList();
    }

    public RoleBrief brief(AuthRole role) {
        return new RoleBrief(role.getId(), role.getCode(), role.getName());
    }

    /** 按角色编码解析角色，未知编码直接报错，避免静默降权。 */
    public List<AuthRole> rolesByCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return List.of();
        }
        Map<String, AuthRole> byCode = allRoles().stream()
                .collect(Collectors.toMap(AuthRole::getCode, Function.identity()));
        List<AuthRole> result = new ArrayList<>();
        for (String code : new LinkedHashSet<>(codes)) {
            AuthRole role = byCode.get(code);
            if (role == null) {
                throw new BizException(AuthCodes.BAD_REQUEST, "角色不存在：" + code);
            }
            result.add(role);
        }
        return result;
    }

    public Map<Long, Long> memberCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (AuthUserRole relation : userRoleRepository.findAll()) {
            counts.merge(relation.getRoleId(), 1L, Long::sum);
        }
        return counts;
    }

    public List<RoleNode> roleTree() {
        List<AuthRole> roles = allRoles();
        Map<Long, Long> counts = memberCounts();
        Map<Long, List<AuthRole>> children = new HashMap<>();
        Set<Long> ids = roles.stream().map(AuthRole::getId).collect(Collectors.toSet());
        List<AuthRole> roots = new ArrayList<>();
        for (AuthRole role : roles) {
            if (role.getParentId() == null || !ids.contains(role.getParentId())) {
                roots.add(role);
            } else {
                children.computeIfAbsent(role.getParentId(), key -> new ArrayList<>()).add(role);
            }
        }
        return roots.stream()
                .sorted(roleComparator())
                .map(role -> toNode(role, children, counts, new HashSet<>()))
                .toList();
    }

    private RoleNode toNode(AuthRole role, Map<Long, List<AuthRole>> children,
                            Map<Long, Long> counts, Set<Long> path) {
        if (!path.add(role.getId())) {
            return new RoleNode(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                    role.getParentId(), role.getMaxMembers(), counts.getOrDefault(role.getId(), 0L),
                    role.isBuiltIn(), List.of());
        }
        List<RoleNode> childNodes = children.getOrDefault(role.getId(), List.of()).stream()
                .sorted(roleComparator())
                .map(child -> toNode(child, children, counts, new HashSet<>(path)))
                .toList();
        return new RoleNode(role.getId(), role.getCode(), role.getName(), role.getDescription(),
                role.getParentId(), role.getMaxMembers(), counts.getOrDefault(role.getId(), 0L),
                role.isBuiltIn(), childNodes);
    }

    public Comparator<AuthRole> roleComparator() {
        return Comparator.comparingInt(AuthRole::getSortOrder).thenComparing(AuthRole::getId);
    }
}
