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
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.ConversationRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.MessageRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    @org.junit.jupiter.api.BeforeEach
    void allowCleanChatByDefault() {
        org.mockito.Mockito.lenient().when(moderation.mask(any())).thenAnswer(inv ->
                new com.rehome.rehome.service.ContentModerationService.MaskResult(inv.getArgument(0), null));
    }

    @Test
    void maskedChatKeepsSanitizedOriginalInSendHistoryAndWebsocketButNotPushOrPreview() {
        UUID memberId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Account sender = account(memberId, Role.MEMBER);
        Account recipient = account(organizationId, Role.ORGANIZATION);
        Conversation conv = conversation(convId, memberId, ConversationType.MEMBER_ORGANIZATION, organizationId);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
        when(accountRepository.findById(memberId)).thenReturn(Optional.of(sender));
        when(accountRepository.findById(organizationId)).thenReturn(Optional.of(recipient));
        String original = "Đồ ngu, SĐT ***, email ***, STK ***";
        String masked = "** ***, SĐT ***, email ***, STK ***";
        var words = org.mockito.Mockito.mock(com.rehome.rehome.repository.ViolationWordRepository.class);
        when(words.findAllByIsActiveTrue()).thenReturn(List.of(
                com.rehome.rehome.entity.marketplace.ViolationWord.builder().word("đồ ngu")
                        .action(com.rehome.rehome.entity.enums.ViolationWordAction.AUTO_REJECT).build()));
        var masker = new com.rehome.rehome.service.ContentModerationService(words, null, null, null);
        when(moderation.mask(any())).thenAnswer(inv -> masker.mask(inv.getArgument(0)));
        when(messageRepository.save(any())).thenAnswer(inv -> {
            Message message = inv.getArgument(0);
            message.setId(messageId);
            return message;
        });
        MessageResponse sent = service.sendMessage(sender, convId, new SendMessageRequest(
                "Đồ ngu, SĐT 0912345678, email test@example.com, STK 123456789012", null));
        assertThat(sent.getContent()).isEqualTo(masked);
        assertThat(sent.getOriginalContent()).isEqualTo(original);
        var saved = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(saved.capture());
        assertThat(saved.getValue().getOriginalContent()).isEqualTo(original);
        verify(moderation, never()).evaluate(any());
        verify(moderation).recordMatch(memberId, "CHAT", messageId,
                com.rehome.rehome.entity.enums.ViolationWordAction.AUTO_REJECT);
        var broadcast = ArgumentCaptor.forClass(MessageResponse.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/conversations." + convId), broadcast.capture());
        assertThat(broadcast.getValue().getContent()).isEqualTo(masked);
        assertThat(broadcast.getValue().getOriginalContent()).isEqualTo(original);
        assertThat(broadcast.getValue().isMine()).isFalse();
        verify(pushNotificationService).send(eq(recipient), eq("Tin nhắn mới"), eq(masked), any());
        when(messageRepository.findByConversation_Id(eq(convId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(saved.getValue())));
        assertThat(service.listMessages(sender, convId, 0, 20).getContent().get(0).getOriginalContent()).isEqualTo(original);
        when(conversationRepository.findByMember_IdOrderByCreatedAtDesc(memberId)).thenReturn(List.of(conv));
        when(messageRepository.findLastMessageByConversationIds(List.of(convId)))
                .thenReturn(List.<Object[]>of(new Object[]{convId, masked, saved.getValue().getSentAt(), memberId}));
        assertThat(service.listConversations(sender).get(0).getLastMessage().getContent()).isEqualTo(masked);
    }

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private OrganizationProfileRepository organizationProfileRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private MemberProfileRepository memberProfileRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private ConsignmentFormRepository consignmentFormRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private com.rehome.rehome.service.PushNotificationService pushNotificationService;

    @Mock private com.rehome.rehome.service.ContentModerationService moderation;

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

        UUID senderId = UUID.randomUUID();
        Instant lastMessageAt = Instant.parse("2026-10-30T09:00:00Z");
        when(conversationRepository.findByMember_IdOrderByCreatedAtDesc(memberId)).thenReturn(List.of(conv));
        when(messageRepository.findLastMessageByConversationIds(List.of(convId)))
                .thenReturn(List.<Object[]>of(new Object[]{convId, "Cảm ơn bạn!", lastMessageAt, senderId}));
        when(messageRepository.countUnreadByConversationIds(List.of(convId), memberId))
                .thenReturn(List.<Object[]>of(new Object[]{convId, 2L}));
        when(organizationProfileRepository.findAllById(List.of(orgId)))
                .thenReturn(List.of(OrganizationProfile.builder()
                        .accountId(orgId).displayName("Chia Sẻ Yêu Thương")
                        .logoUrl("https://cdn/org-logo.png").build()));

        List<ConversationSummaryResponse> result = service.listConversations(account(memberId, Role.MEMBER));

        assertThat(result).hasSize(1);
        ConversationSummaryResponse res = result.get(0);
        assertThat(res.getId()).isEqualTo(convId.toString());
        assertThat(res.getType()).isEqualTo("MEMBER_ORGANIZATION");
        assertThat(res.getCounterpart().getId()).isEqualTo(orgId.toString());
        assertThat(res.getCounterpart().getDisplayName()).isEqualTo("Chia Sẻ Yêu Thương");
        assertThat(res.getCounterpart().getAvatarUrl()).isEqualTo("https://cdn/org-logo.png");
        assertThat(res.getLastMessage().getContent()).isEqualTo("Cảm ơn bạn!");
        assertThat(res.getLastMessage().getCreatedAt()).isEqualTo(lastMessageAt);
        assertThat(res.getLastMessage().getSenderId()).isEqualTo(senderId.toString());
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
                .thenReturn(List.of(MemberProfile.builder().accountId(memberId).fullName("Ngọc Anh")
                        .avatarUrl("https://cdn/member-avatar.png").build()));

        List<ConversationSummaryResponse> result =
                service.listConversations(account(orgAccountId, Role.ORGANIZATION));

        assertThat(result.get(0).getCounterpart().getId()).isEqualTo(memberId.toString());
        assertThat(result.get(0).getCounterpart().getDisplayName()).isEqualTo("Ngọc Anh");
        assertThat(result.get(0).getCounterpart().getAvatarUrl()).isEqualTo("https://cdn/member-avatar.png");
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
    void listConversations_moderator_includesMemberStatsBatchedByMemberIds() {
        UUID moderatorId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Conversation conv = conversation(convId, memberId, ConversationType.MEMBER_WAREHOUSE, warehouseId);
        Account memberAccount = Account.builder().id(memberId).role(Role.MEMBER).phone("0901234567").build();

        when(conversationRepository.findByTypeAndCounterpartIdOrderByCreatedAtDesc(
                ConversationType.MEMBER_WAREHOUSE, warehouseId)).thenReturn(List.of(conv));
        when(accountRepository.findAllById(List.of(memberId))).thenReturn(List.of(memberAccount));
        when(consignmentFormRepository.countActiveFormsByMemberIds(List.of(memberId)))
                .thenReturn(List.<Object[]>of(new Object[]{memberId, 2L}));
        when(orderRepository.countByBuyerIdsInAndStatusIn(eq(List.of(memberId)), any()))
                .thenReturn(List.<Object[]>of(new Object[]{memberId, 1L}));
        when(returnRequestRepository.countByOrderBuyerIdsInAndStatusIn(eq(List.of(memberId)), any()))
                .thenReturn(List.<Object[]>of(new Object[]{memberId, 3L}));

        List<ConversationSummaryResponse> result =
                service.listConversations(moderator(moderatorId, warehouseId));

        assertThat(result).hasSize(1);
        ConversationSummaryResponse summary = result.get(0);
        assertThat(summary.getPhone()).isEqualTo("0901234567");
        assertThat(summary.getConsignCount()).isEqualTo(2L);
        assertThat(summary.getShippingCount()).isEqualTo(1L);
        assertThat(summary.getReturnCount()).isEqualTo(3L);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void moderatorCanReadInboxAndConversationWhenMemberPhoneIsNull(boolean detail) {
        UUID warehouseId = UUID.randomUUID();
        Account viewer = moderator(UUID.randomUUID(), warehouseId);
        Account member = account(UUID.randomUUID(), Role.MEMBER);
        Conversation conv = conversation(UUID.randomUUID(), member.getId(), ConversationType.MEMBER_WAREHOUSE, warehouseId);
        when(accountRepository.findAllById(List.of(member.getId()))).thenReturn(List.of(member));
        when(messageRepository.findLastMessageByConversationIds(List.of(conv.getId())))
                .thenReturn(List.<Object[]>of(new Object[]{conv.getId(), "Preview", Instant.now(), null}));
        if (detail) when(conversationRepository.findById(conv.getId())).thenReturn(Optional.of(conv));
        else when(conversationRepository.findByTypeAndCounterpartIdOrderByCreatedAtDesc(
                ConversationType.MEMBER_WAREHOUSE, warehouseId)).thenReturn(List.of(conv));

        ConversationSummaryResponse result = detail ? service.getConversation(viewer, conv.getId())
                : service.listConversations(viewer).get(0);

        assertThat(result.getPhone()).isNull();
        assertThat(result.getLastMessage().getSenderId()).isNull();
        assertThat(result.getLastMessage().getContent()).isEqualTo("Preview");
        assertThat(result.getConsignCount()).isZero();
        assertThat(result.getShippingCount()).isZero();
        assertThat(result.getReturnCount()).isZero();
    }

    static java.util.stream.Stream<Number> countRepresentations() {
        return java.util.stream.Stream.of(7, 7L, new java.math.BigInteger("7"), new java.math.BigDecimal("7"));
    }

    private List<Object[]> countRowsWithInvalidEntries(UUID id, Number value) {
        return java.util.Arrays.asList(null, new Object[0], new Object[]{id}, new Object[]{null, value},
                new Object[]{"not-a-uuid", value}, new Object[]{id, null}, new Object[]{id, "not-a-number"},
                new Object[]{id, value});
    }

    @ParameterizedTest
    @MethodSource("countRepresentations")
    void inboxStatsAcceptNumberCountsAndIgnoreMalformedRows(Number count) {
        UUID warehouseId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Conversation conv = conversation(UUID.randomUUID(), memberId, ConversationType.MEMBER_WAREHOUSE, warehouseId);
        when(conversationRepository.findByTypeAndCounterpartIdOrderByCreatedAtDesc(
                ConversationType.MEMBER_WAREHOUSE, warehouseId)).thenReturn(List.of(conv));
        when(accountRepository.findAllById(List.of(memberId)))
                .thenReturn(List.of(Account.builder().id(memberId).phone("0901234567").build()));
        when(messageRepository.countUnreadByConversationIds(eq(List.of(conv.getId())), any()))
                .thenReturn(countRowsWithInvalidEntries(conv.getId(), count));
        when(consignmentFormRepository.countActiveFormsByMemberIds(List.of(memberId)))
                .thenReturn(countRowsWithInvalidEntries(memberId, count));
        when(orderRepository.countByBuyerIdsInAndStatusIn(eq(List.of(memberId)), any()))
                .thenReturn(countRowsWithInvalidEntries(memberId, count));
        when(returnRequestRepository.countByOrderBuyerIdsInAndStatusIn(eq(List.of(memberId)), any()))
                .thenReturn(countRowsWithInvalidEntries(memberId, count));

        var result = service.listConversations(moderator(UUID.randomUUID(), warehouseId)).get(0);

        assertThat(result.getPhone()).isEqualTo("0901234567");
        assertThat(result.getUnreadCount()).isEqualTo(7L);
        assertThat(result.getConsignCount()).isEqualTo(7L);
        assertThat(result.getShippingCount()).isEqualTo(7L);
        assertThat(result.getReturnCount()).isEqualTo(7L);
    }

    @Test
    void inboxWithMissingActivityDatesSortsNullLastAndKeepsNewestFirst() {
        UUID memberId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Conversation noDate = conversation(UUID.randomUUID(), memberId, ConversationType.MEMBER_WAREHOUSE, warehouseId);
        Conversation older = conversation(UUID.randomUUID(), memberId, ConversationType.MEMBER_WAREHOUSE, warehouseId);
        Conversation newest = conversation(UUID.randomUUID(), memberId, ConversationType.MEMBER_WAREHOUSE, warehouseId);
        older.setCreatedAt(Instant.parse("2026-11-01T00:00:00Z"));
        newest.setCreatedAt(Instant.parse("2026-11-02T00:00:00Z"));
        when(conversationRepository.findByMember_IdOrderByCreatedAtDesc(memberId))
                .thenReturn(List.of(noDate, older, newest));

        assertThat(service.listConversations(account(memberId, Role.MEMBER)))
                .extracting(ConversationSummaryResponse::getId)
                .containsExactly(newest.getId().toString(), older.getId().toString(), noDate.getId().toString());
    }

    @Test
    void listConversations_member_doesNotPopulateCounterpartStats() {
        UUID memberId = UUID.randomUUID();
        UUID orgAccountId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Conversation conv = conversation(convId, memberId, ConversationType.MEMBER_ORGANIZATION, orgAccountId);

        when(conversationRepository.findByMember_IdOrderByCreatedAtDesc(memberId)).thenReturn(List.of(conv));

        List<ConversationSummaryResponse> result = service.listConversations(account(memberId, Role.MEMBER));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPhone()).isNull();
        assertThat(result.get(0).getConsignCount()).isNull();
        verifyNoInteractions(consignmentFormRepository, orderRepository, returnRequestRepository);
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

    @Test
    void listConversations_sortedByLastMessageTime_notCreationTime() {
        UUID memberId = UUID.randomUUID();
        UUID oldConvRecentMessageId = UUID.randomUUID();
        UUID newConvNoMessageId = UUID.randomUUID();
        UUID orgId1 = UUID.randomUUID();
        UUID orgId2 = UUID.randomUUID();
        // Hội thoại A: tạo trước nhưng mới có tin lúc 2026-11-01 -> phải lên đầu
        Conversation olderButRecentlyActive = conversation(oldConvRecentMessageId, memberId,
                ConversationType.MEMBER_ORGANIZATION, orgId1);
        // Hội thoại B: tạo sau (2026-11-02) nhưng chưa có tin nào -> xếp sau vì mốc dùng createdAt cũ hơn mốc tin của A
        Conversation newerNoMessage = conversation(newConvNoMessageId, memberId,
                ConversationType.MEMBER_ORGANIZATION, orgId2);
        newerNoMessage.setCreatedAt(Instant.parse("2026-11-02T00:00:00Z"));
        olderButRecentlyActive.setCreatedAt(Instant.parse("2026-10-01T00:00:00Z"));

        when(conversationRepository.findByMember_IdOrderByCreatedAtDesc(memberId))
                .thenReturn(List.of(newerNoMessage, olderButRecentlyActive));
        when(messageRepository.findLastMessageByConversationIds(any()))
                .thenReturn(List.<Object[]>of(new Object[]{
                        oldConvRecentMessageId, "Tin mới nhất", Instant.parse("2026-11-03T00:00:00Z"),
                        UUID.randomUUID()}));
        when(organizationProfileRepository.findAllById(any())).thenReturn(List.of());

        List<ConversationSummaryResponse> result = service.listConversations(account(memberId, Role.MEMBER));

        assertThat(result).extracting(ConversationSummaryResponse::getId)
                .containsExactly(oldConvRecentMessageId.toString(), newConvNoMessageId.toString());
    }

    // =====================================================================
    // getConversation (mở thẳng 1 hội thoại)
    // =====================================================================

    @Test
    void getConversation_participant_returnsSummary() {
        UUID memberId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Conversation conv = conversation(convId, memberId, ConversationType.MEMBER_ORGANIZATION, orgId);
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
        when(organizationProfileRepository.findAllById(List.of(orgId))).thenReturn(List.of(
                OrganizationProfile.builder().accountId(orgId).displayName("Chia Sẻ Yêu Thương").build()));

        ConversationSummaryResponse res = service.getConversation(account(memberId, Role.MEMBER), convId);

        assertThat(res.getId()).isEqualTo(convId.toString());
        assertThat(res.getCounterpart().getDisplayName()).isEqualTo("Chia Sẻ Yêu Thương");
    }

    @Test
    void getConversation_notFound_throws() {
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findById(convId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getConversation(account(UUID.randomUUID(), Role.MEMBER), convId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy hội thoại.");
    }

    @Test
    void getConversation_notAParticipant_throwsForbidden() {
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(
                conversation(convId, UUID.randomUUID(), ConversationType.MEMBER_ORGANIZATION, UUID.randomUUID())));

        assertThatThrownBy(() -> service.getConversation(account(UUID.randomUUID(), Role.MEMBER), convId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không thuộc hội thoại này.");
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
        // Người gửi không phải chính viewer (memberId) đang xem
        assertThat(res.isMine()).isFalse();

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
                new SendMessageRequest("Đơn hàng OD-2026-000123 đã giao chưa?", null));

        assertThat(res.getId()).isNotNull();
        assertThat(res.getContent()).isEqualTo("Đơn hàng OD-2026-000123 đã giao chưa?");
        assertThat(res.getSenderId()).isEqualTo(memberId.toString());
        assertThat(res.getCreatedAt()).isNotNull();
        // Chính người gửi đang xem lại tin của mình
        assertThat(res.isMine()).isTrue();

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(captor.capture());
        assertThat(captor.getValue().getConversation()).isSameAs(conv);
        assertThat(captor.getValue().getSenderAccount()).isSameAs(sender);
    }

    @Test
    void sendMessage_blankContent_throwsBadRequest() {
        assertThatThrownBy(() -> service.sendMessage(account(UUID.randomUUID(), Role.MEMBER),
                UUID.randomUUID(), new SendMessageRequest("   ", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("content hoặc imageUrls là bắt buộc.");

        verifyNoInteractions(conversationRepository, messageRepository, accountRepository);
    }

    @Test
    void sendMessage_conversationNotFound_throws() {
        UUID convId = UUID.randomUUID();
        when(conversationRepository.findById(convId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sendMessage(account(UUID.randomUUID(), Role.MEMBER),
                convId, new SendMessageRequest("xin chào", null)))
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
                convId, new SendMessageRequest("xin chào", null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không thuộc hội thoại này.");

        verifyNoInteractions(messageRepository, accountRepository);
    }
}
