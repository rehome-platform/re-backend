package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.WarehouseAuditGroup;
import com.rehome.rehome.dto.request.CounterHandoverRequest;
import com.rehome.rehome.dto.request.CounterLookupRequest;
import com.rehome.rehome.dto.response.MemberLookupItemResponse;
import com.rehome.rehome.dto.response.MemberTasksResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.counter.CounterIdentityConfirmation;
import com.rehome.rehome.entity.enums.*;
import com.rehome.rehome.entity.marketplace.DegradedItemCase;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.*;
import com.rehome.rehome.service.CounterService;
import com.rehome.rehome.service.QueueService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CounterServiceImpl implements CounterService {

    private static final Duration IDENTITY_CONFIRMATION_TTL = Duration.ofMinutes(15);
    private static final List<ItemWithdrawalStatus> ACTIVE_WITHDRAWAL_STATUSES =
            List.of(ItemWithdrawalStatus.PENDING_APPROVAL, ItemWithdrawalStatus.PENDING_PICKUP);
    private final MemberProfileRepository memberProfileRepository;
    private final ConsignmentFormRepository consignmentFormRepository;
    private final ConsignmentReceiptRepository consignmentReceiptRepository;
    private final ProductRepository productRepository;
    private final ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    private final EndOfTermDecisionRepository endOfTermDecisionRepository;
    private final AccountRepository accountRepository;
    private final CounterIdentityConfirmationRepository identityConfirmationRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final DegradedItemCaseRepository degradedItemCaseRepository;
    private final OrderItemRepository orderItemRepository;
    private final AuditLogRepository auditLogRepository;
    private final com.rehome.rehome.component.AuditLogger auditLogger;
    private final QueueService queueService;

    private static final List<ConsignmentFormStatus> OPEN_FORM_STATUSES =
            List.of(ConsignmentFormStatus.PENDING, ConsignmentFormStatus.APPROVED, ConsignmentFormStatus.INSPECTING);

    private static final List<ConsignmentFormStatus> ACTIVE_CONSIGNMENT_STATUSES = List.of(
            ConsignmentFormStatus.PENDING, ConsignmentFormStatus.APPROVED, ConsignmentFormStatus.INSPECTING);

    private static final String BAN_ACTION_TYPE = "BAN_ACCOUNT";
    private static final String ACCOUNT_TARGET_TYPE = "ACCOUNT";

    @Override
    @Transactional
    public void confirmIdentity(Account moderator, UUID accountId) {
        Account member = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        Instant now = Instant.now();
        CounterIdentityConfirmation confirmation = identityConfirmationRepository.findById(member.getId())
                .orElseGet(() -> CounterIdentityConfirmation.builder().accountId(member.getId()).build());

        confirmation.setConfirmedAt(now);
        confirmation.setExpiresAt(now.plus(IDENTITY_CONFIRMATION_TTL));
        confirmation.setConfirmedBy(moderator);

        identityConfirmationRepository.save(confirmation);
    }

    @Override
    @Transactional
    public void handover(Account moderator, CounterHandoverRequest request) {
        CounterHandoverContext context = parseContext(request.getContext());

        ensureIdentityConfirmed(request.getAccountId());

        UUID refId = parseRefId(request.getRefId());

        switch (context) {
            case ITEM_WITHDRAWAL -> handoverItemWithdrawal(moderator, request.getAccountId(), refId);
            case END_OF_TERM_TAKE_BACK -> handoverEndOfTermTakeBack(moderator, request.getAccountId(), refId);
            case INVALID_RETURN_PICKUP -> handoverInvalidReturnPickup(moderator, request.getAccountId(), refId);
            case DEGRADED_ITEM_PICKUP -> handoverDegradedItemPickup(moderator, request.getAccountId(), refId);
        }
    }

    private CounterHandoverContext parseContext(String raw) {
        try {
            return CounterHandoverContext.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AppException(ErrorCode.COUNTER_CONTEXT_INVALID);
        }
    }

    private UUID parseRefId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.COUNTER_RECORD_NOT_FOUND);
        }
    }

    private void ensureIdentityConfirmed(UUID accountId) {
        CounterIdentityConfirmation confirmation = identityConfirmationRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.COUNTER_IDENTITY_NOT_CONFIRMED));

        if (confirmation.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(ErrorCode.COUNTER_IDENTITY_NOT_CONFIRMED);
        }
    }

    private void handoverItemWithdrawal(Account moderator, UUID accountId, UUID refId) {
        ItemWithdrawalRequest withdrawal = itemWithdrawalRequestRepository.findById(refId)
                .filter(r -> r.getMember().getId().equals(accountId))
                .filter(r -> r.getStatus() == ItemWithdrawalStatus.PENDING_PICKUP)
                .orElseThrow(() -> new AppException(ErrorCode.COUNTER_RECORD_NOT_FOUND));

        if (withdrawal.getFeeCharged() != null && withdrawal.getFeeCharged().signum() > 0
                && withdrawal.getFeePaidAt() == null) {
            throw new AppException(ErrorCode.WITHDRAWAL_FEE_NOT_PAID);
        }

        withdrawal.setStatus(ItemWithdrawalStatus.COMPLETED);
        withdrawal.setCompletedAt(Instant.now());
        itemWithdrawalRequestRepository.save(withdrawal);
        var product = productRepository.findByIdForUpdate(withdrawal.getProduct().getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.getStatus() != ProductStatus.WITHDRAWN) {
            product.setStatus(ProductStatus.WITHDRAWN);
            productRepository.save(product);
        }
        auditLogger.logOperation(moderator, "HANDOVER_WITHDRAWAL", "WITHDRAWAL", refId, "COMPLETED");

        queueService.dequeue(QueueType.WITHDRAWAL, withdrawal.getId().toString());
    }

    private void handoverEndOfTermTakeBack(Account moderator, UUID accountId, UUID refId) {
        EndOfTermDecision decision = endOfTermDecisionRepository.findById(refId)
                .filter(d -> d.getProduct().getMember().getId().equals(accountId))
                .filter(d -> d.getDecision() == EndOfTermChoice.TAKE_BACK)
                .filter(d -> d.getTakenBackAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.COUNTER_RECORD_NOT_FOUND));

        decision.setTakenBackAt(Instant.now());
        endOfTermDecisionRepository.save(decision);

        auditLogger.logWarehouseOp(moderator, decision.getProduct().getWarehouse(), WarehouseAuditGroup.INVENTORY,
                "HANDOVER_END_OF_TERM", "END_OF_TERM_DECISION", decision.getId().toString(), "HANDED_OVER",
                decision.getProduct().getId());
    }

    private void handoverInvalidReturnPickup(Account moderator, UUID accountId, UUID refId) {
        ReturnRequest returnRequest = returnRequestRepository.findById(refId)
                .filter(r -> r.getOrder().getBuyer().getId().equals(accountId))
                .filter(r -> r.getStatus() == ReturnRequestStatus.INVALID)
                .filter(r -> r.getInvalidReturnChoice() == InvalidReturnChoice.WAREHOUSE_PICKUP)
                .filter(r -> r.getPickedUpAt() == null)
                .orElseThrow(() -> new AppException(ErrorCode.COUNTER_RECORD_NOT_FOUND));

        Instant now = Instant.now();
        returnRequest.setPickedUpAt(now);
        returnRequest.setResolvedAt(now);
        returnRequest.setResolutionNote("Người mua đã nhận lại hàng tại quầy");
        returnRequestRepository.save(returnRequest);

        com.rehome.rehome.entity.marketplace.Warehouse warehouse = returnRequest.getOrder().getWarehouse() != null
                ? returnRequest.getOrder().getWarehouse()
                : (moderator == null ? null : moderator.getWarehouse());
        if (warehouse != null) {
            auditLogger.logWarehouseOp(moderator, warehouse, WarehouseAuditGroup.INVENTORY,
                    "HANDOVER_INVALID_RETURN", "RETURN_REQUEST", returnRequest.getId().toString(), "HANDED_OVER",
                    returnRequest.getOrder().getOrderNo());
        } else {
            auditLogger.logOperation(moderator, "HANDOVER_INVALID_RETURN", "RETURN_REQUEST",
                    returnRequest.getId(), "HANDED_OVER");
        }
    }

    private void handoverDegradedItemPickup(Account moderator, UUID accountId, UUID refId) {
        DegradedItemCase degradedCase = degradedItemCaseRepository.findById(refId)
                .filter(c -> c.getProduct().getMember().getId().equals(accountId))
                .filter(c -> c.getChoice() == DegradedItemChoice.PARTIAL_WITH_ITEM_BACK)
                .filter(c -> c.getPickedUpAt() == null && !c.isItemDonated())
                .orElseThrow(() -> new AppException(ErrorCode.COUNTER_RECORD_NOT_FOUND));

        degradedCase.setPickedUpAt(Instant.now());
        degradedItemCaseRepository.save(degradedCase);

        auditLogger.logWarehouseOp(moderator, degradedCase.getProduct().getWarehouse(), WarehouseAuditGroup.INVENTORY,
                "HANDOVER_DEGRADED_ITEM", "DEGRADED_ITEM_CASE", degradedCase.getId().toString(), "HANDED_OVER",
                degradedCase.getProduct().getId());
    }

    @Override
    @Transactional
    public List<MemberLookupItemResponse> lookup(Account moderator, CounterLookupRequest req) {
        String memberCode = trimToNull(req.getMemberCode());
        String phone = trimToNull(req.getPhone());
        String qrPayload = trimToNull(req.getQrPayload());

        int provided = (memberCode != null ? 1 : 0) + (phone != null ? 1 : 0) + (qrPayload != null ? 1 : 0);
        if (provided != 1) {
            throw new AppException(ErrorCode.LOOKUP_INVALID_INPUT);
        }
        if (moderator.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);
        }
        UUID warehouseId = moderator.getWarehouse().getId();

        List<MemberProfile> found;
        if (phone != null) {
            found = memberProfileRepository.findByAccount_Phone(phone);
        } else {
            String code = memberCode != null ? memberCode : qrPayload;
            found = memberProfileRepository.findByMemberCode(code).map(List::of).orElse(List.of());
        }

        if (found.isEmpty()) {
            throw new AppException(ErrorCode.MEMBER_NOT_FOUND);
        }

        List<UUID> accountIds = found.stream().map(MemberProfile::getAccountId).toList();
        Map<UUID, Instant> lastVisits = loadLastWarehouseVisits(accountIds);
        Map<UUID, ViolationAggregate> violations = loadViolationAggregates(accountIds);
        Map<UUID, List<MemberLookupItemResponse.ActiveConsignmentItem>> consignments =
                loadActiveConsignments(accountIds, warehouseId);

        return found.stream().map(p -> {
            ViolationAggregate agg = violations.get(p.getAccountId());
            MemberLookupItemResponse.ViolationSummary summary = MemberLookupItemResponse.ViolationSummary
                    .fromAccount(p.getAccount(),
                            agg == null ? 0 : agg.total(),
                            agg == null ? null : agg.lastAt());
            return MemberLookupItemResponse.from(p,
                    lastVisits.get(p.getAccountId()),
                    summary,
                    consignments.getOrDefault(p.getAccountId(), List.of()));
        }).toList();
    }

    private Map<UUID, Instant> loadLastWarehouseVisits(Collection<UUID> accountIds) {
        Map<UUID, Instant> result = new HashMap<>();
        identityConfirmationRepository.findAllById(accountIds)
                .forEach(c -> result.put(c.getAccountId(), c.getConfirmedAt()));
        return result;
    }

    private record ViolationAggregate(long total, Instant lastAt) {}

    private Map<UUID, ViolationAggregate> loadViolationAggregates(Collection<UUID> accountIds) {
        Map<UUID, ViolationAggregate> result = new HashMap<>();
        List<String> targetIds = accountIds.stream().map(UUID::toString).toList();
        for (Object[] row : auditLogRepository.summarizeByTarget(BAN_ACTION_TYPE, ACCOUNT_TARGET_TYPE, targetIds)) {
            result.put(UUID.fromString((String) row[0]),
                    new ViolationAggregate(((Number) row[1]).longValue(), (Instant) row[2]));
        }
        return result;
    }

    private Map<UUID, List<MemberLookupItemResponse.ActiveConsignmentItem>> loadActiveConsignments(
            Collection<UUID> accountIds, UUID warehouseId) {
        return consignmentFormRepository
                .findByMember_IdInAndWarehouse_IdAndStatusInOrderByCreatedAtAsc(
                        accountIds, warehouseId, ACTIVE_CONSIGNMENT_STATUSES)
                .stream()
                .collect(Collectors.groupingBy(f -> f.getMember().getId(),
                        Collectors.mapping(MemberLookupItemResponse.ActiveConsignmentItem::from,
                                Collectors.toList())));
    }

    @Override
    @Transactional
    public MemberTasksResponse getTasks(UUID accountId) {
        if (!memberProfileRepository.existsById(accountId)) {
            throw new AppException(ErrorCode.MEMBER_NOT_FOUND);
        }

        var pendingForms = consignmentFormRepository
                .findByMember_IdAndStatusInOrderByCreatedAtAsc(accountId, OPEN_FORM_STATUSES);
        var formIds = pendingForms.stream().map(f -> f.getId()).toList();
        Map<UUID, com.rehome.rehome.entity.marketplace.ConsignmentReceipt> receipts = new HashMap<>();
        Map<UUID, String> productIds = new HashMap<>();
        Map<String, com.rehome.rehome.entity.marketplace.Product> products = new HashMap<>();
        if (!formIds.isEmpty()) {
            for (var receipt : consignmentReceiptRepository.findLatestByFormIds(formIds)) {
                receipts.merge(receipt.getConsignmentForm().getId(), receipt,
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b);
            }
            productRepository.findProductIdsByFormIds(formIds)
                    .forEach(p -> productIds.put(p.getFormId(), p.getProductId()));
            if (!productIds.isEmpty()) {
                productRepository.findAllById(productIds.values()).forEach(p -> products.put(p.getId(), p));
            }
        }
        List<String> withdrawalProductIds = products.values().stream()
                .filter(p -> p.getStatus() == ProductStatus.WAREHOUSE || p.getStatus() == ProductStatus.WITHDRAWN)
                .map(com.rehome.rehome.entity.marketplace.Product::getId).toList();
        Map<String, ItemWithdrawalRequest> activeWithdrawals = withdrawalProductIds.isEmpty() ? Map.of()
                : itemWithdrawalRequestRepository.findByProductIdsAndStatusIn(
                        withdrawalProductIds, ACTIVE_WITHDRAWAL_STATUSES).stream()
                        .collect(Collectors.toMap(w -> w.getProduct().getId(), w -> w, (first, second) -> first));
        List<MemberTasksResponse.PendingFormItem> forms = pendingForms.stream()
                .map(f -> {
                    var receipt = receipts.get(f.getId());
                    var product = products.get(productIds.get(f.getId()));
                    var activeWithdrawal = product == null ? null : activeWithdrawals.get(product.getId());
                    var step = ModeratorConsignmentFormServiceImpl.resolveResumeStep(f, receipt, product != null);
                    return MemberTasksResponse.PendingFormItem.builder()
                        .id(f.getId())
                        .productName(f.getProductName())
                        .desiredPrice(f.getDesiredPrice())
                        .status(f.getStatus())
                        .createdAt(f.getCreatedAt())
                        .receiptId(receipt == null ? null : receipt.getId())
                        .confirmedAt(receipt == null ? null : receipt.getConfirmedAt())
                        .resumeStep(step)
                        .productSku(product == null ? null : product.getId())
                        .productStatus(product == null ? null : product.getStatus())
                        .activeWithdrawalId(activeWithdrawal == null ? null : activeWithdrawal.getId())
                        .activeWithdrawalStatus(activeWithdrawal == null ? null : activeWithdrawal.getStatus())
                        .displayStatus(product == null ? step.name() : product.getStatus().name())
                        .build();
                })
                .toList();

        List<MemberTasksResponse.PendingWithdrawalItem> withdrawals = itemWithdrawalRequestRepository
                .findByMember_IdAndStatusOrderByCreatedAtAsc(accountId, ItemWithdrawalStatus.PENDING_PICKUP)
                .stream()
                .map(w -> MemberTasksResponse.PendingWithdrawalItem.builder()
                        .id(w.getId())
                        .productId(w.getProduct().getId())
                        .productName(w.getProduct().getName())
                        .feeCharged(w.getFeeCharged())
                        .pickupDeadline(w.getPickupDeadline())
                        .build())
                .toList();

        List<MemberTasksResponse.PendingEndOfTermItem> endOfTerm =
                endOfTermDecisionRepository.findPendingTakeBackByMemberId(accountId).stream()
                        .map(d -> MemberTasksResponse.PendingEndOfTermItem.builder()
                                .id(d.getId())
                                .productId(d.getProduct().getId())
                                .productName(d.getProduct().getName())
                                .deadline(d.getDeadline())
                                .build())
                        .toList();

        List<MemberTasksResponse.PendingDegradedItemItem> degradedItems =
                degradedItemCaseRepository.findPendingPickupByMemberId(accountId).stream()
                        .map(c -> MemberTasksResponse.PendingDegradedItemItem.builder()
                                .id(c.getId())
                                .productId(c.getProduct().getId())
                                .productName(c.getProduct().getName())
                                .paidAmount(c.getPaidAmount())
                                .decidedAt(c.getDecidedAt())
                                .build())
                        .toList();

        List<MemberTasksResponse.PendingInvalidReturnItem> invalidReturns = buildPendingInvalidReturns(accountId);

        return MemberTasksResponse.builder()
                .pendingForms(forms)
                .pendingWithdrawals(withdrawals)
                .pendingEndOfTerm(endOfTerm)
                .pendingDegradedItems(degradedItems)
                .pendingInvalidReturns(invalidReturns)
                .build();
    }

    private List<MemberTasksResponse.PendingInvalidReturnItem> buildPendingInvalidReturns(UUID accountId) {
        List<ReturnRequest> invalidReturns = returnRequestRepository
                .findRecentInvalidByBuyerId(accountId, Instant.now().minus(7, ChronoUnit.DAYS))
                .stream()
                .filter(r -> r.getPickedUpAt() == null && r.getResolvedAt() == null)
                .filter(r -> r.getShipBackStatus() != ShipBackStatus.PAID
                        && r.getShipBackStatus() != ShipBackStatus.SHIPPING
                        && r.getShipBackStatus() != ShipBackStatus.DELIVERED)
                .toList();
        if (invalidReturns.isEmpty()) {
            return List.of();
        }

        List<UUID> orderIds = invalidReturns.stream().map(r -> r.getOrder().getId()).toList();
        Map<UUID, OrderItem> firstItemByOrderId = new HashMap<>();
        for (OrderItem item : orderItemRepository.findAllByOrder_IdInOrderByIdAsc(orderIds)) {
            firstItemByOrderId.putIfAbsent(item.getOrder().getId(), item);
        }

        return invalidReturns.stream()
                .map(r -> {
                    OrderItem item = firstItemByOrderId.get(r.getOrder().getId());
                    return MemberTasksResponse.PendingInvalidReturnItem.builder()
                            .id(r.getId())
                            .productId(item == null ? null : item.getProduct().getId())
                            .productName(item == null ? null : item.getProduct().getName())
                            .pickupDeadline(r.getPickupDeadline())
                            .createdAt(r.getCreatedAt())
                            .build();
                })
                .toList();
    }

    private static String trimToNull(String s) {
        return Optional.ofNullable(s).map(String::trim).filter(v -> !v.isEmpty()).orElse(null);
    }
}
