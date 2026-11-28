package com.rehome.rehome.repository;

import com.rehome.rehome.entity.chat.Conversation;
import com.rehome.rehome.entity.enums.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByMember_IdOrderByCreatedAtDesc(UUID memberId);

    List<Conversation> findByTypeAndCounterpartIdOrderByCreatedAtDesc(ConversationType type, UUID counterpartId);

    Optional<Conversation> findByMember_IdAndTypeAndCounterpartId(
            UUID memberId, ConversationType type, UUID counterpartId);
}
