package com.example.learning.dto.response;

/**
 * 智能体之间/与前端通用的执行状态消息。
 * status: RUNNING / DONE / FAILED / PARTIAL
 */
public record AgentMessage(
        String taskId,
        String agentName,
        String status,
        String topic,
        String message,
        long durationMs
) {

    public static AgentMessage of(String taskId, String agentName, String status,
                                  String topic, String message, long durationMs) {
        return new AgentMessage(taskId, agentName, status, topic, message, durationMs);
    }
}