package com.example.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 学生画像（版本化存储，随学随新）。
 * profileJson 结构见 docs/architecture.md 画像维度定义。
 */
@Data
@Entity
@Table(name = "student_profiles")
public class StudentProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    /** 画像 JSON 全文（含 ≥6 维度） */
    @Column(columnDefinition = "jsonb")
    private String profileJson;

    /** 版本号，每次更新 +1 */
    private Integer version;

    /** 画像置信度 0~1 */
    private Double confidence;

    private Instant createdAt;

    @PrePersist
    void prePersist() {
        this.version = this.version == null ? 1 : this.version;
        this.createdAt = Instant.now();
    }
}