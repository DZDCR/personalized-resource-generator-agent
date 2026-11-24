package com.example.learning.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

/**
 * 会话成员：保存成员快照、角色与已读游标。
 * <p>displayName 做轻量冗余，避免会话列表为每个成员回查学生表。
 */
@Data
@Entity
@Table(name = "chat_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_member", columnNames = {"conversation_id", "user_id"}),
        indexes = {
                @Index(name = "idx_chat_member_user", columnList = "user_id"),
                @Index(name = "idx_chat_member_conv", columnList = "conversation_id")
        })
public class ChatMemberEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    /** OWNER / MEMBER */
    @Column(name = "member_role", nullable = false, length = 16)
    private String role = "MEMBER";

    /** 已读到的会话 seq，用于计算未读数。 */
    @Column(name = "last_read_seq", nullable = false)
    private Long lastReadSeq = 0L;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        this.joinedAt = now;
        this.updatedAt = now;
        if (lastReadSeq == null) lastReadSeq = 0L;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }
}
