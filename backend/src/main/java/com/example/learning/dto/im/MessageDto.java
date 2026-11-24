package com.example.learning.dto.im;

import java.time.Instant;

public record MessageDto(
        Long id,
        Long conversationId,
        Long seq,
        Long senderId,
        String senderName,
        String type,
        String content,
        String clientMsgId,
        Instant createdAt
) {
}
