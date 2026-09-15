package com.example.learning.repository;

import com.example.learning.entity.LearningEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface LearningEventRepository extends JpaRepository<LearningEventEntity, Long> {

    List<LearningEventEntity> findByStudentIdAndCreatedAtAfter(Long studentId, Instant after);
}