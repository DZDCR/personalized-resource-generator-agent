package com.example.learning.repository;

import com.example.learning.entity.StudentProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentProfileRepository extends JpaRepository<StudentProfileEntity, Long> {

    List<StudentProfileEntity> findByStudentIdOrderByVersionDesc(Long studentId);

    Optional<StudentProfileEntity> findTopByStudentIdOrderByVersionDesc(Long studentId);
}