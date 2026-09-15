package com.example.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 辅导会话。
 */
@Data
@Entity
@Table(name = "tutor_sessions")
public class TutorSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    /** 对话 JSON 数组 */
    @Column(columnDefinition = "jsonb")
    private String messagesJson;

    /** 是否已解决 */
    private Boolean resolved;

    private Instant createdAt;

    private Instant updatedAt;
}