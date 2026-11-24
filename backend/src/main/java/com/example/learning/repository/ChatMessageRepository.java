package com.example.learning.repository;

import com.example.learning.entity.ChatMessageEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    Optional<ChatMessageEntity> findBySenderIdAndClientMsgId(Long senderId, String clientMsgId);

    List<ChatMessageEntity> findByConversationIdAndSeqGreaterThanOrderBySeqAsc(
            Long conversationId, Long afterSeq, Pageable pageable);

    List<ChatMessageEntity> findByConversationIdOrderBySeqDesc(Long conversationId, Pageable pageable);

    Optional<ChatMessageEntity> findFirstByConversationIdOrderBySeqDesc(Long conversationId);

    long countByConversationIdAndSeqGreaterThan(Long conversationId, Long afterSeq);

    @Modifying
    @Query("delete from ChatMessageEntity m where m.conversationId = :conversationId")
    void deleteByConversationId(@Param("conversationId") Long conversationId);

    @Modifying
    @Query("update ChatMessageEntity m set m.senderName = :senderName where m.senderId = :senderId")
    int updateSenderName(@Param("senderId") Long senderId, @Param("senderName") String senderName);
}
