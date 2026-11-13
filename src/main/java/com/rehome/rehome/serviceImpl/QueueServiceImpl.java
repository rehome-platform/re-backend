package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.QueueClaimResponse;
import com.rehome.rehome.dto.response.QueueItemResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationLegalChangeRequest;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.Donation;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.marketplace.QueueItem;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.OrganizationLegalChangeMapper;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.DonationRepository;
import com.rehome.rehome.repository.ItemWithdrawalRequestRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.OrganizationLegalChangeRequestRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.QueueItemRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.service.QueueService;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private final ConsignmentFormRepository consignmentFormRepository;
    private final OrganizationProfileRepository organizationProfileRepository;
    private final ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    private final ProductPhotoRepository productPhotoRepository;
    private final DonationRepository donationRepository;

    // Không đặt readOnly: tab RETURN có thể phải bù dòng hàng đợi còn thiếu (syncPendingReturnRequests)
    @Override
    @Transactional
    public List<QueueItemResponse> list(String type, Account moderator) {
        Warehouse warehouse = requireWarehouse(moderator);

        List<QueueItem> items;
        if (type == null || type.isBlank()) {
            items = queueItemRepository.findAllVisible(warehouse);
        } else {
            QueueType queueType = parseType(type);
            if (queueType == QueueType.RETURN) {
                syncPendingReturnRequests(warehouse);
            }
            items = queueItemRepository.findVisibleByType(queueType, warehouse);
        }
        return toResponses(items);
    }

    // Yêu cầu trả hàng PENDING tạo trước khi có hàng đợi (hoặc bị sót lúc tạo) không có dòng trong queue_items
    // nên tab Trả hàng bị rỗng. Bù các dòng còn thiếu cho kho của Moderator; đã có dòng thì giữ nguyên.
    private void syncPendingReturnRequests(Warehouse warehouse) {
        List<String> pendingIds = returnRequestRepository.findPendingIdsByWarehouse(warehouse).stream()
                .map(UUID::toString)
                .toList();
        if (pendingIds.isEmpty()) {
            return;
        }
        Set<String> queuedRefIds = new HashSet<>();
        queueItemRepository.findByTypesAndRefIds(List.of(QueueType.RETURN), pendingIds)
                .forEach(q -> queuedRefIds.add(q.getRefId()));
        for (String refId : pendingIds) {
            if (!queuedRefIds.contains(refId)) {
                enqueue(QueueType.RETURN, refId, warehouse);
            }
        }
    }

    // DG-01: QueueItemResponse của PACKING cần kèm dữ liệu đơn hàng (mã đơn, người nhận,
    // số món, vị trí kệ, thời gian chờ); LEGAL_CHANGE kèm nội dung xin đổi (xem bên dưới);
    // FORM_REVIEW / INSPECTION / ORG_VERIFICATION / WITHDRAWAL kèm targetName + subjectSubtitle;
    // WITHDRAWAL kèm thêm sku, ảnh, phí, hạn lấy hàng; DONATION_PICKUP kèm sku, chiến dịch, tổ chức, status.
    // Mọi dữ liệu nạp theo lô từ danh sách refId nên số truy vấn cố định, không phụ thuộc số dòng hàng đợi.
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

        // Nhánh ký gửi: FORM_REVIEW / INSPECTION cùng trỏ tới id form ký gửi
        List<UUID> consignmentFormIds = items.stream()
                .filter(i -> i.getType() == QueueType.FORM_REVIEW || i.getType() == QueueType.INSPECTION)
                .map(i -> parseUuid(i.getRefId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, ConsignmentForm> formsById = new HashMap<>();
        Map<UUID, String> formMemberNameByAccountId = new HashMap<>();
        if (!consignmentFormIds.isEmpty()) {
            consignmentFormRepository.findAllById(consignmentFormIds).forEach(f -> formsById.put(f.getId(), f));

            // getId() trên proxy lazy không kích hoạt query
            List<UUID> memberIds = formsById.values().stream()
                    .map(f -> f.getMember().getId())
                    .distinct()
                    .toList();
            if (!memberIds.isEmpty()) {
                memberProfileRepository.findAllByAccount_IdIn(memberIds)
                        .forEach(p -> formMemberNameByAccountId.put(p.getAccountId(), p.getFullName()));
            }
        }

        // Nhánh tổ chức: ORG_VERIFICATION có refId là accountId của tổ chức; người đại diện là
        // @Embedded trên OrganizationProfile nên không phát sinh truy vấn thêm
        List<UUID> organizationIds = items.stream()
                .filter(i -> i.getType() == QueueType.ORG_VERIFICATION)
                .map(i -> parseUuid(i.getRefId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, OrganizationProfile> organizationsById = new HashMap<>();
        if (!organizationIds.isEmpty()) {
            organizationProfileRepository.findAllById(organizationIds)
                    .forEach(o -> organizationsById.put(o.getAccountId(), o));
        }

        // Nhánh rút hàng: WITHDRAWAL có refId là id yêu cầu rút hàng; tên món lấy từ product (fetch sẵn),
        // tên và mã người ký gửi lấy theo lô từ MemberProfile (getId() trên proxy lazy không kích hoạt query)
        List<UUID> withdrawalIds = items.stream()
                .filter(i -> i.getType() == QueueType.WITHDRAWAL)
                .map(i -> parseUuid(i.getRefId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, ItemWithdrawalRequest> withdrawalsById = new HashMap<>();
        Map<UUID, MemberProfile> withdrawalMemberByAccountId = new HashMap<>();
        if (!withdrawalIds.isEmpty()) {
            itemWithdrawalRequestRepository.findAllWithProductByIdIn(withdrawalIds)
                    .forEach(w -> withdrawalsById.put(w.getId(), w));

            List<UUID> withdrawalMemberIds = withdrawalsById.values().stream()
                    .map(w -> w.getMember().getId())
                    .distinct()
                    .toList();
            if (!withdrawalMemberIds.isEmpty()) {
                memberProfileRepository.findAllByAccount_IdIn(withdrawalMemberIds)
                        .forEach(p -> withdrawalMemberByAccountId.put(p.getAccountId(), p));
            }
        }

        // Ảnh đại diện món cần rút: nạp 1 lô cho mọi sản phẩm, lấy ảnh đầu tiên theo displayOrder
        Map<String, String> withdrawalImageByProductId = new HashMap<>();
        if (!withdrawalsById.isEmpty()) {
            List<String> withdrawalProductIds = withdrawalsById.values().stream()
                    .map(w -> w.getProduct().getId())
                    .distinct()
                    .toList();
            for (ProductPhoto photo : productPhotoRepository
                    .findByProduct_IdInOrderByDisplayOrderAscIdAsc(withdrawalProductIds)) {
                withdrawalImageByProductId.putIfAbsent(photo.getProduct().getId(), photo.getImageUrl());
            }
        }

        // Nhánh quyên góp: DONATION_PICKUP có refId là id Donation; product, chiến dịch, tổ chức fetch sẵn theo lô
        List<UUID> donationIds = items.stream()
                .filter(i -> i.getType() == QueueType.DONATION_PREP || i.getType() == QueueType.DONATION_PICKUP)
                .map(i -> parseUuid(i.getRefId()))
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<UUID, Donation> donationsById = new HashMap<>();
        if (!donationIds.isEmpty()) {
            donationRepository.findAllWithDetailsByIdIn(donationIds)
                    .forEach(d -> donationsById.put(d.getId(), d));
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
            if (q.getType() == QueueType.FORM_REVIEW || q.getType() == QueueType.INSPECTION) {
                UUID formId = parseUuid(q.getRefId());
                ConsignmentForm form = formId == null ? null : formsById.get(formId);
                if (form == null) {
                    return QueueItemResponse.from(q);
                }
                String memberName = formMemberNameByAccountId.get(form.getMember().getId());
                return QueueItemResponse.fromConsignmentForm(q, form, memberName);
            }
            if (q.getType() == QueueType.ORG_VERIFICATION) {
                UUID organizationId = parseUuid(q.getRefId());
                OrganizationProfile organization = organizationId == null ? null : organizationsById.get(organizationId);
                if (organization == null) {
                    return QueueItemResponse.from(q);
                }
                return QueueItemResponse.fromOrganization(q, organization);
            }
            if (q.getType() == QueueType.WITHDRAWAL) {
                UUID withdrawalId = parseUuid(q.getRefId());
                ItemWithdrawalRequest withdrawal = withdrawalId == null ? null : withdrawalsById.get(withdrawalId);
                if (withdrawal == null) {
                    return QueueItemResponse.from(q);
                }
                MemberProfile memberProfile = withdrawalMemberByAccountId.get(withdrawal.getMember().getId());
                String memberName = memberProfile == null ? null : memberProfile.getFullName();
                String memberCode = memberProfile == null ? null : memberProfile.getMemberCode();
                String productImageUrl = withdrawalImageByProductId.get(withdrawal.getProduct().getId());
                return QueueItemResponse.fromWithdrawal(q, withdrawal, memberName, memberCode, productImageUrl);
            }
            if (q.getType() == QueueType.DONATION_PREP || q.getType() == QueueType.DONATION_PICKUP) {
                UUID donationId = parseUuid(q.getRefId());
                Donation donation = donationId == null ? null : donationsById.get(donationId);
                if (donation == null) {
                    return QueueItemResponse.from(q);
                }
                return QueueItemResponse.fromDonationPickup(q, donation);
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