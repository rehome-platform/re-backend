package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateConversationRequest;
import com.rehome.rehome.dto.request.SendMessageRequest;
import com.rehome.rehome.dto.response.ConversationResponse;
import com.rehome.rehome.dto.response.ConversationSummaryResponse;
import com.rehome.rehome.dto.response.MessageResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.chat.Conversation;
import com.rehome.rehome.entity.chat.Message;
import com.rehome.rehome.entity.enums.ConversationType;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ConversationRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.MessageRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private OrganizationProfileRepository organizationProfileRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private MemberProfileRepository memberProfileRepository;
    @Mock private AccountRepository accountRepository;

    @InjectMocks private ConversationServiceImpl service;

    // ---------- helpers ----------

    private Account account(UUID id, Role role) {
        return Account.builder().id(id).role(role).build();
    }

    private Account moderator(UUID id, UUID warehouseId) {
        return Account.builder().id(id).role(Role.MODERATOR)
                .warehouse(warehouseId == null ? null : Warehouse.builder().id(warehouseId).build())
                .build();
    }

    private Conversation conversation(UUID id, UUID memberId, ConversationType type, UUID counterpartId) {
        return Conversation.builder()
                .id(id)
                .member(account(memberId, Role.MEMBER))
                .type(type)
                .counterpartId(counterpartId)
                .build();
    }

    private Message message(UUID id, UUID senderId, String content, Instant sentAt) {
        return Message.builder()
                .id(id)
                .senderAccount(account(senderId, Role.MEMBER))
                .content(content)
                .sentAt(sentAt)
                .build();
    }

    // =====================================================================
    // listConversations  (API 110)
    // =====================================================================

    @Test
    void listConversations_member_withOrganizationCounterpart() {
        UUID memberId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Conversation conv = conversation(convId, memberId, ConversationType.MEMBER_ORGANIZATION, orgId);

        when(conversationRepository.findByMember_IdOrderByCreatedAtDesc(memberId)).thenReturn(List.of(conv));
        when(messageRepository.findLastMessageByConversationIds(List.of(convId)))
                .thenReturn(List.<Object[]>of(new Object[]{convId, "Cảm ơn bạn!"}));
        when(messageRepository.countUnreadByConversationIds(List.of(convId), memberId))
                .thenReturn(List.<Object[]>of(new Object[]{convId, 2L}));
        when(organizationProfileRepository.findAllById(List.of(orgId)))
                .thenReturn(List.of(OrganizationProfile.builder()
                        .accountId(orgId).displayName("Chia Sẻ Yêu Thương").build()));

        List<ConversationSummaryResponse> result = service.listConversations(account(memberId, Role.MEMBER));

        assertThat(result).hasSize(1);
        ConversationSummaryResponse res = result.get(0);
        assertThat(res.getId()).isEqualTo(convId.toString());
        assertThat(res.getType()).isEqualTo("MEMBER_ORGANIZATION");
        assertThat(res.getCounterpart().getId()).isEqualTo(orgId.toString());
        assertThat(res.getCounterpart().getDisplayName()).isEqualTo("Chia Sẻ Yêu Thương");
        assertThat(res.getLastMessage()).isEqualTo("Cảm ơn bạn!");
        assertThat(res.getUnreadCount()).isEqualTo(2L);
        verifyNoInteractions(memberProfileRepository);
    }

    @Test
    void listConversations_member_withWarehouseCounterpart_usesWarehouseName() {
        UUID memberId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Conversation conv = conversation(convId, memberId, ConversationType.MEMBER_WAREHOUSE, warehouseId);

        when(conversationRepository.findByMember_IdOrderByCreatedAtDesc(memberId)).thenReturn(List.of(conv));
        when(warehouseRepository.findAllById(List.of(warehouseId)))
                .thenReturn(List.of(Warehouse.builder().id(warehouseId).name("Kho Quận 7").build()));

        List<ConversationSummaryResponse> result = service.listConversations(account(memberId, Role.MEMBER));

        assertThat(result.get(0).getCounterpart().getDisplayName()).isEqualTo("Kho Quận 7");
        assertThat(result.get(0).getUnreadCount()).isZero();
        assertThat(result.get(0).getLastMessage()).isNull();
        verifyNoInteractions(organizationProfileRepository);
    }

    @Test
    void listConversations_organization_counterpartIsMember() {
        UUID orgAccountId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Conversation conv = conversation(convId, memberId, ConversationType.MEMBER_ORGANIZATION, orgAccountId);

        when(conversationRepository.findByTypeAndCounterpartIdOrderByCreatedAtDesc(
                ConversationType.MEMBER_ORGANIZATION, orgAccountId)).thenReturn(List.of(conv));
        when(memberProfileRepository.findAllById(List.of(memberId)))
                .thenReturn(List.of(MemberProfile.builder().accountId(memberId).fullName("Ngọc Anh").build()));

        List<ConversationSummaryResponse> result =
                service.listConversations(account(orgAccountId, Role.ORGANIZATION));

        assertThat(result.get(0).getCounterpart().getId()).isEqualTo(memberId.toString());
        assertThat(result.get(0).getCounterpart().getDisplayName()).isEqualTo("Ngọc Anh");
    }

    @Test
    void listConversations_moderator_readsSharedWarehouseInbox() {
        UUID moderatorId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Conversation conv = conversation(convId, memberId, ConversationType.MEMBER_WAREHOUSE, warehouseId);

        when(conversationRepository.findByTypeAndCounterpartIdOrderByCreatedAtDesc(
                ConversationType.MEMBER_WAREHOUSE, warehouseId)).thenReturn(List.of(conv));

        List<ConversationSummaryResponse> result =
                service.listConversations(moderator(moderatorId, warehouseId));

        // Hộp thư tra theo kho, không theo id của Moderator đang đăng nhập (F-091)
        assertThat(result).hasSize(1);
        verify(conversationRepository).findByTypeAndCounterpartIdOrderByCreatedAtDesc(
                ConversationType.MEMBER_WAREHOUSE, warehouseId);
    }

    @Test
    void listConversations_moderatorWithoutWarehouse_throws() {
        assertThatThrownBy(() -> service.listConversations(moderator(UUID.randomUUID(), null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MODERATOR_NO_WAREHOUSE));
    }

    @Test
    void listConversations_empty_returnsEmptyListWithoutMessageQueries() {
        UUID memberId = UUID.randomUUID();
        when(conversationRepository.findByMember_IdOrderByCreatedAtDesc(memberId)).thenReturn(List.of());

        assertThat(service.listConversations(account(memberId, Role.MEMBER))).isEmpty();

        verifyNoInteractions(messageRepository, organizationProfileRepository,
                warehouseRepository, memberProfileRepository);
    }

    // =====================================================================
    // createConversation  (API 111)
    // =====================================================================

    @Test
    void createConversation_new_savesConversation() {
        UUID memberId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Account member = account(memberId, Role.MEMBER);

        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(
                OrganizationProfile.builder().accountId(orgId)
                        .verificationStatus(VerificationStatus.VERIFIED).build()));
        when(conversationRepository.findByMember_IdAndTypeAndCounterpartId(
                memberId, ConversationType.MEMBER_ORGANIZATION, orgId)).thenReturn(Optional.empty());
        when(accountRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(conversationRepository.save(any())).thenAnswer(invocation -> {
            Conversation saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        ConversationResponse res = service.createConversation(memberId,
                new CreateConversationRequest("MEMBER_ORGANIZATION", orgId.toString()));

        assertThat(res.getId()).isNotNull();
        assertThat(res.getType()).isEqualTo("MEMBER_ORGANIZATION");

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(conversationRepository).save(captor.capture());
        assertThat(captor.getValue().getMember()).isSameAs(member);
        assertThat(captor.getValue().getCounterpartId()).isEqualTo(orgId);
    }

    @Test
    void createConversation_alreadyExists_returnsExistingWithoutSaving() {
        UUID memberId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();

        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(
                OrganizationProfile.builder().accountId(orgId)
                        .verificationStatus(VerificationStatus.VERIFIED).build()));
        when(conversationRepository.findByMember_IdAndTypeAndCounterpartId(
                memberId, ConversationType.MEMBER_ORGANIZATION, orgId))
                .thenReturn(Optional.of(conversation(convId, memberId,
                        ConversationType.MEMBER_ORGANIZATION, orgId)));

        ConversationResponse res = service.createConversation(memberId,
                new CreateConversationRequest("MEMBER_ORGANIZATION", orgId.toString()));

        assertThat(res.getId()).isEqualTo(convId.toString());
        verify(conversationRepository, never()).save(any());
        verifyNoInteractions(accountRepository);
    }

    @Test
    void createConversation_warehouseCounterpart_checksWarehouseRepository() {
        UUID memberId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();

        when(warehouseRepository.existsById(warehouseId)).thenReturn(true);
        when(conversationRepository.findByMember_IdAndTypeAndCounterpartId(
                memberId, ConversationType.MEMBER_WAREHOUSE, warehouseId))
                .thenReturn(Optional.of(conversation(UUID.randomUUID(), memberId,
                        ConversationType.MEMBER_WAREHOUSE, warehouseId)));

        ConversationResponse res = service.createConversation(memberId,
                new CreateConversationRequest("MEMBER_WAREHOUSE", warehouseId.toString()));

        assertThat(res.getType()).isEqualTo("MEMBER_WAREHOUSE");
        verifyNoInteractions(organizationProfileRepository);
    }

    @Test
    void createConversation_organizationNotFound_throws() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createConversation(UUID.randomUUID(),
                new CreateConversationRequest("MEMBER_ORGANIZATION", orgId.toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy đối tác hội thoại.");

        verify(conversationRepository, never()).save(any());
    }

    @Test
    void createConversation_organizationNotVerified_throws() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(
                OrganizationProfile.builder().accountId(orgId)
                        .verificationStatus(VerificationStatus.PENDING).build()));

        assertThatThrownBy(() -> service.createConversation(UUID.randomUUID(),
                new CreateConversationRequest("MEMBER_ORGANIZATION", orgId.toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy đối tác hội thoại.");
    }

    @Test
    void createConversation_warehouseNotFound_throws() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.existsById(warehouseId)).thenReturn(false);

        assertThatThrownBy(() -> service.createConversation(UUID.randomUUID(),
                new CreateConversationRequest("MEMBER_WAREHOUSE", warehouseId.toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy đối tác hội thoại.");
    }

    @Test
    void createConversation_blankFields_throwsBadRequest() {
        assertThatThrownBy(() -> service.createConversation(UUID.randomUUID(),
                new CreateConversationRequest(" ", UUID.randomUUID().toString())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("type và counterpartId là bắt buộc.");

        verifyNoInteractions(conversationRepository, organizationProfileRepository, warehouseRepository);
    }

    @Test
    void createConversation_invalidType_throwsBadRequest() {
        assertThatThrownBy(() -> service.createConversation(UUID.randomUUID(),
                new CreateConversationRequest("NOT_A_TYPE", UUID.randomUUID().toString())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("type và counterpartId là bắt buộc.");
    }

    @Test
    void createConversation_invalidCounterpartIdFormat_throwsBadRequest() {
        assertThatThrownBy(() -> service.createConversation(UUID.randomUUID(),
                new CreateConversationRequest("MEMBER_ORGANIZATION", "not-a-uuid")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("type và counterpartId là bắt buộc.");
    }

    // =====================================================================
    // listMessages  (API 112)
    // =====================================================================

    @Test
    void listMessages_member_returnsPageAndMarksAsRead() {
        UUID memberId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        Instant sentAt = Instant.parse("2026-10-13T13:00:00Z");

        when(conversationRepository.findById(convId)).thenReturn(Optional.of(
                conversation(convId, memberId, ConversationType.MEMBER_ORGANIZATION, UUID.randomUUID())));
        when(messageRepository.findByConversation_Id(eq(convId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(message(msgId, senderId, "Cảm ơn bạn!", sentAt))));

        PageResponse<MessageResponse> result =
                service.listMessages(account(memberId, Role.MEMBER), convId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        MessageResponse res = result.getContent().get(0);
        assertThat(res.getId()).isEqualTo(msgId.toString());
        assertThat(res.getSenderId()).isEqualTo(senderId.toString());
        assertThat(res.getContent()).isEqualTo("Cảm ơn bạn!");
        assertThat(res.getCreatedAt()).isEqualTo(sentAt);

        verify(messageRepository).markAsReadByConversationId(eq(convId), eq(memberId), any(Instant.class));
    }

    @Test
    void listMessages_newestFirstAndSizeClamped() {
        UUID memberId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(
                conversation(convId, memberId, ConversationType.MEMBER_ORGANIZATION, UUID.randomUUID())));
        when(messageRepository.findByConversation_Id(eq(convId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listMessages(account(memberId, Role.MEMBER), convId, 0, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(messageRepository).findByConversation_Id(eq(convId), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(captor.getValue().getSort().getOrderFor("sentAt")).isNotNull();
        assertThat(captor.getValue().getSort().getOrderFor("sentAt").isDescending()).isTrue();
    }

    @Test
    void listMessages_conversationNotFound_throws() {
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findById(convId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listMessages(account(UUID.randomUUID(), Role.MEMBER), convId, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy hội thoại.");

        verifyNoInteractions(messageRepository);
    }

    @Test
    void listMessages_otherMember_throwsForbidden() {
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(
                conversation(convId, UUID.randomUUID(), ConversationType.MEMBER_ORGANIZATION, UUID.randomUUID())));

        assertThatThrownBy(() -> service.listMessages(account(UUID.randomUUID(), Role.MEMBER), convId, 0, 20))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không thuộc hội thoại này.");

        verifyNoInteractions(messageRepository);
    }

    @Test
    void listMessages_organizationOfThisConversation_isAllowed() {
        UUID orgAccountId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(
                conversation(convId, UUID.randomUUID(), ConversationType.MEMBER_ORGANIZATION, orgAccountId)));
        when(messageRepository.findByConversation_Id(eq(convId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<MessageResponse> result =
                service.listMessages(account(orgAccountId, Role.ORGANIZATION), convId, 0, 20);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void listMessages_moderatorOfOtherWarehouse_throwsForbidden() {
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(
                conversation(convId, UUID.randomUUID(), ConversationType.MEMBER_WAREHOUSE, UUID.randomUUID())));

        assertThatThrownBy(() -> service.listMessages(
                moderator(UUID.randomUUID(), UUID.randomUUID()), convId, 0, 20))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không thuộc hội thoại này.");
    }

    @Test
    void listMessages_moderatorOfSameWarehouse_isAllowed() {
        UUID warehouseId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(
                conversation(convId, UUID.randomUUID(), ConversationType.MEMBER_WAREHOUSE, warehouseId)));
        when(messageRepository.findByConversation_Id(eq(convId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<MessageResponse> result = service.listMessages(
                moderator(UUID.randomUUID(), warehouseId), convId, 0, 20);

        assertThat(result.getContent()).isEmpty();
    }

    // =====================================================================
    // sendMessage  (API 113)
    // =====================================================================

    @Test
    void sendMessage_success_savesAndReturnsMessage() {
        UUID memberId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Account sender = account(memberId, Role.MEMBER);
        Conversation conv = conversation(convId, memberId, ConversationType.MEMBER_WAREHOUSE, UUID.randomUUID());

        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
        when(accountRepository.findById(memberId)).thenReturn(Optional.of(sender));
        when(messageRepository.save(any())).thenAnswer(invocation -> {
            Message saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        MessageResponse res = service.sendMessage(account(memberId, Role.MEMBER), convId,
                new SendMessageRequest("Đơn hàng OD-2026-000123 đã giao chưa?"));

        assertThat(res.getId()).isNotNull();
        assertThat(res.getContent()).isEqualTo("Đơn hàng OD-2026-000123 đã giao chưa?");
        assertThat(res.getSenderId()).isEqualTo(memberId.toString());
        assertThat(res.getCreatedAt()).isNotNull();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getConversation()).isSameAs(conv);
        assertThat(captor.getValue().getSenderAccount()).isSameAs(sender);
    }

    @Test
    void sendMessage_blankContent_throwsBadRequest() {
        assertThatThrownBy(() -> service.sendMessage(account(UUID.randomUUID(), Role.MEMBER),
                UUID.randomUUID(), new SendMessageRequest("   ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("content là bắt buộc.");

        verifyNoInteractions(conversationRepository, messageRepository, accountRepository);
    }

    @Test
    void sendMessage_conversationNotFound_throws() {
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findById(convId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage(account(UUID.randomUUID(), Role.MEMBER),
                convId, new SendMessageRequest("xin chào")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy hội thoại.");

        verifyNoInteractions(messageRepository, accountRepository);
    }

    @Test
    void sendMessage_notAParticipant_throwsForbidden() {
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(
                conversation(convId, UUID.randomUUID(), ConversationType.MEMBER_ORGANIZATION, UUID.randomUUID())));

        assertThatThrownBy(() -> service.sendMessage(account(UUID.randomUUID(), Role.MEMBER),
                convId, new SendMessageRequest("xin chào")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không thuộc hội thoại này.");

        verifyNoInteractions(messageRepository, accountRepository);
    }
}
