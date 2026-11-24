package com.example.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 聊天会话：一个群聊或一对一会话。
 * <p>seq 由会话级 lastSeq 递增分配，保证同一会话内消息可排序；允许空洞。
 */
@Data
@Entity
@Table(name = "chat_conversations",
        indexes = @Index(name = "idx_chat_conv_updated", columnList = "updated_at"))
public class ChatConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** GROUP / DIRECT */
    @Column(name = "conversation_type", nullable = false, length = 16)
    private String type;

    /** 群聊标题；私聊可为空，由服务端按对方姓名生成展示标题。 */
    @Column(length = 128)
    private String title;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    /** 当前会话最大消息序号。 */
    @Column(name = "last_seq", nullable = false)
    private Long lastSeq = 0L;

    @Column(name = "last_message_preview", length = 256)
    private String lastMessagePreview;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (lastSeq == null) lastSeq = 0L;
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
