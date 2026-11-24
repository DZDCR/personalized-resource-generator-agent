package com.example.learning.auth.repository;

import com.example.learning.auth.entity.AuthRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuthRoleRepository extends JpaRepository<AuthRole, Long> {

    Optional<AuthRole> findByCode(String code);

    boolean existsByCode(String code);

    List<AuthRole> findAllByOrderBySortOrderAscIdAsc();

    List<AuthRole> findByIdIn(Collection<Long> ids);

    long countByParentId(Long parentId);

    long countByRequiredRoleId(Long requiredRoleId);
}
