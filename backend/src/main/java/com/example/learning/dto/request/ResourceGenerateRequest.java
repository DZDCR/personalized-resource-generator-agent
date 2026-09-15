package com.example.learning.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 多智能体资源生成请求。
 */
public record ResourceGenerateRequest(
        @NotBlank(message = "studentId 不能为空") String studentId,
        @NotBlank(message = "topic 不能为空") String topic,
        // 期望生成的资源类型，空则全量生成
        java.util.List<String> resourceTypes,
        // 附加约束：难度、语言等
        java.util.Map<String, String> constraints
) {

    public ResourceGenerateRequest {
        if (constraints == null) {
            constraints = java.util.Map.of();
        }
    }
}