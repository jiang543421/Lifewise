package com.lifewise.ai.repository;

import com.lifewise.ai.domain.ChatMessage;
import com.lifewise.ai.domain.ChatMessage.ChatMessageId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * chat_messages 仓储（V8 + V25 conversation_id 可空）。
 *
 * <p>复合主键（id, local_date） — 与 V8 分区表对齐。
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, ChatMessageId> {

    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.userId = :userId
              AND m.deletedAt IS NULL
              AND m.createdAt >= :since
            ORDER BY m.createdAt DESC
            """)
    List<ChatMessage> findByUserIdSince(@Param("userId") Long userId,
                                       @Param("since") java.time.OffsetDateTime since);

    @Query("""
            SELECT m FROM ChatMessage m
            WHERE m.userId = :userId
              AND m.localDate = :localDate
              AND m.deletedAt IS NULL
            ORDER BY m.createdAt ASC
            """)
    List<ChatMessage> findByUserIdAndLocalDate(@Param("userId") Long userId,
                                                @Param("localDate") LocalDate localDate);
}