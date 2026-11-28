package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.NotificationType;

import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.component.ReceiptClauseRenderer;
import com.rehome.rehome.entity.enums.WarehouseAuditGroup;
import com.rehome.rehome.component.FeaturedListingManager;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.request.CompleteInspectionRequest;
import com.rehome.rehome.dto.response.CompleteInspectionResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptConfirmResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.dto.response.PdfDownload;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.ConsignmentReceiptPdf;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ConsignmentReceiptMapper;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.ConsignmentReceiptPdfRepository;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.ConsignmentReceiptService;
import com.rehome.rehome.service.ReceiptPdfService;
import com.rehome.rehome.service.SystemConfigReader;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsignmentReceiptServiceImpl implements ConsignmentReceiptService {
    private final com.rehome.rehome.component.BusinessNotifications businessNotifications;

    private final AuditLogger auditLogger;
    private final com.rehome.rehome.service.QueueService queueService;

    private final ConsignmentReceiptRepository consignmentReceiptRepository;
    private final ConsignmentReceiptPdfRepository consignmentReceiptPdfRepository;
    private final ReceiptPdfService receiptPdfService;
    private final ConsignmentFormRepository consignmentFormRepository;
    private final AccountRepository accountRepository;
    private final ProductRepository productRepository;
    private final EndOfTermDecisionRepository endOfTermDecisionRepository;
    private final com.rehome.rehome.repository.ReConsignNegotiationHistoryRepository negotiationHistoryRepository;
    private final FeaturedListingManager featuredListingManager;

    private final SystemConfigReader systemConfigReader;
    private final ReceiptClauseRenderer receiptClauseRenderer;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    @Override
    @Transactional
    public ConsignmentReceiptResponse getMyReceipt(Account account, UUID id) {
        ConsignmentReceipt receipt = consignmentReceiptRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_RECEIPT_NOT_FOUND));

        authorizeRead(account, receipt);

        String sku = productRepository.findByConsignmentReceiptId(receipt.getId())
                .map(Product::getId)
                .orElse(null);

        EndOfTermDecision reConsign = endOfTermDecisionRepository
                .findByNewReceipt_Id(receipt.getId())
                .orElse(null);

        ConsignmentReceiptResponse response = ConsignmentReceiptMapper.toResponse(receipt, sku, reConsign);
        if (reConsign != null) {
            response.setMaxCounterRounds(systemConfigReader.getInt(
                    SystemConfigKeys.RE_CONSIGN_MAX_COUNTER_ROUNDS,
                    SystemConfigKeys.DEFAULT_RE_CONSIGN_MAX_COUNTER_ROUNDS));
            response.setNegotiationHistory(negotiationHistoryRepository
                    .findByDecision_IdOrderByCreatedAtAsc(reConsign.getId()).stream()
                    .map(h -> ConsignmentReceiptResponse.NegotiationHistoryItem.builder()
                            .actor(h.getActor())
                            .action(h.getAction())
                            .price(h.getPrice())
                            .note(h.getNote())
                            .createdAt(h.getCreatedAt())
                            .build())
                    .toList());
        }
        consignmentReceiptRepository.findByPreviousReceipt_Id(id).ifPresent(next -> response.setReplacedByReceiptId(next.getId()));
        return response;
    }

    @Override
    @Transactional
    public ConsignmentReceiptConfirmResponse confirmReceipt(Account account, UUID id) {
        return confirmReceipt(account, id, null);
    }

    @Override
    @Transactional
    public ConsignmentReceiptConfirmResponse confirmReceipt(Account account, UUID id, Integer version) {
        ConsignmentReceipt receipt = consignmentReceiptRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_RECEIPT_NOT_FOUND));

        if (!receipt.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_FORBIDDEN);
        }
        if (version != null && version != receipt.getVersion()) {
            throw new AppException(ErrorCode.RECEIPT_CHANGED);
        }
        if (receipt.effectiveStatus() == com.rehome.rehome.entity.enums.ReceiptStatus.VOIDED) {
            throw new AppException(ErrorCode.RECEIPT_NOT_EDITABLE);
        }
        if (receipt.getConfirmedAt() != null) {
            throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_ALREADY_CONFIRMED);
        }

        var reConsignDecision = endOfTermDecisionRepository.findByNewReceipt_Id(receipt.getId());
        reConsignDecision.ifPresent(reConsign -> {
            if (reConsign.getReConsignStatus() == com.rehome.rehome.entity.enums.ReConsignStatus.COUNTER_OFFERED) {
                throw new AppException(ErrorCode.RE_CONSIGN_WAITING_MODERATOR);
            }
            if (reConsign.getReConsignStatus() != com.rehome.rehome.entity.enums.ReConsignStatus.PROPOSED) {
                throw new AppException(ErrorCode.RE_CONSIGN_NOT_PROPOSED);
            }
            if (reConsign.getProposalDeadline() != null && Instant.now().isAfter(reConsign.getProposalDeadline())) {
                throw new AppException(ErrorCode.RE_CONSIGN_PROPOSAL_EXPIRED);
            }
        });

        Instant now = Instant.now();

        receipt.setConfirmedAt(now);
        receipt.setStatus(com.rehome.rehome.entity.enums.ReceiptStatus.CONFIRMED);

        byte[] pdf = receiptPdfService.generate(receipt);

        consignmentReceiptRepository.save(receipt);
        saveReceiptPdf(receipt, pdf, now);

        reConsignDecision.ifPresent(reConsign -> {
            reConsign.setReConsignStatus(com.rehome.rehome.entity.enums.ReConsignStatus.CONFIRMED);
            endOfTermDecisionRepository.save(reConsign);
            activateReConsignedProduct(reConsign.getProduct(), receipt, now);
        });

        if (reConsignDecision.isPresent()) {
            auditLogger.logWarehouseOp(account, receipt.getWarehouse(), WarehouseAuditGroup.INVENTORY,
                    "CONFIRM_RECONSIGN", "CONSIGNMENT_RECEIPT", receipt.getId().toString(), "CONFIRMED",
                    receipt.getReceiptNo());
        } else {
            auditLogger.logWarehouseOp(account, receipt.getWarehouse(), WarehouseAuditGroup.INSPECTION,
                    "CONFIRM_RECEIPT", "CONSIGNMENT_RECEIPT", receipt.getId().toString(), "CONFIRMED",
                    receipt.getReceiptNo());
        }

        return ConsignmentReceiptMapper.toConfirmResponse(receipt);
    }

    private ConsignmentReceipt editableReceipt(Account actor, UUID id) {
        ConsignmentReceipt receipt = consignmentReceiptRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_RECEIPT_NOT_FOUND));
        if (actor.getRole() != Role.MODERATOR) {
            throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_FORBIDDEN);
        }
        authorizeRead(actor, receipt);
        return receipt;
    }

    @Override
    @Transactional
    public ConsignmentReceiptResponse editReceipt(Account actor, UUID id,
            com.rehome.rehome.dto.request.EditReceiptRequest request) {
        ConsignmentReceipt receipt = editableReceipt(actor, id);
        if (receipt.effectiveStatus() != com.rehome.rehome.entity.enums.ReceiptStatus.PENDING) {
            throw new AppException(ErrorCode.RECEIPT_NOT_EDITABLE);
        }
        if (request.reason() == null || request.reason().isBlank()
                || request.agreedPrice() == null || request.agreedPrice().signum() <= 0
                || request.conditionNote() == null || request.conditionNote().isBlank()) {
            throw new com.rehome.rehome.exception.BadRequestException("Giá, tình trạng và lý do là bắt buộc.");
        }
        var detail = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        detail.put("oldAgreedPrice", receipt.getAgreedPrice());
        detail.put("oldFloorPrice", receipt.getFloorPrice());
        detail.put("oldConditionNote", receipt.getConditionNote());
        detail.put("oldModeratorNote", receipt.getModeratorNote());
        receipt.setAgreedPrice(request.agreedPrice().setScale(2, RoundingMode.HALF_UP));
        BigDecimal ratio = systemConfigReader.getDecimal(SystemConfigKeys.FLOOR_PRICE_RATIO,
                SystemConfigKeys.DEFAULT_FLOOR_PRICE_RATIO);
        receipt.setFloorPrice(receipt.getAgreedPrice().multiply(ratio).setScale(2, RoundingMode.HALF_UP));
        receipt.setConditionNote(request.conditionNote().strip());
        receipt.setModeratorNote(request.moderatorNote() == null ? null : request.moderatorNote().strip());
        receipt.setVersion(receipt.getVersion() + 1);
        receipt.setEditReason(request.reason().strip());
        receipt.setEditedAt(Instant.now());
        detail.put("newAgreedPrice", receipt.getAgreedPrice());
        detail.put("newFloorPrice", receipt.getFloorPrice());
        detail.put("newConditionNote", receipt.getConditionNote());
        detail.put("newModeratorNote", receipt.getModeratorNote());
        detail.put("reason", receipt.getEditReason());
        detail.put("version", receipt.getVersion());
        byte[] pdf = receiptPdfService.generate(receipt);
        saveReceiptPdf(receipt, pdf, receipt.getEditedAt());
        auditLogger.log(actor, "UPDATE_RECEIPT", "CONSIGNMENT_RECEIPT", id.toString(), detail);
        receiptNotification(receipt, com.rehome.rehome.entity.enums.NotificationType.RECEIPT_UPDATED,
                "Biên nhận " + receipt.getReceiptNo() + " đã được cập nhật: " + receipt.getEditReason());
        return getMyReceipt(actor, id);
    }

    @Override
    @Transactional
    public ConsignmentReceiptResponse voidReceipt(Account actor, UUID id, String reason) {
        ConsignmentReceipt receipt = editableReceipt(actor, id);
        if (reason == null || reason.isBlank()) {
            throw new com.rehome.rehome.exception.BadRequestException("Vui lòng nhập lý do huỷ.");
        }
        if (receipt.effectiveStatus() != com.rehome.rehome.entity.enums.ReceiptStatus.CONFIRMED) {
            throw new AppException(ErrorCode.RECEIPT_NOT_EDITABLE);
        }
        if (productRepository.findByConsignmentReceiptId(id).isPresent()
                || !productRepository.findProductIdsByFormIds(java.util.List.of(receipt.getConsignmentForm().getId())).isEmpty()
                || endOfTermDecisionRepository.findByNewReceipt_Id(id).isPresent()) {
            throw new AppException(ErrorCode.RECEIPT_HAS_PRODUCT);
        }
        receipt.setStatus(com.rehome.rehome.entity.enums.ReceiptStatus.VOIDED);
        receipt.setVoidReason(reason.strip());
        receipt.setVoidedAt(Instant.now());
        receipt.setVoidedBy(actor);
        receipt.getConsignmentForm().setStatus(ConsignmentFormStatus.INSPECTING);
        queueService.enqueue(com.rehome.rehome.entity.enums.QueueType.INSPECTION,
                receipt.getConsignmentForm().getId().toString(), receipt.getWarehouse());
        auditLogger.logOperation(actor, "VOID_RECEIPT", "CONSIGNMENT_RECEIPT", id, receipt.getVoidReason());
        receiptNotification(receipt, com.rehome.rehome.entity.enums.NotificationType.RECEIPT_VOIDED,
                "Biên nhận " + receipt.getReceiptNo() + " đã bị huỷ: " + receipt.getVoidReason()
                        + ". ReHome sẽ gửi biên nhận mới để bạn xác nhận lại.");
        return getMyReceipt(actor, id);
    }

    private void saveReceiptPdf(ConsignmentReceipt receipt, byte[] content, Instant generatedAt) {
        ConsignmentReceiptPdf pdf = consignmentReceiptPdfRepository.findById(receipt.getId())
                .orElseGet(() -> ConsignmentReceiptPdf.builder().receipt(receipt).build());
        pdf.setContent(content);
        pdf.setSha256(sha256(content));
        pdf.setByteSize(content.length);
        pdf.setGeneratedAt(generatedAt);
        consignmentReceiptPdfRepository.save(pdf);
    }

    private void receiptNotification(ConsignmentReceipt receipt,
            com.rehome.rehome.entity.enums.NotificationType type, String message) {
        businessNotifications.send(receipt.getMember(), type, "Cập nhật biên nhận ký gửi", message,
                com.rehome.rehome.entity.enums.NotificationTargetType.CONSIGNMENT_RECEIPT, receipt.getId());
    }

    private void activateReConsignedProduct(Product product, ConsignmentReceipt newReceipt, Instant now) {
        if (product.getStatus() == ProductStatus.DONATED) {
            throw new AppException(ErrorCode.PRODUCT_NOT_DONATABLE);
        }
        product.setConsignmentReceipt(newReceipt);
        product.setCurrentPrice(newReceipt.getAgreedPrice());
        product.setCurrentTerm((short) 1);
        product.setTermStartedAt(now);
        product.setEndOfTermDeadline(now.plus(Duration.ofDays(
                (long) newReceipt.getTermLengthDays() * (newReceipt.getMaxRenewals() + 1))));
        product.setStatus(ProductStatus.ON_SALE);
        featuredListingManager.applyForNewProduct(product);
        productRepository.save(product);
        businessNotifications.product(product, NotificationType.QUEUE_TASK, "Món ký gửi đã lên sàn", "đã được ký gửi lại và đăng bán.");
    }

    @Override
    @Transactional(readOnly = true)
    public PdfDownload getReceiptPdf(Account account, UUID id) {
        ConsignmentReceipt receipt = consignmentReceiptRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_RECEIPT_NOT_FOUND));

        boolean staff = account.getRole() == Role.MODERATOR || account.getRole() == Role.ADMIN;
        if (!staff && !receipt.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_FORBIDDEN);
        }
        if (receipt.getConfirmedAt() == null) {
            throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_PDF_NOT_READY);
        }

        ConsignmentReceiptPdf pdf = consignmentReceiptPdfRepository.findById(receipt.getId())
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_RECEIPT_PDF_MISSING));

        return new PdfDownload(receipt.getReceiptNo() + ".pdf", pdf.getContent());
    }

    private void authorizeRead(Account account, ConsignmentReceipt receipt) {
        switch (account.getRole()) {
            case ADMIN -> {
            }
            case MODERATOR -> {
                if (account.getWarehouse() == null) {
                    throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);
                }
                if (!receipt.getWarehouse().getId().equals(account.getWarehouse().getId())) {
                    throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_FORBIDDEN);
                }
            }
            case MEMBER -> {
                if (!receipt.getMember().getId().equals(account.getId())) {
                    throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_FORBIDDEN);
                }
            }
            default -> throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }
    }

    private static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append("%02x".formatted(b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    @Transactional
    public CompleteInspectionResponse completeInspection(UUID formId, CompleteInspectionRequest request) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(formId)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));

        if (form.getStatus() != ConsignmentFormStatus.INSPECTING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_INSPECTING);
        }
        ConsignmentReceipt previous = consignmentReceiptRepository
                .findFirstByConsignmentForm_IdOrderByCreatedAtDesc(form.getId()).orElse(null);
        if (previous != null && previous.effectiveStatus() != com.rehome.rehome.entity.enums.ReceiptStatus.VOIDED) {
            throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_ALREADY_EXISTS);
        }

        Account moderator = currentModerator();
        if (moderator.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);
        }
        if (form.getWarehouse() == null
                || !form.getWarehouse().getId().equals(moderator.getWarehouse().getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);
        }

        BigDecimal agreedPrice = request.getAgreedPrice().setScale(2, RoundingMode.HALF_UP);
        int termDays = request.getTermLengthDays() != null
                ? request.getTermLengthDays()
                : (previous != null ? previous.getTermLengthDays() : systemConfigReader.getInt(SystemConfigKeys.TERM_LENGTH_DAYS, SystemConfigKeys.DEFAULT_TERM_LENGTH_DAYS));
        BigDecimal floorPriceRatio = systemConfigReader.getDecimal(
                SystemConfigKeys.FLOOR_PRICE_RATIO, SystemConfigKeys.DEFAULT_FLOOR_PRICE_RATIO);

        ConsignmentReceipt receipt = ConsignmentReceipt.builder()
                .receiptNo(generateReceiptNo())
                .consignmentForm(form)
                .member(form.getMember())
                .moderator(moderator)
                .warehouse(form.getWarehouse())
                .conditionNote(request.getConditionNote().trim())
                .moderatorNote(request.getModeratorNote() == null || request.getModeratorNote().isBlank()
                        ? null : request.getModeratorNote().trim())
                .agreedPrice(agreedPrice)
                .floorPrice(agreedPrice.multiply(floorPriceRatio).setScale(2, RoundingMode.HALF_UP))
                .termLengthDays(termDays)
                .maxRenewals((previous != null ? previous.getMaxRenewals() : systemConfigReader.getInt(SystemConfigKeys.MAX_RENEWALS, SystemConfigKeys.DEFAULT_MAX_RENEWALS)))
                .freeWithdrawalWindowDays((previous != null ? previous.getFreeWithdrawalWindowDays() : systemConfigReader.getInt(
                        SystemConfigKeys.FREE_WITHDRAWAL_WINDOW_DAYS, SystemConfigKeys.DEFAULT_FREE_WITHDRAWAL_WINDOW_DAYS)))
                .commissionRateTerm1((previous != null ? previous.getCommissionRateTerm1() : systemConfigReader.getDecimal(
                        SystemConfigKeys.COMMISSION_RATE_TERM1, SystemConfigKeys.DEFAULT_COMMISSION_RATE_TERM1)))
                .commissionRateTerm2((previous != null ? previous.getCommissionRateTerm2() : systemConfigReader.getDecimal(
                        SystemConfigKeys.COMMISSION_RATE_TERM2, SystemConfigKeys.DEFAULT_COMMISSION_RATE_TERM2)))
                .commissionRateTerm3((previous != null ? previous.getCommissionRateTerm3() : systemConfigReader.getDecimal(
                        SystemConfigKeys.COMMISSION_RATE_TERM3, SystemConfigKeys.DEFAULT_COMMISSION_RATE_TERM3)))
                .withdrawalFee((previous != null ? previous.getWithdrawalFee() : systemConfigReader.getDecimal(
                        SystemConfigKeys.WITHDRAWAL_FEE, SystemConfigKeys.DEFAULT_WITHDRAWAL_FEE)))
                .degradedCompensationRate((previous != null ? previous.getDegradedCompensationRate() : systemConfigReader.getDecimal(
                        SystemConfigKeys.DEGRADED_COMPENSATION_RATE, SystemConfigKeys.DEFAULT_DEGRADED_COMPENSATION_RATE)))
                .confirmedAt(null)
                .build();
        receipt.setClausesSnapshot(receiptClauseRenderer.render(receipt));

        if (previous != null) {
            receipt.replaceVoided(previous);
        }

        consignmentReceiptRepository.save(receipt);
        businessNotifications.receipt(receipt);

        auditLogger.logWarehouseOp(moderator, form.getWarehouse(), WarehouseAuditGroup.INSPECTION,
                "COMPLETE_INSPECTION", "CONSIGNMENT_FORM", form.getId().toString(), "PASSED", form.getFormNo());
        return new CompleteInspectionResponse(receipt.getId());
    }

    private String generateReceiptNo() {
        return "KG-%d-%06d".formatted(Year.now(VN_ZONE).getValue(), consignmentReceiptRepository.nextReceiptSequence());
    }

    private Account currentModerator() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        return accountRepository.findByEmail(name)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
    }


}
