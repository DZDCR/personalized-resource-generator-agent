package com.example.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 路径节点（每步的学习内容与资源）。
 */
@Data
@Entity
@Table(name = "path_nodes")
public class PathNodeEntity {

    public enum NodeStatus { LOCKED, AVAILABLE, DONE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long pathId;

    /** 顺序号 */
    private Integer orderIndex;

    /** 知识点名称 */
    @Column(length = 128)
    private String topic;

    /** 该节点推荐资源 id 列表（JSON 数组） */
    @Column(columnDefinition = "jsonb")
    private String resourceIds;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private NodeStatus status;

    /** 该节点检查点测试得分 */
    private Double score;

    private Instant startedAt;

    private Instant finishedAt;
}