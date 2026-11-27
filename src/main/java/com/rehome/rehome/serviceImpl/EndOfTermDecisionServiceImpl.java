package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.NotificationTargetType;

import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.component.ReceiptClauseRenderer;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.enums.ReConsignCounterResponseAction;
import com.rehome.rehome.entity.enums.ReConsignNegotiationAction;
import com.rehome.rehome.entity.enums.ReConsignStatus;
import com.rehome.rehome.entity.enums.WarehouseAuditGroup;
import com.rehome.rehome.config.ConsignmentProperties;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.request.DecideEndOfTermRequest;
import com.rehome.rehome.dto.request.ReConsignCounterOfferRequest;
import com.rehome.rehome.dto.request.ReConsignCounterResponseRequest;
import com.rehome.rehome.dto.request.ReConsignDeclineRequest;
import com.rehome.rehome.dto.request.ReConsignTermsRequest;
import com.rehome.rehome.dto.response.DecideEndOfTermResponse;
import com.rehome.rehome.dto.response.PendingReConsignResponse;
import com.rehome.rehome.dto.response.ReConsignCounterOfferResponse;
import com.rehome.rehome.dto.response.ReConsignTermsResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ReConsignNegotiationHistory;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.ReConsignNegotiationHistoryRepository;
import com.rehome.rehome.service.ContentModerationService;
import com.rehome.rehome.service.EndOfTermDecisionService;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.service.SystemConfigReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EndOfTermDecisionServiceImpl implements EndOfTermDecisionService {
    private final com.rehome.rehome.component.BusinessNotifications businessNotifications;

    private final AuditLogger auditLogger;
    private final EndOfTermDecisionRepository decisionRepository;
    private final ProductRepository productRepository;
    private final ConsignmentReceiptRepository consignmentReceiptRepository;
    private final ReConsignNegotiationHistoryRepository negotiationHistoryRepository;
    private final ConsignmentProperties consignmentProperties;
    private final ReceiptClauseRenderer receiptClauseRenderer;
    private final SystemConfigReader systemConfigReader;
    private final QueueService queueService;
    private final AccountRepository accountRepository;
    private final ContentModerationService contentModerationService;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Override
    @Transactional
    public EndOfTermDecision createDecision(Product product, Instant now) {
        // Chống tạo trùng: sản phẩm đã có quyết định đang chờ thì dùng lại
        return decisionRepository.findFirstByProduct_IdAndDecisionIsNullOrderByDeadlineDesc(product.getId())
                .orElseGet(() -> {
                    int days = systemConfigReader.getInt(SystemConfigKeys.END_OF_TERM_DECISION_DAYS,
                            SystemConfigKeys.DEFAULT_END_OF_TERM_DECISION_DAYS);
                    Instant deadline = now.plus(Duration.ofDays(days));

                    product.setUnclaimedDecisionDeadline(deadline);
                    productRepository.save(product);

                    businessNotifications.product(product, NotificationType.TERM_EXPIRY, "Món ký gửi đã hết kỳ",
                            "vui lòng chọn nhận lại hoặc ký gửi lại trước hạn " + deadline + ".");
                    return decisionRepository.save(EndOfTermDecision.builder()
                            .product(product)
                            .deadline(deadline)
                            .build());
                });
    }

    @Override
    @Transactional
    public DecideEndOfTermResponse decide(Account account, UUID decisionId, DecideEndOfTermRequest request) {

        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }

        EndOfTermChoice choice = request.getDecision();
        if (choice != EndOfTermChoice.TAKE_BACK && choice != EndOfTermChoice.RE_CONSIGN) {
            throw new AppException(ErrorCode.INVALID_DECISION);                                        // 400
        }

        // Khóa dòng để chống bấm đồng thời
        EndOfTermDecision decision = decisionRepository.findByIdForUpdate(decisionId)
                .orElseThrow(() -> new AppException(ErrorCode.END_OF_TERM_DECISION_NOT_FOUND));       // 404

        Product product = decision.getProduct();

        if (!product.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.END_OF_TERM_DECISION_FORBIDDEN);                          // 403
        }

        // Job đã đánh dấu vô thừa nhận
        if (decision.getDecision() == EndOfTermChoice.UNCLAIMED) {
            throw new AppException(ErrorCode.DECISION_EXPIRED);                                        // 409
        }
        if (decision.getDecision() != null) {
            throw new AppException(ErrorCode.END_OF_TERM_DECISION_ALREADY_DECIDED);                    // 409
        }

        Instant now = Instant.now();
        // Quá hạn nhưng job chưa kịp chạy
        if (now.isAfter(decision.getDeadline())) {
            throw new AppException(ErrorCode.DECISION_EXPIRED);                                        // 409
        }

        if (choice == EndOfTermChoice.TAKE_BACK) {
            product.setStatus(ProductStatus.WITHDRAWN);
            productRepository.save(product);
        } else {
            // RE_CONSIGN: chưa tạo biên nhận. Moderator chốt giá và điều khoản ở setReConsignTerms,
            // lúc đó biên nhận mới mới được tạo và chờ Member xác nhận.
            decision.setReConsignStatus(ReConsignStatus.AWAITING_PRICE);
        }

        decision.setDecision(choice);
        decision.setDecidedAt(now);
        decisionRepository.save(decision);

        auditLogger.logWarehouseOp(account, product.getWarehouse(), WarehouseAuditGroup.INVENTORY,
                "END_OF_TERM_DECISION", "END_OF_TERM_DECISION", decision.getId().toString(), choice.name(),
                product.getId());

        return new DecideEndOfTermResponse(choice, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingReConsignResponse> listPendingPricing(Account moderator) {
        requireWarehouse(moderator);
        UUID warehouseId = moderator.getWarehouse().getId();
        int moderatorResponseDays = systemConfigReader.getInt(SystemConfigKeys.RE_CONSIGN_MODERATOR_RESPONSE_DAYS,
                SystemConfigKeys.DEFAULT_RE_CONSIGN_MODERATOR_RESPONSE_DAYS);
        Instant now = Instant.now();

        List<PendingReConsignResponse> result = new ArrayList<>();

        decisionRepository.findPendingPricingByWarehouse(warehouseId).forEach(d -> {
            Product p = d.getProduct();
            PendingReConsignResponse row = new PendingReConsignResponse();
            row.setDecisionId(d.getId());
            row.setProductId(p.getId());
            row.setProductName(p.getName());
            row.setCurrentPrice(p.getCurrentPrice());
            row.setPreviousAgreedPrice(p.getConsignmentReceipt().getAgreedPrice());
            row.setDecidedAt(d.getDecidedAt());
            row.setReConsignStatus(ReConsignStatus.AWAITING_PRICE);
            result.add(row);
        });

        decisionRepository.findCounterOfferedByWarehouse(warehouseId).forEach(d -> {
            Product p = d.getProduct();
            Instant responseDeadline = d.getCounteredAt() == null ? null
                    : d.getCounteredAt().plus(Duration.ofDays(moderatorResponseDays));
            PendingReConsignResponse row = new PendingReConsignResponse();
            row.setDecisionId(d.getId());
            row.setProductId(p.getId());
            row.setProductName(p.getName());
            row.setCurrentPrice(p.getCurrentPrice());
            row.setPreviousAgreedPrice(d.getNewReceipt().getAgreedPrice());
            row.setDecidedAt(d.getDecidedAt());
            row.setReConsignStatus(ReConsignStatus.COUNTER_OFFERED);
            row.setMemberCounterPrice(d.getMemberCounterPrice());
            row.setMemberCounterNote(d.getMemberCounterNote());
            row.setCounteredAt(d.getCounteredAt());
            row.setModeratorResponseDeadline(responseDeadline);
            row.setOverdue(responseDeadline != null && now.isAfter(responseDeadline));
            result.add(row);
        });

        return result;
    }

    @Override
    @Transactional
    public ReConsignTermsResponse setReConsignTerms(Account moderator, UUID decisionId,
                                                    ReConsignTermsRequest request) {
        requireWarehouse(moderator);

        // Khóa dòng để hai Moderator không cùng chốt một yêu cầu (tránh tạo 2 biên nhận)
        EndOfTermDecision decision = decisionRepository.findByIdForUpdate(decisionId)
                .orElseThrow(() -> new AppException(ErrorCode.END_OF_TERM_DECISION_NOT_FOUND));       // 404

        Product product = decision.getProduct();

        // Chỉ Moderator của kho đang giữ hàng
        if (!product.getWarehouse().getId().equals(moderator.getWarehouse().getId())) {
            throw new AppException(ErrorCode.END_OF_TERM_DECISION_FORBIDDEN);                          // 403
        }
        if (decision.getDecision() != EndOfTermChoice.RE_CONSIGN) {
            throw new AppException(ErrorCode.RE_CONSIGN_NOT_REQUESTED);                                // 409
        }
        if (decision.getNewReceipt() != null) {
            throw new AppException(ErrorCode.RE_CONSIGN_TERMS_ALREADY_SET);                            // 409
        }

        ConsignmentProperties cfg = consignmentProperties;
        ConsignmentReceipt old = product.getConsignmentReceipt();

        BigDecimal agreedPrice = request.getAgreedPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal floorPrice = request.getFloorPrice() != null
                ? request.getFloorPrice().setScale(2, RoundingMode.HALF_UP)
                : agreedPrice.multiply(cfg.getFloorPriceRate()).setScale(2, RoundingMode.HALF_UP);
        if (floorPrice.signum() <= 0 || floorPrice.compareTo(agreedPrice) > 0) {
            throw new AppException(ErrorCode.INVALID_FLOOR_PRICE);                                     // 400
        }

        // Giá và điều khoản lấy từ Moderator, không sao chép từ biên nhận cũ; chưa xác nhận, chưa có PDF
        ConsignmentReceipt newReceiptDraft = ConsignmentReceipt.builder()
                .receiptNo(generateReceiptNo())
                .consignmentForm(old.getConsignmentForm())
                .member(old.getMember())
                .moderator(moderator)
                .warehouse(old.getWarehouse())
                .conditionNote(StringUtils.hasText(request.getConditionNote())
                        ? request.getConditionNote().trim() : old.getConditionNote())
                .moderatorNote(StringUtils.hasText(request.getModeratorNote())
                        ? request.getModeratorNote().trim() : null)
                .agreedPrice(agreedPrice)
                .floorPrice(floorPrice)
                .termLengthDays(request.getTermLengthDays() != null
                        ? request.getTermLengthDays() : cfg.getTermLengthDays())
                .maxRenewals(request.getMaxRenewals() != null
                        ? request.getMaxRenewals() : cfg.getMaxRenewals())
                .freeWithdrawalWindowDays(cfg.getFreeWithdrawalWindowDays())
                .commissionRateTerm1(orDefault(request.getCommissionRateTerm1(), cfg.getCommissionRateTerm1()))
                .commissionRateTerm2(orDefault(request.getCommissionRateTerm2(), cfg.getCommissionRateTerm2()))
                .commissionRateTerm3(orDefault(request.getCommissionRateTerm3(), cfg.getCommissionRateTerm3()))
                .withdrawalFee(cfg.getWithdrawalFee())
                .degradedCompensationRate(cfg.getDegradedCompensationRate())
                .confirmedAt(null)
                .build();
        newReceiptDraft.setClausesSnapshot(receiptClauseRenderer.render(newReceiptDraft));
        ConsignmentReceipt newReceipt = consignmentReceiptRepository.save(newReceiptDraft);

        Instant now = Instant.now();
        int confirmDays = systemConfigReader.getInt(SystemConfigKeys.RE_CONSIGN_CONFIRM_DAYS,
                SystemConfigKeys.DEFAULT_RE_CONSIGN_CONFIRM_DAYS);
        Instant proposalDeadline = now.plus(Duration.ofDays(confirmDays));

        decision.setNewReceipt(newReceipt);
        decision.setReConsignStatus(ReConsignStatus.PROPOSED);
        decision.setProposalRound(1);
        decision.setCounterRoundsUsed(0);
        decision.setProposalDeadline(proposalDeadline);
        decision.setProposalReminderSentAt(null);
        decisionRepository.save(decision);

        saveHistory(decision, Role.MODERATOR, ReConsignNegotiationAction.PROPOSE, agreedPrice, request.getModeratorNote());

        businessNotifications.product(product, NotificationType.TERM_EXPIRY, "ReHome đã đề xuất giá ký gửi lại",
                "giá " + agreedPrice + "đ. Vui lòng xác nhận hoặc đề xuất lại trước hạn " + proposalDeadline + ".");

        auditLogger.logWarehouseOp(moderator, product.getWarehouse(), WarehouseAuditGroup.INVENTORY,
                "PROPOSE_RECONSIGN_TERMS", "END_OF_TERM_DECISION", decision.getId().toString(), "PROPOSED",
                product.getId());

        return new ReConsignTermsResponse(decision.getId(), newReceipt.getId());
    }

    @Override
    @Transactional
    public ReConsignCounterOfferResponse counterOffer(Account member, UUID decisionId,
                                                      ReConsignCounterOfferRequest request) {
        EndOfTermDecision decision = decisionRepository.findByIdForUpdate(decisionId)
                .orElseThrow(() -> new AppException(ErrorCode.END_OF_TERM_DECISION_NOT_FOUND));

        Product product = decision.getProduct();
        if (!product.getMember().getId().equals(member.getId())) {
            throw new AppException(ErrorCode.END_OF_TERM_DECISION_FORBIDDEN);
        }
        if (decision.getReConsignStatus() != ReConsignStatus.PROPOSED) {
            throw new AppException(ErrorCode.RE_CONSIGN_NOT_PROPOSED);
        }
        Instant now = Instant.now();
        if (decision.getProposalDeadline() != null && now.isAfter(decision.getProposalDeadline())) {
            throw new AppException(ErrorCode.RE_CONSIGN_PROPOSAL_EXPIRED);
        }
        int maxCounterRounds = systemConfigReader.getInt(SystemConfigKeys.RE_CONSIGN_MAX_COUNTER_ROUNDS,
                SystemConfigKeys.DEFAULT_RE_CONSIGN_MAX_COUNTER_ROUNDS);
        if (decision.getCounterRoundsUsed() >= maxCounterRounds) {
            throw new AppException(ErrorCode.RE_CONSIGN_COUNTER_LIMIT);
        }
        if (request.getPrice() == null || request.getPrice().compareTo(new BigDecimal("10000")) < 0) {
            throw new AppException(ErrorCode.INVALID_PRICE);
        }
        contentModerationService.check(member.getId(), "RE_CONSIGN_COUNTER_OFFER", request.getNote());

        BigDecimal price = request.getPrice().setScale(2, RoundingMode.HALF_UP);
        decision.setReConsignStatus(ReConsignStatus.COUNTER_OFFERED);
        decision.setMemberCounterPrice(price);
        decision.setMemberCounterNote(request.getNote());
        decision.setCounteredAt(now);
        decision.setCounterRoundsUsed(decision.getCounterRoundsUsed() + 1);
        decisionRepository.save(decision);

        saveHistory(decision, Role.MEMBER, ReConsignNegotiationAction.COUNTER_OFFER, price, request.getNote());

        queueService.enqueue(QueueType.RECONSIGN_COUNTER, decision.getId().toString(), product.getWarehouse());

        auditLogger.logWarehouseOp(member, product.getWarehouse(), WarehouseAuditGroup.INVENTORY,
                "RE_CONSIGN_COUNTER_OFFER", "END_OF_TERM_DECISION", decision.getId().toString(),
                "COUNTER_OFFERED", product.getId());

        return new ReConsignCounterOfferResponse(decision.getId(), ReConsignStatus.COUNTER_OFFERED,
                decision.getCounterRoundsUsed(), maxCounterRounds);
    }

    @Override
    @Transactional
    public DecideEndOfTermResponse decline(Account member, UUID decisionId, ReConsignDeclineRequest request) {
        EndOfTermDecision decision = decisionRepository.findByIdForUpdate(decisionId)
                .orElseThrow(() -> new AppException(ErrorCode.END_OF_TERM_DECISION_NOT_FOUND));

        Product product = decision.getProduct();
        if (!product.getMember().getId().equals(member.getId())) {
            throw new AppException(ErrorCode.END_OF_TERM_DECISION_FORBIDDEN);
        }
        ReConsignStatus status = decision.getReConsignStatus();
        if (status != ReConsignStatus.PROPOSED && status != ReConsignStatus.COUNTER_OFFERED) {
            throw new AppException(ErrorCode.RE_CONSIGN_NOT_PROPOSED);
        }

        Instant now = Instant.now();
        voidDraftReceipt(decision.getNewReceipt(), "Member không đồng ý ký gửi lại", now);

        decision.setReConsignStatus(ReConsignStatus.DECLINED);
        decision.setDeclinedAt(now);
        decision.setDeclineReason(request.getReason());
        // Điều 8.3: coi như TAKE_BACK để job 30-ngày-đến-lấy-hàng áp dụng y hệt
        decision.setDecision(EndOfTermChoice.TAKE_BACK);
        decision.setDecidedAt(now);
        decisionRepository.save(decision);

        product.setStatus(ProductStatus.WITHDRAWN);
        productRepository.save(product);

        saveHistory(decision, Role.MEMBER, ReConsignNegotiationAction.DECLINE, null, request.getReason());

        queueService.dequeue(QueueType.RECONSIGN_COUNTER, decision.getId().toString());

        businessNotifications.product(product, NotificationType.TERM_EXPIRY, "Đã chuyển sang nhận lại hàng",
                "bạn đã không đồng ý đề xuất ký gửi lại, vui lòng đến kho nhận lại hàng.");
        notifyWarehouseModerators(product.getWarehouse(), NotificationType.QUEUE_TASK,
                "Member không đồng ý ký gửi lại",
                "Món \"" + product.getName() + "\" chuyển sang nhận lại, không cần xử lý thêm giá.");

        auditLogger.logWarehouseOp(member, product.getWarehouse(), WarehouseAuditGroup.INVENTORY,
                "RE_CONSIGN_DECLINE", "END_OF_TERM_DECISION", decision.getId().toString(), "DECLINED",
                product.getId());

        return new DecideEndOfTermResponse(EndOfTermChoice.TAKE_BACK, null);
    }

    @Override
    @Transactional
    public ReConsignTermsResponse counterResponse(Account moderator, UUID decisionId,
                                                  ReConsignCounterResponseRequest request) {
        requireWarehouse(moderator);

        EndOfTermDecision decision = decisionRepository.findByIdForUpdate(decisionId)
                .orElseThrow(() -> new AppException(ErrorCode.END_OF_TERM_DECISION_NOT_FOUND));

        Product product = decision.getProduct();
        if (!product.getWarehouse().getId().equals(moderator.getWarehouse().getId())) {
            throw new AppException(ErrorCode.END_OF_TERM_DECISION_FORBIDDEN);
        }
        if (decision.getReConsignStatus() != ReConsignStatus.COUNTER_OFFERED) {
            throw new AppException(ErrorCode.RE_CONSIGN_NOT_COUNTERED);
        }

        ConsignmentReceipt draft = decision.getNewReceipt();
        ReConsignCounterResponseAction action = request.getAction();

        if (action == ReConsignCounterResponseAction.KEEP) {
            if (!StringUtils.hasText(request.getNote())) {
                throw new com.rehome.rehome.exception.BadRequestException("Cần nêu lý do khi giữ nguyên giá.");
            }
        } else {
            BigDecimal agreedPrice = action == ReConsignCounterResponseAction.ACCEPT
                    ? decision.getMemberCounterPrice()
                    : request.getAgreedPrice();
            if (agreedPrice == null || agreedPrice.signum() <= 0) {
                throw new AppException(ErrorCode.INVALID_PRICE);
            }
            agreedPrice = agreedPrice.setScale(2, RoundingMode.HALF_UP);
            BigDecimal ratio = systemConfigReader.getDecimal(SystemConfigKeys.FLOOR_PRICE_RATIO,
                    SystemConfigKeys.DEFAULT_FLOOR_PRICE_RATIO);
            BigDecimal floorPrice = request.getFloorPrice() != null
                    ? request.getFloorPrice().setScale(2, RoundingMode.HALF_UP)
                    : agreedPrice.multiply(ratio).setScale(2, RoundingMode.HALF_UP);
            if (floorPrice.signum() <= 0 || floorPrice.compareTo(agreedPrice) > 0) {
                throw new AppException(ErrorCode.INVALID_FLOOR_PRICE);
            }
            draft.setAgreedPrice(agreedPrice);
            draft.setFloorPrice(floorPrice);
            draft.setClausesSnapshot(receiptClauseRenderer.render(draft));
        }
        consignmentReceiptRepository.save(draft);

        Instant now = Instant.now();
        int confirmDays = systemConfigReader.getInt(SystemConfigKeys.RE_CONSIGN_CONFIRM_DAYS,
                SystemConfigKeys.DEFAULT_RE_CONSIGN_CONFIRM_DAYS);

        decision.setModeratorResponse(action);
        decision.setModeratorResponseNote(request.getNote());
        decision.setRespondedAt(now);
        decision.setReConsignStatus(ReConsignStatus.PROPOSED);
        decision.setProposalRound(decision.getProposalRound() == null ? 1 : decision.getProposalRound() + 1);
        decision.setProposalDeadline(now.plus(Duration.ofDays(confirmDays)));
        decision.setProposalReminderSentAt(null);
        decisionRepository.save(decision);

        ReConsignNegotiationAction historyAction = switch (action) {
            case ACCEPT -> ReConsignNegotiationAction.ACCEPT;
            case REPROPOSE -> ReConsignNegotiationAction.REPROPOSE;
            case KEEP -> ReConsignNegotiationAction.KEEP;
        };
        saveHistory(decision, Role.MODERATOR, historyAction, draft.getAgreedPrice(), request.getNote());

        queueService.dequeue(QueueType.RECONSIGN_COUNTER, decision.getId().toString());

        businessNotifications.product(product, NotificationType.TERM_EXPIRY, "ReHome phản hồi giá bạn đề xuất",
                "giá hiện tại " + draft.getAgreedPrice() + "đ. Vui lòng xác nhận hoặc đề xuất lại trước hạn "
                        + decision.getProposalDeadline() + ".");

        auditLogger.logWarehouseOp(moderator, product.getWarehouse(), WarehouseAuditGroup.INVENTORY,
                "RE_CONSIGN_COUNTER_RESPONSE", "END_OF_TERM_DECISION", decision.getId().toString(),
                action.name(), product.getId());

        return new ReConsignTermsResponse(decision.getId(), draft.getId());
    }

    // Huỷ biên nhận nháp (PROPOSED/COUNTER_OFFERED, confirmedAt vẫn null) khi Member từ chối hoặc
    // quá hạn - không dùng ConsignmentReceiptService.voidReceipt() vì hàm đó chỉ cho huỷ biên nhận
    // đã CONFIRMED, và cố tình chặn huỷ biên nhận ký gửi lại (RECEIPT_HAS_PRODUCT).
    private void voidDraftReceipt(ConsignmentReceipt draft, String reason, Instant now) {
        if (draft == null) return;
        draft.setStatus(com.rehome.rehome.entity.enums.ReceiptStatus.VOIDED);
        draft.setVoidReason(reason);
        draft.setVoidedAt(now);
        consignmentReceiptRepository.save(draft);
    }

    private void saveHistory(EndOfTermDecision decision, Role actor, ReConsignNegotiationAction action,
                             BigDecimal price, String note) {
        negotiationHistoryRepository.save(ReConsignNegotiationHistory.builder()
                .decision(decision)
                .actor(actor)
                .action(action)
                .price(price)
                .note(note)
                .build());
    }

    private void notifyWarehouseModerators(Warehouse warehouse, NotificationType type, String title, String message) {
        if (warehouse == null) return;
        for (Account moderator : accountRepository.findByRoleAndWarehouse_Id(Role.MODERATOR, warehouse.getId())) {
            businessNotifications.withoutTarget(moderator, type, title, message);
        }
    }

    private void requireWarehouse(Account moderator) {
        if (moderator == null || moderator.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);                                  // 403
        }
    }

    private static BigDecimal orDefault(BigDecimal value, BigDecimal fallback) {
        return value != null ? value : fallback;
    }

    private String generateReceiptNo() {
        return "KG-%d-%06d".formatted(
                Year.now(VN_ZONE).getValue(),
                consignmentReceiptRepository.nextReceiptSequence());
    }
}
