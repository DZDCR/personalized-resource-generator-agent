package com.example.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 聊天消息：只存一份，群聊不为每个成员复制。
 * <p>clientMsgId 与 senderId 组成幂等键，客户端重试不会产生重复消息。
 */
@Data
@Entity
@Table(name = "chat_messages",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_chat_msg_seq", columnNames = {"conversation_id", "seq"}),
                @UniqueConstraint(name = "uk_chat_msg_client", columnNames = {"sender_id", "client_msg_id"})
        },
        indexes = {
                @Index(name = "idx_chat_msg_conv_seq", columnList = "conversation_id, seq"),
                @Index(name = "idx_chat_msg_conv_created", columnList = "conversation_id, created_at")
        })
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    /** 会话内递增序号，允许空洞。 */
    @Column(nullable = false)
    private Long seq;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "sender_name", nullable = false, length = 64)
    private String senderName;

    @Column(name = "client_msg_id", nullable = false, length = 64)
    private String clientMsgId;

    /** TEXT（当前 MVP 只启用文本，保留扩展位）。 */
    @Column(name = "message_type", nullable = false, length = 16)
    private String type = "TEXT";

    @Column(nullable = false, length = 4000)
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
