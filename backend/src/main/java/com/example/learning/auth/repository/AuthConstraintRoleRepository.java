package com.example.learning.auth.repository;

import com.example.learning.auth.entity.AuthConstraintRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AuthConstraintRoleRepository extends JpaRepository<AuthConstraintRole, Long> {

    List<AuthConstraintRole> findByConstraintId(Long constraintId);

    List<AuthConstraintRole> findByConstraintIdIn(Collection<Long> constraintIds);

    List<AuthConstraintRole> findByRoleId(Long roleId);

    @Modifying
    @Query("delete from AuthConstraintRole cr where cr.constraintId = :constraintId")
    int deleteByConstraintId(@Param("constraintId") Long constraintId);
}
