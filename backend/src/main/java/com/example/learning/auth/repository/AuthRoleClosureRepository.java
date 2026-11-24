package com.example.learning.auth.repository;

import com.example.learning.auth.entity.AuthRoleClosure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AuthRoleClosureRepository extends JpaRepository<AuthRoleClosure, Long> {

    /** 用户直接角色 + 所有后代角色（父角色继承子角色权限）。 */
    @Query("""
            select distinct c.descendantId
            from AuthRoleClosure c, AuthUserRole ur
            where ur.userId = :userId and c.ancestorId = ur.roleId
            """)
    List<Long> findEffectiveRoleIds(@Param("userId") Long userId);

    @Query("select c.descendantId from AuthRoleClosure c where c.ancestorId in :roleIds")
    List<Long> findDescendantIds(@Param("roleIds") Collection<Long> roleIds);

    @Query("select c.ancestorId from AuthRoleClosure c where c.descendantId in :roleIds")
    List<Long> findAncestorIds(@Param("roleIds") Collection<Long> roleIds);

    @Query("select c from AuthRoleClosure c where c.ancestorId = :roleId and c.depth > 0")
    List<AuthRoleClosure> findDescendants(@Param("roleId") Long roleId);
}
