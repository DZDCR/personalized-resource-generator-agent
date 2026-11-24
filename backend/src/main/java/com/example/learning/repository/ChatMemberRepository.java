package com.example.learning.repository;

import com.example.learning.entity.ChatMemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatMemberRepository extends JpaRepository<ChatMemberEntity, Long> {

    List<ChatMemberEntity> findByConversationId(Long conversationId);

    List<ChatMemberEntity> findByUserId(Long userId);

    Optional<ChatMemberEntity> findByConversationIdAndUserId(Long conversationId, Long userId);

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    long countByConversationId(Long conversationId);
}
