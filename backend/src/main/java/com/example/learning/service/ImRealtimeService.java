package com.example.learning.service;

import com.example.learning.config.ImSessionRegistry;
import com.example.learning.dto.im.MessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * IM 实时转发：把已提交的消息/已读事件转成 WebSocket 帧并广播。
 * <p>广播发生在事务提交之后，避免接收方读到尚未落库的消息。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImRealtimeService {

    private final ImService imService;
    private final ImSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 发送并广播。originSessionId 非空时，该连接只收 ACK，避免重复收到 message.new。
     * <p>sendMessage 是 @Transactional 方法，跨 Bean 调用返回时事务已提交，
     * 因此这里广播出去的消息一定已经落库，接收方立即补拉也不会读到旧数据。
     */
    public MessageDto sendAndBroadcast(Long conversationId, Long senderId, String clientMsgId,
                                       String content, String originSessionId) {
        MessageDto message = imService.sendMessage(conversationId, senderId, clientMsgId, content);
        List<Long> memberIds = imService.memberIds(conversationId);
        String frame = frame("message.new", null, message);
        sessionRegistry.broadcast(memberIds, frame, originSessionId);
        return message;
    }

    public void broadcastRead(Long conversationId, Long userId, long seq, long unreadCount,
                              String originSessionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("conversationId", conversationId);
        payload.put("userId", userId);
        payload.put("seq", seq);
        payload.put("unreadCount", unreadCount);
        String frame = frame("message.read", null, payload);
        // 只同步给同账号的其他设备，不向群成员暴露“谁已读”的细节。
        sessionRegistry.broadcast(List.of(userId), frame, originSessionId);
    }

    public String frame(String type, String requestId, Object payload) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("v", 1);
        root.put("type", type);
        if (requestId != null && !requestId.isBlank()) {
            root.put("requestId", requestId);
        }
        root.put("ts", Instant.now().toEpochMilli());
        root.put("payload", payload);
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("WebSocket 帧序列化失败", e);
        }
    }

    public String error(String requestId, int code, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("code", code);
        payload.put("message", message);
        return frame("error", requestId, payload);
    }
}
