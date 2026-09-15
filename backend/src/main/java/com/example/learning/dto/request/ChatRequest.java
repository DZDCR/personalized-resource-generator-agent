package com.example.learning.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 对话请求：统一入口（画像构建 / 辅导问答，由后端路由）。
 */
public record ChatRequest(
        @NotBlank(message = "studentId 不能为空") String studentId,
        @NotBlank(message = "message 不能为空") String message
) {}