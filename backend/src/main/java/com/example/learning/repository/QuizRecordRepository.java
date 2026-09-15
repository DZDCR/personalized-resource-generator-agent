package com.example.learning.repository;

import com.example.learning.entity.QuizRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizRecordRepository extends JpaRepository<QuizRecordEntity, Long> {

    List<QuizRecordEntity> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    List<QuizRecordEntity> findTop10ByStudentIdOrderByCreatedAtDesc(Long studentId);
}