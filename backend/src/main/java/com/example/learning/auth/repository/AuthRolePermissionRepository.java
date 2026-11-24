package com.example.learning.auth.repository;

import com.example.learning.auth.entity.AuthRolePermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AuthRolePermissionRepository extends JpaRepository<AuthRolePermission, Long> {

    List<AuthRolePermission> findByRoleId(Long roleId);

    long countByPermissionId(Long permissionId);

    @Query("select rp.permissionId from AuthRolePermission rp where rp.roleId = :roleId")
    List<Long> findPermissionIdsByRoleId(@Param("roleId") Long roleId);

    @Query("select distinct rp.permissionId from AuthRolePermission rp where rp.roleId in :roleIds")
    List<Long> findPermissionIdsByRoleIds(@Param("roleIds") Collection<Long> roleIds);

    @Modifying
    @Query("delete from AuthRolePermission rp where rp.roleId = :roleId")
    int deleteByRoleId(@Param("roleId") Long roleId);
}
