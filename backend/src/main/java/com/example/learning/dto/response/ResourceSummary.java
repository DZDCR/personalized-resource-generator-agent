package com.example.learning.dto.response;

import com.example.learning.entity.ResourceEntity.ResourceType;

import java.time.Instant;

/**
 * 资源列表项（前端列表展示用）。
 */
public record ResourceSummary(
        Long id,
        ResourceType type,
        String topic,
        String difficulty,
        String metaJson,
        Instant createdAt
) {}