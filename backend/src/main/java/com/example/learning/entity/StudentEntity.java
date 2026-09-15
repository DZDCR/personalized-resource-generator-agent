package com.example.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 学生基础信息。
 */
@Data
@Entity
@Table(name = "students")
public class StudentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 学号/用户标识 */
    @Column(nullable = false, unique = true, length = 64)
    private String studentNo;

    /** 姓名 */
    @Column(length = 64)
    private String name;

    /** 专业 */
    @Column(length = 128)
    private String major;

    /** 年级 */
    @Column(length = 32)
    private String grade;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}