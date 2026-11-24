package com.example.learning.config;

import com.example.learning.common.BizException;
import com.example.learning.dto.im.MessageDto;
import com.example.learning.service.ImRealtimeService;
import com.example.learning.service.ImService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * IM WebSocket 处理器。
 * <p>协议：/ws/im?studentId=1
 * <pre>
 * 客户端 → 服务端：{"v":1,"type":"message.send","requestId":"r1","payload":{
 *   "conversationId":1,"clientMsgId":"uuid","content":"hello"}}
 * 服务端 → 客户端：{"type":"message.ack","requestId":"r1","payload":{...}}
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImWebSocketHandler extends TextWebSocketHandler {

    private final ImService imService;
    private final ImRealtimeService realtimeService;
    private final ImSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = userId(session);
        // 握手阶段只解析 studentId，不做鉴权：课程演示用，生产应校验登录态/Token。
        if (userId == null || !imService.studentExists(userId)) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION.withReason("invalid studentId"));
            return;
        }
        sessionRegistry.register(userId, session);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("serverTime", Instant.now().toEpochMilli());
        payload.put("heartbeatSeconds", 25);
        sessionRegistry.sendToSession(session, realtimeService.frame("connected", null, payload));
        log.info("im ws connected user={} session={}", userId, session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long userId = userId(session);
        if (userId == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        String requestId = null;
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            requestId = root.path("requestId").asText(null);
            String type = root.path("type").asText("");
            JsonNode payload = root.path("payload");

            switch (type) {
                // 心跳：客户端每 25s 发一次，服务端回 pong 维持连接并探测半开连接。
                case "ping" -> sendPong(session);
                // 发消息：落库后向会话成员广播 message.new，并给发送连接回 ACK。
                case "message.send" -> handleSend(session, userId, requestId, payload);
                // 已读回执：更新服务端游标，并同步给同账号的其他设备。
                case "message.read" -> handleRead(session, userId, requestId, payload);
                // 客户端重连后可主动请求一次在线确认。
                case "auth" -> sendConnected(session, userId);
                default -> sessionRegistry.sendToSession(session,
                        realtimeService.error(requestId, 400, "未知消息类型: " + type));
            }
        } catch (BizException e) {
            sessionRegistry.sendToSession(session, realtimeService.error(requestId, e.getCode(), e.getMessage()));
        } catch (Exception e) {
            log.warn("im ws handle failed user={} session={}", userId, session.getId(), e);
            sessionRegistry.sendToSession(session, realtimeService.error(requestId, 500, "消息处理失败"));
        }
    }

    /**
     * 处理 message.send：校验参数后落库并广播，最后只给当前连接回 ACK。
     */
    private void handleSend(WebSocketSession session, Long userId, String requestId, JsonNode payload) {
        long conversationId = payload.path("conversationId").asLong(0);
        String clientMsgId = payload.path("clientMsgId").asText("");
        String content = payload.path("content").asText("");
        if (conversationId <= 0) {
            sessionRegistry.sendToSession(session, realtimeService.error(requestId, 400, "conversationId 无效"));
            return;
        }
        MessageDto dto = realtimeService.sendAndBroadcast(
                conversationId, userId, clientMsgId, content, session.getId());
        sessionRegistry.sendToSession(session,
                realtimeService.frame("message.ack", requestId, dto));
    }

    /**
     * 处理 message.read：更新已读游标，仅同步给同账号的其他设备。
     */
    private void handleRead(WebSocketSession session, Long userId, String requestId, JsonNode payload) {
        long conversationId = payload.path("conversationId").asLong(0);
        long seq = payload.path("seq").asLong(0);
        if (conversationId <= 0) {
            sessionRegistry.sendToSession(session, realtimeService.error(requestId, 400, "conversationId 无效"));
            return;
        }
        long unread = imService.markRead(conversationId, userId, seq);
        realtimeService.broadcastRead(conversationId, userId, seq, unread, session.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("conversationId", conversationId);
        result.put("seq", seq);
        result.put("unreadCount", unread);
        sessionRegistry.sendToSession(session,
                realtimeService.frame("message.read.ok", requestId, result));
    }

    private void sendPong(WebSocketSession session) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serverTime", Instant.now().toEpochMilli());
        sessionRegistry.sendToSession(session, realtimeService.frame("pong", null, payload));
    }

    private void sendConnected(WebSocketSession session, Long userId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("serverTime", Instant.now().toEpochMilli());
        sessionRegistry.sendToSession(session, realtimeService.frame("connected", null, payload));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = userId(session);
        if (userId != null) {
            sessionRegistry.unregister(userId, session);
            log.info("im ws closed user={} session={} status={}", userId, session.getId(), status);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("im ws transport error session={}", session.getId(), exception);
    }

    private Long userId(WebSocketSession session) {
        Object value = session.getAttributes().get("studentId");
        if (value instanceof Number number) return number.longValue();
        if (value instanceof String text) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            session.close(status);
        } catch (Exception e) {
            log.debug("close ws failed session={}", session.getId(), e);
        }
    }
}
