package com.example.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 学习行为日志（埋点）。
 * 事件类型: study_start / resource_open / resource_complete /
 *          quiz_submit / video_play / question_submit ...
 */
@Data
@Entity
@Table(name = "learning_events")
public class LearningEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    @Column(length = 64)
    private String eventType;

    @Column(columnDefinition = "jsonb")
    private String payloadJson;

    private Instant createdAt;
}