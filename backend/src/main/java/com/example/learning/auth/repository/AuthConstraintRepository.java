package com.example.learning.auth.repository;

import com.example.learning.auth.entity.AuthConstraint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AuthConstraintRepository extends JpaRepository<AuthConstraint, Long> {

    List<AuthConstraint> findAllByOrderByIdAsc();

    List<AuthConstraint> findAllByEnabledTrueOrderByIdAsc();

    Optional<AuthConstraint> findByCode(String code);

    boolean existsByCode(String code);
}
