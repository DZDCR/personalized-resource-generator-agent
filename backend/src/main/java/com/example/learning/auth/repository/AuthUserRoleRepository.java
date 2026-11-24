package com.example.learning.auth.repository;

import com.example.learning.auth.entity.AuthUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AuthUserRoleRepository extends JpaRepository<AuthUserRole, Long> {

    List<AuthUserRole> findByUserId(Long userId);

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    long countByRoleId(Long roleId);

    long countByUserId(Long userId);

    List<AuthUserRole> findByRoleId(Long roleId);

    @Query("select ur.roleId from AuthUserRole ur where ur.userId = :userId")
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);

    @Query("select ur.userId from AuthUserRole ur where ur.roleId = :roleId")
    List<Long> findUserIdsByRoleId(@Param("roleId") Long roleId);

    @Query("select ur.userId from AuthUserRole ur where ur.roleId in :roleIds")
    List<Long> findUserIdsByRoleIds(@Param("roleIds") Collection<Long> roleIds);

    @Modifying
    @Query("delete from AuthUserRole ur where ur.userId = :userId")
    int deleteByUserId(@Param("userId") Long userId);
}
