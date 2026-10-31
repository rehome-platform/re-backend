package com.rehome.rehome.repository;

import com.rehome.rehome.entity.chat.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {

    Page<Message> findByConversation_Id(UUID conversationId, Pageable pageable);

    // Tin cuối của từng hội thoại, gom 1 query để tránh N+1 khi dựng danh sách hội thoại.
    // Trả về [conversationId, content]; sentAt trùng nhau có thể cho 2 dòng cùng 1 hội thoại -> service giữ dòng đầu.
    @Query("select m.conversation.id, m.content from Message m "
            + "where m.conversation.id in :conversationIds "
            + "and m.sentAt = (select max(m2.sentAt) from Message m2 where m2.conversation.id = m.conversation.id)")
    List<Object[]> findLastMessageByConversationIds(@Param("conversationIds") Collection<UUID> conversationIds);

    // Số tin chưa đọc do người khác gửi, theo từng hội thoại. Trả về [conversationId, count]
    @Query("select m.conversation.id, count(m) from Message m "
            + "where m.conversation.id in :conversationIds "
            + "and m.senderAccount.id <> :viewerId "
            + "and m.readAt is null "
            + "group by m.conversation.id")
    List<Object[]> countUnreadByConversationIds(@Param("conversationIds") Collection<UUID> conversationIds,
                                                @Param("viewerId") UUID viewerId);

    @Modifying
    @Query("update Message m set m.readAt = :readAt "
            + "where m.conversation.id = :conversationId "
            + "and m.senderAccount.id <> :viewerId "
            + "and m.readAt is null")
    int markAsReadByConversationId(@Param("conversationId") UUID conversationId,
                                   @Param("viewerId") UUID viewerId,
                                   @Param("readAt") Instant readAt);
}
