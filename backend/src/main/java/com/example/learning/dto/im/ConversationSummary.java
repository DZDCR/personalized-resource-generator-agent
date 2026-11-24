package com.example.learning.dto.im;

import java.time.Instant;
import java.util.List;

/**
 * 会话列表项。unreadCount 由 lastSeq - lastReadSeq 对应的消息数计算。
 */
public record ConversationSummary(
        Long id,
        String type,
        String title,
        Long lastSeq,
        long unreadCount,
        String lastMessagePreview,
        Instant lastMessageAt,
        List<MemberSummary> members
) {
}
