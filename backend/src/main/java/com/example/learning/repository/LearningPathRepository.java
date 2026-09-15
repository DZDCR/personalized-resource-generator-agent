package com.example.learning.repository;

import com.example.learning.entity.LearningPathEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LearningPathRepository extends JpaRepository<LearningPathEntity, Long> {

    Optional<LearningPathEntity> findTopByStudentIdAndStatusOrderByUpdatedAtDesc(
            Long studentId, String status);
}