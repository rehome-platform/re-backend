package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.chat.Conversation;
import com.rehome.rehome.entity.chat.Message;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.ConversationType;
import com.rehome.rehome.entity.enums.Role;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MessageOriginalContentTest {
    @Autowired EntityManager em;
    @Autowired MessageRepository messages;

    @Test void historyPersistsOriginalButLatestMessageProjectionUsesMaskedContent() {
        Account member = Account.builder().email("chat-member@example.test").passwordHash("test")
                .role(Role.MEMBER).status(AccountStatus.ACTIVE).emailVerified(true).build();
        em.persist(member);
        Conversation conversation = Conversation.builder().member(member)
                .type(ConversationType.MEMBER_ORGANIZATION).counterpartId(UUID.randomUUID()).build();
        em.persist(conversation);
        Message legacy = Message.builder().conversation(conversation).senderAccount(member)
                .content("Clean legacy message").sentAt(Instant.parse("2026-11-20T00:00:00Z")).build();
        Message masked = Message.builder().conversation(conversation).senderAccount(member)
                .content("** *** · ***").originalContent("Đồ ngu · ***")
                .sentAt(Instant.parse("2026-11-20T00:00:01Z")).build();
        messages.saveAllAndFlush(List.of(legacy, masked));
        em.clear();
        assertThat(messages.findById(masked.getId()).orElseThrow().getOriginalContent()).isEqualTo("Đồ ngu · ***");
        assertThat(messages.findById(legacy.getId()).orElseThrow().getOriginalContent()).isNull();
        assertThat(messages.findByConversation_Id(conversation.getId(), PageRequest.of(0, 20)).getContent())
                .extracting(Message::getOriginalContent).containsExactlyInAnyOrder(null, "Đồ ngu · ***");
        assertThat(messages.findLastMessageByConversationIds(List.of(conversation.getId())).get(0)[1])
                .isEqualTo("** *** · ***");
    }
}
