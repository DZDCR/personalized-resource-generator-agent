package com.example.learning.dto.response;

/**
 * 多智能体生成任务提交响应（立即返回，任务异步执行）。
 */
public record GenerateTaskResponse(
        String taskId,
        String status,
        String message
) {}