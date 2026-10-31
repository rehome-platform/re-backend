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
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.VerificationStatus;
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
import com.rehome.rehome.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String CONVERSATION_NOT_FOUND = "Không tìm thấy hội thoại.";
    private static final String NOT_A_PARTICIPANT = "Bạn không thuộc hội thoại này.";

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final OrganizationProfileRepository organizationProfileRepository;
    private final WarehouseRepository warehouseRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final AccountRepository accountRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> listConversations(Account viewer) {
        List<Conversation> conversations = findInbox(viewer);
        if (conversations.isEmpty()) {
            return List.of();
        }

        List<UUID> conversationIds = conversations.stream().map(Conversation::getId).toList();

        Map<UUID, String> lastMessageByConversationId = messageRepository
                .findLastMessageByConversationIds(conversationIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (String) row[1], (first, second) -> first));
        Map<UUID, Long> unreadByConversationId = messageRepository
                .countUnreadByConversationIds(conversationIds, viewer.getId()).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
        Map<UUID, String> counterpartNames = resolveCounterpartNames(conversations, viewer.getRole());

        return conversations.stream().map(conversation -> {
            UUID counterpartId = counterpartIdFor(conversation, viewer.getRole());
            return ConversationSummaryResponse.builder()
                    .id(conversation.getId().toString())
                    .type(conversation.getType().name())
                    .counterpart(ConversationSummaryResponse.CounterpartInfo.builder()
                            .id(counterpartId.toString())
                            .displayName(counterpartNames.get(counterpartId))
                            .build())
                    .lastMessage(lastMessageByConversationId.get(conversation.getId()))
                    .unreadCount(unreadByConversationId.getOrDefault(conversation.getId(), 0L))
                    .build();
        }).toList();
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

        // Mở hội thoại là đã đọc: nếu không set readAt ở đây thì unreadCount (API 110) chỉ tăng
        messageRepository.markAsReadByConversationId(conversationId, viewer.getId(), Instant.now());

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "sentAt"));
        Page<Message> result = messageRepository.findByConversation_Id(conversationId, pageable);

        return PageResponse.from(result.map(ConversationServiceImpl::toMessageResponse));
    }

    @Override
    @Transactional
    public MessageResponse sendMessage(Account viewer, UUID conversationId, SendMessageRequest request) {
        if (!StringUtils.hasText(request.getContent())) {
            throw new BadRequestException("content là bắt buộc.");
        }

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(CONVERSATION_NOT_FOUND));
        requireParticipant(viewer, conversation);

        Account sender = accountRepository.findById(viewer.getId())
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));

        Message message = Message.builder()
                .conversation(conversation)
                .senderAccount(sender)
                .content(request.getContent())
                .sentAt(Instant.now())
                .build();
        messageRepository.save(message);

        return toMessageResponse(message);
    }

    // Hộp thư theo vai trò: Moderator thấy hộp thư chung của kho mình, không phân theo từng người (F-091)
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
        boolean participant = switch (viewer.getRole()) {
            case MEMBER -> conversation.getMember().getId().equals(viewer.getId());
            case ORGANIZATION -> conversation.getType() == ConversationType.MEMBER_ORGANIZATION
                    && conversation.getCounterpartId().equals(viewer.getId());
            case MODERATOR -> conversation.getType() == ConversationType.MEMBER_WAREHOUSE
                    && viewer.getWarehouse() != null
                    && conversation.getCounterpartId().equals(viewer.getWarehouse().getId());
            case ADMIN -> false;
        };
        if (!participant) {
            throw new ForbiddenException(NOT_A_PARTICIPANT);
        }
    }

    // Member chỉ thấy tổ chức đã VERIFIED (F-082), nên cũng chỉ chat được với tổ chức đó
    private boolean counterpartExists(ConversationType type, UUID counterpartId) {
        return switch (type) {
            case MEMBER_ORGANIZATION -> organizationProfileRepository.findById(counterpartId)
                    .filter(o -> o.getVerificationStatus() == VerificationStatus.VERIFIED)
                    .isPresent();
            case MEMBER_WAREHOUSE -> warehouseRepository.existsById(counterpartId);
        };
    }

    private Map<UUID, String> resolveCounterpartNames(List<Conversation> conversations, Role viewerRole) {
        Map<UUID, String> names = new HashMap<>();

        if (viewerRole != Role.MEMBER) {
            // Organization / Moderator: phía đối diện là Member
            List<UUID> memberIds = conversations.stream().map(c -> c.getMember().getId()).distinct().toList();
            memberProfileRepository.findAllById(memberIds)
                    .forEach(profile -> names.put(profile.getAccountId(), profile.getFullName()));
            return names;
        }

        List<UUID> organizationIds = conversations.stream()
                .filter(c -> c.getType() == ConversationType.MEMBER_ORGANIZATION)
                .map(Conversation::getCounterpartId).distinct().toList();
        List<UUID> warehouseIds = conversations.stream()
                .filter(c -> c.getType() == ConversationType.MEMBER_WAREHOUSE)
                .map(Conversation::getCounterpartId).distinct().toList();

        if (!organizationIds.isEmpty()) {
            organizationProfileRepository.findAllById(organizationIds)
                    .forEach(org -> names.put(org.getAccountId(), org.getDisplayName()));
        }
        if (!warehouseIds.isEmpty()) {
            warehouseRepository.findAllById(warehouseIds)
                    .forEach(warehouse -> names.put(warehouse.getId(), warehouse.getName()));
        }
        return names;
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

    private static MessageResponse toMessageResponse(Message message) {
        return MessageResponse.builder()
                .id(message.getId().toString())
                .senderId(message.getSenderAccount().getId().toString())
                .content(message.getContent())
                .createdAt(message.getSentAt())
                .build();
    }
}
