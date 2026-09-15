package com.example.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 测试记录。
 */
@Data
@Entity
@Table(name = "quiz_records")
public class QuizRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    private Long resourceId;

    /** 主题 */
    @Column(length = 128)
    private String topic;

    /** 得分 0~100 */
    private Double score;

    /** 答案 JSON */
    @Column(columnDefinition = "jsonb")
    private String answersJson;

    /** 用时秒 */
    private Integer durationSec;

    private Instant createdAt;
}