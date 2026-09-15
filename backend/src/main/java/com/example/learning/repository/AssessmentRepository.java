package com.example.learning.repository;

import com.example.learning.entity.AssessmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AssessmentRepository extends JpaRepository<AssessmentEntity, Long> {

    Optional<AssessmentEntity> findTopByStudentIdOrderByCreatedAtDesc(Long studentId);
}