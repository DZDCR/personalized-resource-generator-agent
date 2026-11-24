package com.example.learning.controller;

import com.example.learning.common.ApiResult;
import com.example.learning.dto.im.*;
import com.example.learning.service.ImRealtimeService;
import com.example.learning.service.ImService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 实时聊天 REST 接口。
 * <p>WebSocket 负责实时收发；这些接口负责首屏加载、历史消息、离线补拉和创建会话。
 */
@RestController
@RequestMapping("/api/im")
@RequiredArgsConstructor
public class ImController {

    private final ImService imService;
    private final ImRealtimeService realtimeService;

    @GetMapping("/demo/users")
    public ApiResult<List<DemoUser>> demoUsers() {
        return ApiResult.success(imService.listDemoUsers());
    }

    @GetMapping("/conversations")
    public ApiResult<List<ConversationSummary>> conversations(@RequestParam Long studentId) {
        return ApiResult.success(imService.listConversations(studentId));
    }

    @PostMapping("/conversations/direct")
    public ApiResult<ConversationSummary> createDirect(@Valid @RequestBody CreateDirectRequest request) {
        return ApiResult.success(imService.createDirect(request.studentId(), request.peerId()));
    }

    @PostMapping("/conversations/group")
    public ApiResult<ConversationSummary> createGroup(@Valid @RequestBody CreateGroupRequest request) {
        return ApiResult.success(imService.createGroup(
                request.studentId(), request.title(), request.memberIds()));
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResult<MessagePage> messages(
            @PathVariable Long id,
            @RequestParam Long studentId,
            // afterSeq=0 表示首屏加载最近消息；>0 表示断线重连后的增量补拉。
            @RequestParam(defaultValue = "0") long afterSeq,
            @RequestParam(defaultValue = "50") int limit) {
        return ApiResult.success(imService.getMessages(id, studentId, afterSeq, limit));
    }

    /**
     * REST 发送通道：WebSocket 不可用时的降级方案。
     * originSessionId 传 null，表示发送方也通过广播接收 message.new。
     */
    @PostMapping("/conversations/{id}/messages")
    public ApiResult<MessageDto> send(
            @PathVariable Long id,
            @Valid @RequestBody SendMessageRequest request) {
        return ApiResult.success(realtimeService.sendAndBroadcast(
                id, request.studentId(), request.clientMsgId(), request.content(), null));
    }

    @PostMapping("/conversations/{id}/read")
    public ApiResult<Map<String, Object>> read(
            @PathVariable Long id,
            @Valid @RequestBody MarkReadRequest request) {
        long unread = imService.markRead(id, request.studentId(), request.seq());
        realtimeService.broadcastRead(id, request.studentId(), request.seq(), unread, null);
        return ApiResult.success(Map.of("unreadCount", unread));
    }

    @GetMapping("/conversations/{id}/members")
    public ApiResult<List<MemberSummary>> members(
            @PathVariable Long id,
            @RequestParam Long studentId) {
        return ApiResult.success(imService.getMembers(id, studentId));
    }
}
