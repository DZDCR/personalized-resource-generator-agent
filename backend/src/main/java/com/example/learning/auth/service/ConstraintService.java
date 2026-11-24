package com.example.learning.auth.service;

import com.example.learning.auth.config.AuthProperties;
import com.example.learning.auth.dto.AdminRequests;
import com.example.learning.auth.dto.AuthViews.ConstraintView;
import com.example.learning.auth.dto.AuthViews.RoleBrief;
import com.example.learning.auth.dto.AuthViews.RoleChoiceView;
import com.example.learning.auth.entity.AuthConstraint;
import com.example.learning.auth.entity.AuthConstraintRole;
import com.example.learning.auth.entity.AuthRole;
import com.example.learning.auth.entity.ConstraintType;
import com.example.learning.auth.repository.AuthConstraintRepository;
import com.example.learning.auth.repository.AuthConstraintRoleRepository;
import com.example.learning.auth.repository.AuthRoleRepository;
import com.example.learning.auth.repository.AuthUserRoleRepository;
import com.example.learning.auth.security.AuthCodes;
import com.example.learning.auth.security.ClientInfo;
import com.example.learning.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * RBAC2 约束：静态职责分离（SSD）、动态职责分离（DSD）、角色基数、角色前置依赖。
 *
 * <p>SSD 在“授予角色”时校验：用户有效角色（含继承）在同一约束内不得超过阈值。</p>
 * <p>DSD 在“签发/切换会话角色”时校验：单次会话激活的角色在同一约束内不得超过阈值。
 * 冲突时返回 40901 与可选项，前端弹出角色选择框，用户选择后重新签发令牌。</p>
 * <p>基数与前置依赖是角色属性，在授予角色时一并校验。</p>
 */
@Service
@RequiredArgsConstructor
public class ConstraintService {

    private final AuthConstraintRepository constraintRepository;
    private final AuthConstraintRoleRepository constraintRoleRepository;
    private final AuthRoleRepository roleRepository;
    private final AuthUserRoleRepository userRoleRepository;
    private final RbacQueryService queryService;
    private final AuthAuditService auditService;
    private final AuthProperties properties;

    @Transactional(readOnly = true)
    public List<ConstraintView> list() {
        List<AuthConstraint> constraints = constraintRepository.findAllByOrderByIdAsc();
        Map<Long, AuthRole> roles = queryService.roleMap();
        return constraints.stream().map(constraint -> toView(constraint, roles)).toList();
    }

    @Transactional
    public ConstraintView create(AdminRequests.ConstraintUpsert request, String actor, ClientInfo client) {
        String code = request.code().trim().toUpperCase();
        if (constraintRepository.existsByCode(code)) {
            throw new BizException(AuthCodes.CONFLICT, "约束编码已存在：" + code);
        }
        validateRequest(request);
        AuthConstraint constraint = new AuthConstraint();
        apply(constraint, request, code);
        constraintRepository.save(constraint);
        replaceMembers(constraint.getId(), request.roleIds());
        auditService.record("CONSTRAINT_CREATE", actor, null, true, client,
                "创建 " + request.type() + " 约束 " + code + "，角色数=" + request.roleIds().size());
        return toView(constraint, queryService.roleMap());
    }

    @Transactional
    public ConstraintView update(Long id, AdminRequests.ConstraintUpsert request, String actor, ClientInfo client) {
        AuthConstraint constraint = require(id);
        String code = request.code().trim().toUpperCase();
        if (!constraint.getCode().equals(code)) {
            throw new BizException(AuthCodes.BAD_REQUEST, "约束编码创建后不可修改");
        }
        validateRequest(request);
        apply(constraint, request, code);
        constraintRepository.save(constraint);
        replaceMembers(id, request.roleIds());
        // 约束收紧后，已有用户可能违反 SSD，交由下次角色变更/登录时提示；此处记录审计。
        auditService.record("CONSTRAINT_UPDATE", actor, null, true, client,
                "更新 " + request.type() + " 约束 " + code + "，角色数=" + request.roleIds().size());
        return toView(constraint, queryService.roleMap());
    }

    @Transactional
    public void delete(Long id, String actor, ClientInfo client) {
        AuthConstraint constraint = require(id);
        constraintRoleRepository.deleteByConstraintId(id);
        constraintRepository.delete(constraint);
        auditService.record("CONSTRAINT_DELETE", actor, null, true, client,
                "删除约束 " + constraint.getCode());
    }

