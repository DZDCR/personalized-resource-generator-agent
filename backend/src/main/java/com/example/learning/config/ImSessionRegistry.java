package com.example.learning.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * IM WebSocket 会话注册表：userId → (sessionId → session)。
 * <p>网关式转发：业务服务只按 userId 广播，不关心连接落在哪台机器。
 * 课程项目为单实例内存实现，生产可替换为 Redis 路由 + 网关集群。
 */
@Slf4j
@Component
public class ImSessionRegistry {

    private final Map<Long, Map<String, WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(Long userId, WebSocketSession session) {
        sessions.computeIfAbsent(userId, k -> new ConcurrentHashMap<>()).put(session.getId(), session);
        log.debug("im session registered user={} session={}", userId, session.getId());
    }

    public void unregister(Long userId, WebSocketSession session) {
        Map<String, WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) return;
        userSessions.remove(session.getId());
        if (userSessions.isEmpty()) sessions.remove(userId);
        log.debug("im session unregistered user={} session={}", userId, session.getId());
    }

    public void sendToSession(WebSocketSession session, String json) {
        if (session == null || !session.isOpen()) return;
        try {
            // WebSocketSession 不是线程安全的：同一连接可能被多个业务线程同时写入，必须串行发送。
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (IOException e) {
            log.warn("im send failed session={}", session.getId(), e);
        }
    }

    public void sendToUser(Long userId, String json) {
        Map<String, WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions == null) return;
        userSessions.values().forEach(session -> sendToSession(session, json));
    }

    /**
     * 向多个用户广播。
     * <p>excludeSessionId 只排除“发送消息的那条连接”，发送者的其他设备仍会收到，
     * 从而保证多端同步；而发送连接本身通过 ACK 更新本地气泡，避免重复渲染。
     */
    public void broadcast(Collection<Long> userIds, String json, String excludeSessionId) {
        for (Long userId : userIds) {
            Map<String, WebSocketSession> userSessions = sessions.get(userId);
            if (userSessions == null) continue;
            userSessions.values().forEach(session -> {
                if (excludeSessionId == null || !excludeSessionId.equals(session.getId())) {
                    sendToSession(session, json);
                }
            });
        }
    }

    public boolean isOnline(Long userId) {
        Map<String, WebSocketSession> userSessions = sessions.get(userId);
        return userSessions != null && !userSessions.isEmpty();
    }

    public int onlineCount() {
        return sessions.values().stream().mapToInt(Map::size).sum();
    }
}
