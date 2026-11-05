package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.QueueClaimResponse;
import com.rehome.rehome.dto.response.QueueItemResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationLegalChangeRequest;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.QueueItem;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.OrganizationLegalChangeMapper;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.OrganizationLegalChangeRequestRepository;
import com.rehome.rehome.repository.QueueItemRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.service.QueueService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QueueServiceImpl implements QueueService {

    private final QueueItemRepository queueItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrganizationLegalChangeRequestRepository organizationLegalChangeRequestRepository;
    private final OrganizationLegalChangeMapper organizationLegalChangeMapper;
    private final ReturnRequestRepository returnRequestRepository;
    private final MemberProfileRepository memberProfileRepository;

    @Override
    @Transactional(readOnly = true)
    public List<QueueItemResponse> list(String type, Account moderator) {
        Warehouse warehouse = requireWarehouse(moderator);

        List<QueueItem> items;
        if (type == null || type.isBlank()) {
            items = queueItemRepository.findAllVisible(warehouse);
        } else {
            items = queueItemRepository.findVisibleByType(parseType(type), warehouse);
        }
        return toResponses(items);
    }

    // DG-01: QueueItemResponse của PACKING cần kèm dữ liệu đơn hàng (mã đơn, người nhận,
    // số món, vị trí kệ, thời gian chờ); LEGAL_CHANGE kèm nội dung xin đổi (xem bên dưới);
    // các loại việc khác giữ nguyên QueueItemResponse.from.
    private List<QueueItemResponse> toResponses(List<QueueItem> items) {
        List<UUID> packingOrderIds = items.stream()
                .filter(i -> i.getType() == QueueType.PACKING)
                .map(i -> parseUuid(i.getRefId()))
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, Order> ordersById = new HashMap<>();
        Map<UUID, List<OrderItem>> orderItemsByOrderId = new HashMap<>();

        if (!packingOrderIds.isEmpty()) {
            orderRepository.findAllById(packingOrderIds).forEach(o -> ordersById.put(o.getId(), o));
            orderItemRepository.findAllByOrder_IdInOrderByIdAsc(packingOrderIds)
                    .forEach(oi -> orderItemsByOrderId
                            .computeIfAbsent(oi.getOrder().getId(), k -> new ArrayList<>())
                            .add(oi));
        }

        List<UUID> legalChangeIds = items.stream()
                .filter(i -> i.getType() == QueueType.LEGAL_CHANGE)
                .map(i -> parseUuid(i.getRefId()))
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, OrganizationLegalChangeRequest> legalChangesById = new HashMap<>();
        if (!legalChangeIds.isEmpty()) {
            organizationLegalChangeRequestRepository.findAllById(legalChangeIds)
                    .forEach(r -> legalChangesById.put(r.getId(), r));
        }

        List<UUID> returnRequestIds = items.stream()
                .filter(i -> i.getType() == QueueType.RETURN)
                .map(i -> parseUuid(i.getRefId()))
                .filter(Objects::nonNull)
                .toList();

        Map<UUID, ReturnRequest> returnRequestsById = new HashMap<>();
        Map<UUID, Order> returnOrdersById = new HashMap<>();
        Map<UUID, String> sellerNameByOrderId = new HashMap<>();
        Map<UUID, String> buyerNameByOrderId = new HashMap<>();

        if (!returnRequestIds.isEmpty()) {
            returnRequestRepository.findAllById(returnRequestIds)
                    .forEach(r -> returnRequestsById.put(r.getId(), r));

            List<UUID> returnOrderIds = returnRequestsById.values().stream()
                    .map(r -> r.getOrder().getId())
                    .distinct()
                    .toList();

            if (!returnOrderIds.isEmpty()) {
                orderRepository.findAllById(returnOrderIds).forEach(o -> returnOrdersById.put(o.getId(), o));

                Map<UUID, List<OrderItem>> returnOrderItemsByOrderId = new HashMap<>();
                orderItemRepository.findAllByOrder_IdInOrderByIdAsc(returnOrderIds)
                        .forEach(oi -> returnOrderItemsByOrderId
                                .computeIfAbsent(oi.getOrder().getId(), k -> new ArrayList<>())
                                .add(oi));

                List<UUID> accountIds = new ArrayList<>();
                returnOrdersById.values().forEach(o -> accountIds.add(o.getBuyer().getId()));
                returnOrderItemsByOrderId.values().stream()
                        .filter(list -> !list.isEmpty())
                        .map(list -> list.get(0).getProduct().getMember().getId())
                        .forEach(accountIds::add);

                Map<UUID, String> fullNameByAccountId = new HashMap<>();
                if (!accountIds.isEmpty()) {
                    memberProfileRepository.findAllByAccount_IdIn(accountIds.stream().distinct().toList())
                            .forEach(p -> fullNameByAccountId.put(p.getAccount().getId(), p.getFullName()));
                }

                returnOrdersById.forEach((orderId, order) -> {
                    buyerNameByOrderId.put(orderId, fullNameByAccountId.get(order.getBuyer().getId()));
                    List<OrderItem> orderItems = returnOrderItemsByOrderId.getOrDefault(orderId, List.of());
                    if (!orderItems.isEmpty()) {
                        UUID sellerId = orderItems.get(0).getProduct().getMember().getId();
                        sellerNameByOrderId.put(orderId, fullNameByAccountId.get(sellerId));
                    }
                });
            }
        }

        Instant now = Instant.now();
        return items.stream().map(q -> {
            if (q.getType() == QueueType.PACKING) {
                UUID orderId = parseUuid(q.getRefId());
                Order order = orderId == null ? null : ordersById.get(orderId);
                if (order == null) {
                    return QueueItemResponse.from(q);
                }
                List<OrderItem> orderItems = orderItemsByOrderId.getOrDefault(orderId, List.of());
                long waitingMinutes = Duration.between(order.getCreatedAt(), now).toMinutes();
                return QueueItemResponse.fromPacking(q, order, orderItems.size(), waitingMinutes);
            }
            if (q.getType() == QueueType.LEGAL_CHANGE) {
                UUID requestId = parseUuid(q.getRefId());
                OrganizationLegalChangeRequest request = requestId == null ? null : legalChangesById.get(requestId);
                if (request == null) {
                    return QueueItemResponse.from(q);
                }
                OrganizationLegalChangeMapper.LegalChangeSummary summary = organizationLegalChangeMapper
                        .summarize(request.getProposedChanges(), request.getOrganization());
                return QueueItemResponse.fromLegalChange(q, request,
                        summary.fieldChanged(), summary.oldValue(), summary.newValue());
            }
            if (q.getType() == QueueType.RETURN) {
                UUID returnRequestId = parseUuid(q.getRefId());
                ReturnRequest returnRequest = returnRequestId == null ? null : returnRequestsById.get(returnRequestId);
                Order order = returnRequest == null ? null : returnOrdersById.get(returnRequest.getOrder().getId());
                if (order == null) {
                    return QueueItemResponse.from(q);
                }
                String sellerName = sellerNameByOrderId.get(order.getId());
                String buyerName = buyerNameByOrderId.get(order.getId());
                return QueueItemResponse.fromReturn(q, order, sellerName, buyerName);
            }
            return QueueItemResponse.from(q);
        }).toList();
    }

    private UUID parseUuid(String refId) {
        try {
            return UUID.fromString(refId);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    @Transactional
    public QueueClaimResponse claim(String id, Account moderator) {
        Warehouse warehouse = requireWarehouse(moderator);

        QueueItem item = queueItemRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUEUE_ITEM_NOT_FOUND));

        // Việc thuộc kho khác thì coi như không tồn tại
        if (item.getWarehouse() != null && !item.getWarehouse().getId().equals(warehouse.getId())) {
            throw new AppException(ErrorCode.QUEUE_ITEM_NOT_FOUND);
        }

        Instant now = Instant.now();
        int updated = queueItemRepository.claimIfFree(id, moderator, now);
        if (updated == 0) {
            throw new AppException(ErrorCode.QUEUE_ITEM_ALREADY_CLAIMED);
        }
        return QueueClaimResponse.builder()
                .id(id)
                .claimedBy(moderator.getId().toString())
                .claimedAt(now)
                .build();
    }

    @Override
    @Transactional
    public void release(String id, Account moderator) {
        QueueItem item = queueItemRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.QUEUE_ITEM_NOT_FOUND));

        if (item.getClaimedBy() == null || !item.getClaimedBy().getId().equals(moderator.getId())) {
            throw new AppException(ErrorCode.QUEUE_ITEM_NOT_CLAIMED_BY_YOU);
        }
        item.setClaimedBy(null);
        item.setClaimedAt(null);
        queueItemRepository.save(item);
    }

    @Override
    @Transactional
    public QueueItem enqueue(QueueType type, String refId, Warehouse warehouse) {
        return queueItemRepository.findByTypeAndRefId(type, refId)
                .orElseGet(() -> queueItemRepository.save(
                        QueueItem.builder().type(type).refId(refId).warehouse(warehouse).build()));
    }

    @Override
    @Transactional
    public void dequeue(QueueType type, String refId) {
        queueItemRepository.findByTypeAndRefId(type, refId).ifPresent(queueItemRepository::delete);
    }

    private Warehouse requireWarehouse(Account moderator) {
        if (moderator.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);
        }
        return moderator.getWarehouse();
    }

    private QueueType parseType(String type) {
        try {
            return QueueType.valueOf(type.trim());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_QUEUE_TYPE);
        }
    }
}