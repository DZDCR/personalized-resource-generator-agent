package com.example.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 学习路径（版本化）。planJson 为路径节点数组。
 */
@Data
@Entity
@Table(name = "learning_paths")
public class LearningPathEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long studentId;

    /** 学习目标知识点 */
    @Column(length = 128)
    private String targetTopic;

    /** 路径 JSON（节点序列 + 资源id） */
    @Column(columnDefinition = "jsonb")
    private String planJson;

    /** 版本号 */
    private Integer planVersion;

    /** 状态：IN_PROGRESS / COMPLETED / ARCHIVED */
    @Column(length = 32)
    private String status;

    private Instant createdAt;

    private Instant updatedAt;
}