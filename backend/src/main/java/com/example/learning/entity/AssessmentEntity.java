package com.example.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 评估记录（周评估 / 动态评估）。
 */
@Data
@Entity
@Table(name = "assessments")
public class AssessmentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    /** 评估周期，如 2026-W36 */
    @Column(length = 16)
    private String period;

    /** 综合得分 0~100 */
    private Double scoreTotal;

    /** 维度得分 + LLM 分析报告 JSON */
    @Column(columnDefinition = "jsonb")
    private String reportJson;

    private Instant createdAt;
}