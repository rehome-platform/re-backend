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

    // Hộp thư của Member
    List<Conversation> findByMember_IdOrderByCreatedAtDesc(UUID memberId);

    // Hộp thư của Organization (type = MEMBER_ORGANIZATION, counterpartId = account id của tổ chức)
    // và hộp thư chung của kho (type = MEMBER_WAREHOUSE, counterpartId = warehouse id) — F-091
    List<Conversation> findByTypeAndCounterpartIdOrderByCreatedAtDesc(ConversationType type, UUID counterpartId);

    Optional<Conversation> findByMember_IdAndTypeAndCounterpartId(
            UUID memberId, ConversationType type, UUID counterpartId);
}