    /** 授予角色时的静态校验（SSD + 基数 + 前置角色 + 全局角色数上限）。 */
    @Transactional(readOnly = true)
    public void validateAssignment(Long userId, List<Long> proposedRoleIds) {
        List<Long> distinct = proposedRoleIds == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(proposedRoleIds));
        if (distinct.size() > properties.getRbac().getMaxRolesPerUser()) {
            throw new BizException(AuthCodes.CONSTRAINT_VIOLATION,
                    "单个用户最多直接拥有 " + properties.getRbac().getMaxRolesPerUser() + " 个角色");
        }
        List<AuthRole> proposedRoles = roleRepository.findByIdIn(distinct);
        if (proposedRoles.size() != distinct.size()) {
            throw new BizException(AuthCodes.BAD_REQUEST, "存在无效的角色 ID");
        }
        List<Long> currentRoleIds = userId == null ? List.of()
                : userRoleRepository.findRoleIdsByUserId(userId);
        Set<Long> effective = queryService.effectiveRoleIdsForDirect(distinct);

        // 前置角色：当前有效角色集合中必须包含 requiredRoleId。
        for (AuthRole role : proposedRoles) {
            if (role.getRequiredRoleId() != null && !effective.contains(role.getRequiredRoleId())) {
                AuthRole required = roleRepository.findById(role.getRequiredRoleId()).orElse(null);
                throw new BizException(AuthCodes.CONSTRAINT_VIOLATION,
                        "授予角色「" + role.getName() + "」前必须先拥有角色「"
                                + (required == null ? role.getRequiredRoleId() : required.getName()) + "」");
            }
        }

        // 基数：只检查本次新增的角色，避免已有超限角色导致无法调整其他角色。
        for (AuthRole role : proposedRoles) {
            if (role.getMaxMembers() == null || currentRoleIds.contains(role.getId())) {
                continue;
            }
            long members = userRoleRepository.countByRoleId(role.getId());
            if (members >= role.getMaxMembers()) {
                throw new BizException(AuthCodes.CONSTRAINT_VIOLATION,
                        "角色「" + role.getName() + "」成员已达上限 " + role.getMaxMembers());
            }
        }

