package com.example.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 多智能体生成的资源。
 * contentJson 为各类 Agent 的结构化产物。
 */
@Data
@Entity
@Table(name = "resources")
public class ResourceEntity {

    public enum ResourceType {
        DOC, MINDMAP, QUIZ, READING, VIDEO_SCRIPT, CODE
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ResourceType type;

    /** 主题（如：递归算法） */
    @Column(nullable = false, length = 128)
    private String topic;

    /** 难度：easy / medium / hard */
    @Column(length = 16)
    private String difficulty;

    /** 归属学生，null 表示公共资源 */
    private Long studentId;

    /** 结构化产物 JSON */
    @Column(columnDefinition = "jsonb")
    private String contentJson;

    /** Qdrant 向量 id（语义检索） */
    private String vectorId;

    /** 元信息：知识点清单、预估时长等 */
    @Column(columnDefinition = "jsonb")
    private String metaJson;

    /** 由哪个任务生成（幂等去重） */
    private String taskId;

    private Instant createdAt;
}