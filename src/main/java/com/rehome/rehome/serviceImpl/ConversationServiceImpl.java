package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateConversationRequest;
import com.rehome.rehome.dto.request.SendMessageRequest;
import com.rehome.rehome.dto.response.ConversationResponse;
import com.rehome.rehome.dto.response.ConversationSummaryResponse;
import com.rehome.rehome.dto.response.MessageResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.chat.Conversation;
import com.rehome.rehome.entity.chat.Message;
import com.rehome.rehome.entity.enums.ConversationType;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.VerificationStatus;
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
import com.rehome.rehome.service.ConversationService;
import com.rehome.rehome.service.PushNotificationService;
import com.rehome.rehome.util.CloudinaryImageUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String CONVERSATION_NOT_FOUND = "Không tìm thấy hội thoại.";
    private static final String NOT_A_PARTICIPANT = "Bạn không thuộc hội thoại này.";

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("(?:\\+84|0)(?:[\\s.-]?\\d){9,10}\\b");
    private static final Pattern BANK_ACCOUNT_PATTERN = Pattern.compile("\\b\\d{9,19}\\b");

    private static final int MESSAGE_RATE_LIMIT_MAX = 10;
    private static final Duration MESSAGE_RATE_LIMIT_WINDOW = Duration.ofSeconds(30);
    private final com.rehome.rehome.service.ContentModerationService moderation;
    private final Map<UUID, Deque<Instant>> messageSendTimestamps = new ConcurrentHashMap<>();

    private static final List<OrderStatus> SHIPPING_ORDER_STATUSES = List.of(
            OrderStatus.PACKING, OrderStatus.PACKED, OrderStatus.SHIPPING);

    private static final List<ReturnRequestStatus> OPEN_RETURN_STATUSES = List.of(
            ReturnRequestStatus.PENDING, ReturnRequestStatus.APPROVED,
            ReturnRequestStatus.SHIPPED_BACK, ReturnRequestStatus.INSPECTING);

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OrganizationProfileRepository organizationProfileRepository;
    private final WarehouseRepository warehouseRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final AccountRepository accountRepository;
    private final ConsignmentFormRepository consignmentFormRepository;
    private final OrderRepository orderRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PushNotificationService pushNotificationService;

    private static String conversationTopic(UUID conversationId) {
        return "/topic/conversations." + conversationId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> listConversations(Account viewer) {
        return buildSummaries(findInbox(viewer), viewer);
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationSummaryResponse getConversation(Account viewer, UUID conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(CONVERSATION_NOT_FOUND));
        requireParticipant(viewer, conversation);
        return buildSummaries(List.of(conversation), viewer).get(0);
    }

    private record LastMessageInfo(String content, Instant createdAt, UUID senderId) {
    }

    private List<ConversationSummaryResponse> buildSummaries(List<Conversation> conversations, Account viewer) {
        if (conversations.isEmpty()) {
            return List.of();
        }

        List<UUID> conversationIds = conversations.stream().map(Conversation::getId).toList();

        Map<UUID, LastMessageInfo> lastMessageByConversationId = messageRepository
                .findLastMessageByConversationIds(conversationIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0],
                        row -> new LastMessageInfo((String) row[1], (Instant) row[2], (UUID) row[3]),
                        (first, second) -> first));
        Map<UUID, Long> unreadByConversationId = toCountMap(messageRepository
                .countUnreadByConversationIds(conversationIds, viewer.getId()));
        Map<UUID, CounterpartDisplay> counterpartDisplays = resolveCounterpartDisplays(conversations, viewer.getRole());
        Map<UUID, MemberStats> memberStatsById = viewer.getRole() == Role.MODERATOR
                ? resolveMemberStats(conversations)
                : Map.of();

        return conversations.stream()
                .sorted(Comparator.comparing(
                        (Conversation c) -> lastActivityAt(c, lastMessageByConversationId.get(c.getId())),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(conversation -> {
                    UUID counterpartId = counterpartIdFor(conversation, viewer.getRole());
                    CounterpartDisplay display = counterpartDisplays.get(counterpartId);
                    LastMessageInfo lastMessage = lastMessageByConversationId.get(conversation.getId());
                    MemberStats stats = memberStatsById.get(conversation.getMember().getId());
                    return ConversationSummaryResponse.builder()
                            .id(conversation.getId().toString())
                            .type(conversation.getType().name())
                            .counterpart(ConversationSummaryResponse.CounterpartInfo.builder()
                                    .id(counterpartId.toString())
                                    .displayName(display == null ? null : display.displayName())
                                    .avatarUrl(display == null ? null : display.avatarUrl())
                                    .build())
                            .lastMessage(lastMessage == null ? null : ConversationSummaryResponse.LastMessageInfo
                                    .builder()
                                    .content(lastMessage.content())
                                    .createdAt(lastMessage.createdAt())
                                    .senderId(lastMessage.senderId() == null ? null : lastMessage.senderId().toString())
                                    .build())
                            .unreadCount(unreadByConversationId.getOrDefault(conversation.getId(), 0L))
                            .phone(stats == null ? null : stats.phone())
                            .consignCount(stats == null ? null : stats.consignCount())
                            .shippingCount(stats == null ? null : stats.shippingCount())
                            .returnCount(stats == null ? null : stats.returnCount())
                            .build();
                }).toList();
    }

    private record MemberStats(String phone, long consignCount, long shippingCount, long returnCount) {
    }

    private Map<UUID, MemberStats> resolveMemberStats(List<Conversation> conversations) {
        List<UUID> memberIds = conversations.stream().map(c -> c.getMember().getId()).distinct().toList();

        Map<UUID, String> phoneByMemberId = new HashMap<>();
        for (Account account : accountRepository.findAllById(memberIds)) {
            if (account.getPhone() != null) {
                phoneByMemberId.put(account.getId(), account.getPhone());
            }
        }
        Map<UUID, Long> consignCountByMemberId = toCountMap(
                consignmentFormRepository.countActiveFormsByMemberIds(memberIds));
        Map<UUID, Long> shippingCountByMemberId = toCountMap(orderRepository
                .countByBuyerIdsInAndStatusIn(memberIds, SHIPPING_ORDER_STATUSES));
        Map<UUID, Long> returnCountByMemberId = toCountMap(returnRequestRepository
                .countByOrderBuyerIdsInAndStatusIn(memberIds, OPEN_RETURN_STATUSES));

        Map<UUID, MemberStats> result = new HashMap<>();
        for (UUID memberId : memberIds) {
            result.put(memberId, new MemberStats(
                    phoneByMemberId.get(memberId),
                    consignCountByMemberId.getOrDefault(memberId, 0L),
                    shippingCountByMemberId.getOrDefault(memberId, 0L),
                    returnCountByMemberId.getOrDefault(memberId, 0L)));
        }
        return result;
    }

    private static Map<UUID, Long> toCountMap(List<Object[]> rows) {
        Map<UUID, Long> result = new HashMap<>();
        if (rows != null) {
            for (Object[] row : rows) {
                if (row != null && row.length >= 2 && row[0] instanceof UUID id && row[1] instanceof Number count) {
                    result.put(id, count.longValue());
                }
            }
        }
        return result;
    }

    private static Instant lastActivityAt(Conversation conversation, LastMessageInfo lastMessage) {
        return lastMessage != null ? lastMessage.createdAt() : conversation.getCreatedAt();
    }

    @Override
    @Transactional
    public ConversationResponse createConversation(UUID memberId, CreateConversationRequest request) {
        if (!StringUtils.hasText(request.getType()) || !StringUtils.hasText(request.getCounterpartId())) {
            throw new BadRequestException("type và counterpartId là bắt buộc.");
        }

        ConversationType type;
        UUID counterpartId;
        try {
            type = ConversationType.valueOf(request.getType().trim());
            counterpartId = UUID.fromString(request.getCounterpartId().trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("type và counterpartId là bắt buộc.");
        }

        if (!counterpartExists(type, counterpartId)) {
            throw new IllegalArgumentException("Không tìm thấy đối tác hội thoại.");
        }

        Conversation conversation = conversationRepository
                .findByMember_IdAndTypeAndCounterpartId(memberId, type, counterpartId)
                .orElseGet(() -> {
                    Account member = accountRepository.findById(memberId)
                            .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
                    return conversationRepository.save(Conversation.builder()
                            .member(member)
                            .type(type)
                            .counterpartId(counterpartId)
                            .build());
                });

        return ConversationResponse.builder()
                .id(conversation.getId().toString())
                .type(conversation.getType().name())
                .build();
    }

    @Override
    @Transactional
    public PageResponse<MessageResponse> listMessages(Account viewer, UUID conversationId, int page, int size) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(CONVERSATION_NOT_FOUND));
        requireParticipant(viewer, conversation);

        messageRepository.markAsReadByConversationId(conversationId, viewer.getId(), Instant.now());

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "sentAt"));
        Page<Message> result = messageRepository.findByConversation_Id(conversationId, pageable);

        return PageResponse.from(result.map(m -> toMessageResponse(m, viewer.getId())));
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(Account viewer, UUID conversationId, SendMessageRequest request) {
        List<String> imageUrls = request.getImageUrls() == null ? List.of() : request.getImageUrls();
        boolean hasImages = !imageUrls.isEmpty();
        if (!StringUtils.hasText(request.getContent()) && !hasImages) {
            throw new BadRequestException("content hoặc imageUrls là bắt buộc.");
        }
        if (hasImages) {
            validateImageUrls(imageUrls);
        }

        checkMessageRateLimit(viewer.getId());

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(CONVERSATION_NOT_FOUND));
        requireParticipant(viewer, conversation);

        Account sender = accountRepository.findById(viewer.getId())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));

        String sensitiveMasked = maskSensitiveContent(request.getContent());
        var masked = moderation.mask(sensitiveMasked);
        Message message = Message.builder()
                .conversation(conversation)
                .senderAccount(sender)
                .content(masked.text())
                .originalContent(java.util.Objects.equals(sensitiveMasked, masked.text()) ? null : sensitiveMasked)
                .imageUrls(imageUrls)
                .sentAt(Instant.now())
                .build();
        messageRepository.save(message);
        moderation.recordMatch(viewer.getId(), "CHAT", message.getId(), masked.action());

        MessageResponse response = toMessageResponse(message, viewer.getId());
        messagingTemplate.convertAndSend(conversationTopic(conversationId),
                toMessageResponse(message, null));
        pushMessageNotification(conversation, sender, response);
        return response;
    }

    private List<Account> resolveRecipients(Conversation conversation, Account sender) {
        boolean senderIsMember = sender.getId().equals(conversation.getMember().getId());
        return switch (conversation.getType()) {
            case MEMBER_ORGANIZATION -> {
                UUID recipientId = senderIsMember
                        ? conversation.getCounterpartId() : conversation.getMember().getId();
                yield accountRepository.findById(recipientId).map(List::of).orElse(List.of());
            }
            case MEMBER_WAREHOUSE -> senderIsMember
                    ? accountRepository.findByRoleAndWarehouse_Id(Role.MODERATOR, conversation.getCounterpartId())
                    : List.of(conversation.getMember());
        };
    }

    private void pushMessageNotification(Conversation conversation, Account sender, MessageResponse response) {
        List<Account> recipients = resolveRecipients(conversation, sender);
        if (recipients.isEmpty()) {
            return;
        }
        String body = StringUtils.hasText(response.getContent()) ? response.getContent() : "Đã gửi ảnh";
        Map<String, String> data = Map.of(
                "type", "CHAT_MESSAGE",
                "conversationId", conversation.getId().toString());
        for (Account recipient : recipients) {
            pushNotificationService.send(recipient, "Tin nhắn mới", body, data);
        }
    }

    private static void validateImageUrls(List<String> imageUrls) {
        CloudinaryImageUrls.requireOwnUrls(imageUrls, "imageUrls chỉ được chứa ảnh đã upload qua hệ thống.");
    }

    private static String maskSensitiveContent(String content) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        String masked = EMAIL_PATTERN.matcher(content).replaceAll("***");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("***");
        masked = BANK_ACCOUNT_PATTERN.matcher(masked).replaceAll("***");
        return masked;
    }

    private void checkMessageRateLimit(UUID senderId) {
        Deque<Instant> timestamps = messageSendTimestamps.computeIfAbsent(senderId, id -> new ArrayDeque<>());
        synchronized (timestamps) {
            Instant now = Instant.now();
            Instant windowStart = now.minus(MESSAGE_RATE_LIMIT_WINDOW);
            while (!timestamps.isEmpty() && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MESSAGE_RATE_LIMIT_MAX) {
                throw new AppException(ErrorCode.MESSAGE_RATE_LIMITED);
            }
            timestamps.addLast(now);
        }
    }

    private List<Conversation> findInbox(Account viewer) {
        return switch (viewer.getRole()) {
            case MEMBER -> conversationRepository.findByMember_IdOrderByCreatedAtDesc(viewer.getId());
            case ORGANIZATION -> conversationRepository.findByTypeAndCounterpartIdOrderByCreatedAtDesc(
                    ConversationType.MEMBER_ORGANIZATION, viewer.getId());
            case MODERATOR -> conversationRepository.findByTypeAndCounterpartIdOrderByCreatedAtDesc(
                    ConversationType.MEMBER_WAREHOUSE, requireWarehouseId(viewer));
            case ADMIN -> throw new ForbiddenException(NOT_A_PARTICIPANT);
        };
    }

    private void requireParticipant(Account viewer, Conversation conversation) {
        if (!isParticipant(viewer, conversation)) {
            throw new ForbiddenException(NOT_A_PARTICIPANT);
        }
    }

    private static boolean isParticipant(Account viewer, Conversation conversation) {
        return switch (viewer.getRole()) {
            case MEMBER -> conversation.getMember().getId().equals(viewer.getId());
            case ORGANIZATION -> conversation.getType() == ConversationType.MEMBER_ORGANIZATION
                    && conversation.getCounterpartId().equals(viewer.getId());
            case MODERATOR -> conversation.getType() == ConversationType.MEMBER_WAREHOUSE
                    && viewer.getWarehouse() != null
                    && conversation.getCounterpartId().equals(viewer.getWarehouse().getId());
            case ADMIN -> false;
        };
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isParticipant(Account viewer, UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .map(conversation -> isParticipant(viewer, conversation))
                .orElse(false);
    }

    private boolean counterpartExists(ConversationType type, UUID counterpartId) {
        return switch (type) {
            case MEMBER_ORGANIZATION -> organizationProfileRepository.findById(counterpartId)
                    .filter(o -> o.getVerificationStatus() == VerificationStatus.VERIFIED)
                    .isPresent();
            case MEMBER_WAREHOUSE -> warehouseRepository.existsById(counterpartId);
        };
    }

    private record CounterpartDisplay(String displayName, String avatarUrl) {
    }

    private Map<UUID, CounterpartDisplay> resolveCounterpartDisplays(List<Conversation> conversations,
                                                                      Role viewerRole) {
        Map<UUID, CounterpartDisplay> displays = new HashMap<>();

        if (viewerRole != Role.MEMBER) {
            List<UUID> memberIds = conversations.stream().map(c -> c.getMember().getId()).distinct().toList();
            memberProfileRepository.findAllById(memberIds)
                    .forEach(profile -> displays.put(profile.getAccountId(),
                            new CounterpartDisplay(profile.getFullName(), profile.getAvatarUrl())));
            return displays;
        }

        List<UUID> organizationIds = conversations.stream()
                .filter(c -> c.getType() == ConversationType.MEMBER_ORGANIZATION)
                .map(Conversation::getCounterpartId).distinct().toList();
        List<UUID> warehouseIds = conversations.stream()
                .filter(c -> c.getType() == ConversationType.MEMBER_WAREHOUSE)
                .map(Conversation::getCounterpartId).distinct().toList();

        if (!organizationIds.isEmpty()) {
            organizationProfileRepository.findAllById(organizationIds)
                    .forEach(org -> displays.put(org.getAccountId(),
                            new CounterpartDisplay(org.getDisplayName(), org.getLogoUrl())));
        }
        if (!warehouseIds.isEmpty()) {
            warehouseRepository.findAllById(warehouseIds)
                    .forEach(warehouse -> displays.put(warehouse.getId(),
                            new CounterpartDisplay(warehouse.getName(), null)));
        }
        return displays;
    }

    private static UUID counterpartIdFor(Conversation conversation, Role viewerRole) {
        return viewerRole == Role.MEMBER ? conversation.getCounterpartId() : conversation.getMember().getId();
    }

    private UUID requireWarehouseId(Account moderator) {
        if (moderator.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);
        }
        return moderator.getWarehouse().getId();
    }

    private static MessageResponse toMessageResponse(Message message, UUID viewerId) {
        return MessageResponse.builder()
                .id(message.getId().toString())
                .senderId(message.getSenderAccount().getId().toString())
                .content(message.getContent())
                .originalContent(message.getOriginalContent())
                .imageUrls(message.getImageUrls() == null ? List.of() : message.getImageUrls())
                .createdAt(message.getSentAt())
                .isMine(message.getSenderAccount().getId().equals(viewerId))
                .build();
    }
}