        // SSD：使用有效角色集合（含继承），防止通过高级角色绕过互斥。
        for (AuthConstraint constraint : constraintRepository.findAllByEnabledTrueOrderByIdAsc()) {
            if (constraint.getType() != ConstraintType.SSD) {
                continue;
            }
            Set<Long> members = memberRoleIds(constraint.getId());
            long conflict = effective.stream().filter(members::contains).count();
            if (conflict > constraint.getMaxRoles()) {
                throw new BizException(AuthCodes.CONSTRAINT_VIOLATION,
                        "违反静态职责分离约束「" + constraint.getName() + "」：同一用户不能同时拥有 "
                                + constraint.getMaxRoles() + " 个以上互斥角色");
            }
        }
    }

    /**
     * 签发/切换会话时校验 DSD。
     *
     * @param userId         用户 ID（用于返回可选项）
     * @param activeRoleIds  本次会话选择激活的直接角色
     * @return null 表示通过；否则返回前端需要的角色选择数据
     */
    @Transactional(readOnly = true)
    public RoleChoiceView checkDynamic(Long userId, Collection<Long> activeRoleIds) {
        Set<Long> effective = queryService.effectiveRoleIdsForDirect(activeRoleIds);
        List<AuthConstraint> violated = new ArrayList<>();
        Set<Long> conflicting = new LinkedHashSet<>();
        for (AuthConstraint constraint : constraintRepository.findAllByEnabledTrueOrderByIdAsc()) {
            if (constraint.getType() != ConstraintType.DSD) {
                continue;
            }
            Set<Long> members = memberRoleIds(constraint.getId());
            List<Long> hit = effective.stream().filter(members::contains).toList();
            if (hit.size() > constraint.getMaxRoles()) {
                violated.add(constraint);
                conflicting.addAll(hit);
            }
        }
        if (violated.isEmpty()) {
            return null;
        }
        Map<Long, AuthRole> roles = queryService.roleMap();
        List<RoleBrief> conflictingRoles = conflicting.stream().map(roles::get)
                .filter(java.util.Objects::nonNull).map(queryService::brief).toList();
        List<RoleBrief> options = queryService.briefs(queryService.effectiveRoleIds(userId));
        String reason = "动态职责分离（DSD）要求本次会话在互斥角色中择一激活："
                + violated.stream().map(AuthConstraint::getName).collect(Collectors.joining("、"));
        return new RoleChoiceView(true, reason, conflictingRoles, options, List.of());
    }

    @Transactional(readOnly = true)
    public List<ConstraintView> constraintsForRole(Long roleId) {
        List<Long> ids = constraintRoleRepository.findByRoleId(roleId).stream()
                .map(AuthConstraintRole::getConstraintId).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, AuthRole> roles = queryService.roleMap();
        return constraintRepository.findAllById(ids).stream()
                .map(constraint -> toView(constraint, roles))
                .toList();
    }

    private void validateRequest(AdminRequests.ConstraintUpsert request) {
        if (request.type() == null) {
            throw new BizException(AuthCodes.BAD_REQUEST, "请选择约束类型");
        }
        if (request.roleIds() == null || request.roleIds().size() < 2) {
            throw new BizException(AuthCodes.BAD_REQUEST, "职责分离约束至少需要两个角色");
        }
        long valid = roleRepository.findByIdIn(request.roleIds()).size();
        if (valid != new HashSet<>(request.roleIds()).size()) {
            throw new BizException(AuthCodes.BAD_REQUEST, "存在无效的角色 ID");
        }
        int maxRoles = request.maxRoles() == null ? 1 : request.maxRoles();
        int distinctRoles = new LinkedHashSet<>(request.roleIds()).size();
        if (maxRoles < 1 || maxRoles >= distinctRoles) {
            throw new BizException(AuthCodes.BAD_REQUEST, "允许角色数必须大于等于 1 且小于约束角色总数");
        }
    }

    private void apply(AuthConstraint constraint, AdminRequests.ConstraintUpsert request, String code) {
        constraint.setCode(code);
        constraint.setName(request.name().trim());
        constraint.setType(request.type());
        constraint.setMaxRoles(request.maxRoles() == null ? 1 : request.maxRoles());
        constraint.setDescription(request.description());
        constraint.setEnabled(request.enabled() == null || request.enabled());
    }

    private void replaceMembers(Long constraintId, List<Long> roleIds) {
        constraintRoleRepository.deleteByConstraintId(constraintId);
        Set<Long> unique = new LinkedHashSet<>(roleIds);
        List<AuthConstraintRole> links = unique.stream()
                .map(roleId -> new AuthConstraintRole(constraintId, roleId))
                .toList();
        constraintRoleRepository.saveAll(links);
    }

    private Set<Long> memberRoleIds(Long constraintId) {
        return constraintRoleRepository.findByConstraintId(constraintId).stream()
                .map(AuthConstraintRole::getRoleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private AuthConstraint require(Long id) {
        return constraintRepository.findById(id)
                .orElseThrow(() -> BizException.notFound("约束"));
    }

    private ConstraintView toView(AuthConstraint constraint, Map<Long, AuthRole> roles) {
        List<RoleBrief> roleBriefs = constraintRoleRepository.findByConstraintId(constraint.getId()).stream()
                .map(AuthConstraintRole::getRoleId)
                .distinct()
                .map(roles::get)
                .filter(java.util.Objects::nonNull)
                .map(queryService::brief)
                .toList();
        return new ConstraintView(constraint.getId(), constraint.getCode(), constraint.getName(),
                constraint.getType().name(), constraint.getMaxRoles(), constraint.getDescription(),
                constraint.isEnabled(), roleBriefs);
    }

    /** 仅用于测试/初始化：按编码查找约束。 */
    @Transactional(readOnly = true)
    public java.util.Optional<AuthConstraint> findByCode(String code) {
        return constraintRepository.findByCode(code);
    }

    /** 供初始化器直接写入关联关系。 */
    @Transactional
    public void saveMembers(Long constraintId, List<Long> roleIds) {
        replaceMembers(constraintId, roleIds);
    }

    /** 供初始化器直接创建约束。 */
    @Transactional
    public AuthConstraint saveConstraint(AuthConstraint constraint) {
        return constraintRepository.save(constraint);
    }
}
