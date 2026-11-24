package com.example.learning.repository;

import com.example.learning.entity.ChatConversationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ChatConversationRepository extends JpaRepository<ChatConversationEntity, Long> {

    /**
     * 发送消息时锁定会话行，串行分配 seq，避免同一会话内并发写导致序号冲突。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ChatConversationEntity c where c.id = :id")
    Optional<ChatConversationEntity> findByIdForUpdate(@Param("id") Long id);
}
