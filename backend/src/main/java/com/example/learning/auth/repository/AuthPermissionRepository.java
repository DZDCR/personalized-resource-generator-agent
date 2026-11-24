package com.example.learning.auth.repository;

import com.example.learning.auth.entity.AuthPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuthPermissionRepository extends JpaRepository<AuthPermission, Long> {

    Optional<AuthPermission> findByCode(String code);

    boolean existsByCode(String code);

    List<AuthPermission> findAllByOrderBySortOrderAscIdAsc();

    List<AuthPermission> findByIdIn(Collection<Long> ids);

    long countByParentId(Long parentId);
}
