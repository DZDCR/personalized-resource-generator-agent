package com.example.learning.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * WebSocket 进度处理器：连接时注册到 TaskProgressBroadcaster，关闭时注销。
 */
@Slf4j
@RequiredArgsConstructor
public class ProgressWebSocketHandler extends TextWebSocketHandler {

    private final TaskProgressBroadcaster broadcaster;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String taskId = getTaskId(session);
        if (taskId != null) {
            broadcaster.register(taskId, session);
            log.info("ws connected task={} session={}", taskId, session.getId());
            try {
                session.sendMessage(new TextMessage("{\"status\":\"connected\"}"));
            } catch (Exception e) {
                log.warn("send welcome failed", e);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String taskId = getTaskId(session);
        if (taskId != null) {
            broadcaster.unregister(taskId, session);
            log.info("ws closed task={} session={}", taskId, session.getId());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        // 客户端心跳或控制消息，暂不处理
    }

    private String getTaskId(WebSocketSession session) {
        Object attr = session.getAttributes().get("taskId");
        return attr != null ? attr.toString() : null;
    }
}