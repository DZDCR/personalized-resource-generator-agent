package com.example.learning.config;

import com.example.learning.dto.response.AgentMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务进度广播服务：按 taskId 管理 WebSocket 会话，推送 AgentMessage。
 * Orchestrator 调用 broadcast() 向前端推送实时进度。
 */
@Slf4j
@Component
public class TaskProgressBroadcaster {

    private final Map<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(String taskId, WebSocketSession session) {
        sessions.computeIfAbsent(taskId, k -> ConcurrentHashMap.newKeySet()).add(session);
        log.info("ws registered for task {}", taskId);
    }

    public void unregister(String taskId, WebSocketSession session) {
        Set<WebSocketSession> set = sessions.get(taskId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) sessions.remove(taskId);
        }
    }

    public void broadcast(String taskId, AgentMessage message) {
        Set<WebSocketSession> set = sessions.get(taskId);
        if (set == null || set.isEmpty()) return;
        String json = toJson(message);
        for (WebSocketSession session : set) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (IOException e) {
                    log.warn("ws send failed for {}", taskId, e);
                }
            }
        }
    }

    private String toJson(AgentMessage msg) {
        return String.format("""
                {"taskId":"%s","agentName":"%s","status":"%s","topic":"%s","message":"%s","durationMs":%d}""",
                msg.taskId(), msg.agentName(), msg.status(), msg.topic(), msg.message(), msg.durationMs());
    }
}