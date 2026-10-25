package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.config.ConsignmentProperties;
import com.rehome.rehome.dto.request.CompleteInspectionRequest;
import com.rehome.rehome.dto.response.CompleteInspectionResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptConfirmResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.dto.response.PdfDownload;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.ConsignmentReceiptPdf;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ConsignmentReceiptMapper;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.ConsignmentReceiptPdfRepository;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.service.ConsignmentReceiptService;
import com.rehome.rehome.service.ReceiptPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsignmentReceiptServiceImpl implements ConsignmentReceiptService {

    private final ConsignmentReceiptRepository consignmentReceiptRepository;
    private final ConsignmentReceiptPdfRepository consignmentReceiptPdfRepository;
    private final ReceiptPdfService receiptPdfService;   // interface, không phải ReceiptPdfServiceImpl
    private final ConsignmentFormRepository consignmentFormRepository;
    private final AccountRepository accountRepository;

    private final ConsignmentProperties consignmentProperties;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    @Override
    @Transactional
    public ConsignmentReceiptResponse getMyReceipt(Account account, UUID id) {
        if(account.getRole() != Role.MEMBER){
            throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }

        ConsignmentReceipt receipt = consignmentReceiptRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_RECEIPT_NOT_FOUND));

        if (!receipt.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_FORBIDDEN);
        }

        return ConsignmentReceiptMapper.toResponse(receipt);
    }

    @Override
    @Transactional
    public ConsignmentReceiptConfirmResponse confirmReceipt(Account account, UUID id) {
        // Khóa dòng để chống bấm xác nhận đồng thời
        ConsignmentReceipt receipt = consignmentReceiptRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_RECEIPT_NOT_FOUND));

        if (!receipt.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_FORBIDDEN);
        }
        if (receipt.getConfirmedAt() != null) {
            throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_ALREADY_CONFIRMED);
        }

        Instant now = Instant.now();

        // Set confirmedAt trước để PDF snapshot có đúng mốc xác nhận
        receipt.setConfirmedAt(now);

        // F-022: render PDF trong cùng transaction. Thuần CPU, không gọi mạng, nên
        // hoặc commit được cả confirmedAt lẫn PDF, hoặc rollback cả hai - biên nhận
        // không bao giờ "có hiệu lực" mà thiếu file bằng chứng.
        byte[] pdf = receiptPdfService.generate(receipt);

        // TODO: ghi audit log (account, receipt id, thời điểm xác nhận)

        consignmentReceiptRepository.save(receipt);
        consignmentReceiptPdfRepository.save(ConsignmentReceiptPdf.builder()
                .receipt(receipt)
                .content(pdf)
                .sha256(sha256(pdf))
                .byteSize(pdf.length)
                .generatedAt(now)
                .build());

        return ConsignmentReceiptMapper.toConfirmResponse(receipt);
    }

    @Override
    @Transactional(readOnly = true)
    public PdfDownload getReceiptPdf(Account account, UUID id) {
        ConsignmentReceipt receipt = consignmentReceiptRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_RECEIPT_NOT_FOUND));

        // Member chỉ xem biên nhận của mình; Moderator/Admin xem được mọi biên nhận
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

    private static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append("%02x".formatted(b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 là thuật toán bắt buộc có trong mọi JVM
            throw new IllegalStateException(e);
        }
    }

    @Override
    @Transactional
    public CompleteInspectionResponse completeInspection(UUID formId, CompleteInspectionRequest request) {
        ConsignmentForm form = consignmentFormRepository.findById(formId)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));      // 404

        if (form.getStatus() != ConsignmentFormStatus.INSPECTING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_INSPECTING);                    // 409
        }
        if (consignmentReceiptRepository.existsByConsignmentFormId(form.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_ALREADY_EXISTS);                 // 409
        }

        ConsignmentProperties cfg = consignmentProperties;
        BigDecimal agreedPrice = request.getAgreedPrice().setScale(2, RoundingMode.HALF_UP);
        int termDays = request.getTermLengthDays() != null
                ? request.getTermLengthDays() : cfg.getTermLengthDays();

        ConsignmentReceipt receipt = ConsignmentReceipt.builder()
                .receiptNo(generateReceiptNo())
                .consignmentForm(form)
                .member(form.getMember())
                .moderator(currentModerator())
                .warehouse(form.getWarehouse())
                .conditionNote(request.getConditionNote().trim())
                .agreedPrice(agreedPrice)
                .floorPrice(agreedPrice.multiply(cfg.getFloorPriceRate()).setScale(2, RoundingMode.HALF_UP))
                .termLengthDays(termDays)
                .maxRenewals(cfg.getMaxRenewals())
                .freeWithdrawalWindowDays(cfg.getFreeWithdrawalWindowDays())
                .commissionRateTerm1(cfg.getCommissionRateTerm1())
                .commissionRateTerm2(cfg.getCommissionRateTerm2())
                .commissionRateTerm3(cfg.getCommissionRateTerm3())
                .withdrawalFee(cfg.getWithdrawalFee())
                .degradedCompensationRate(cfg.getDegradedCompensationRate())
                .confirmedAt(null)   // Member xác nhận ở API 20, PDF sinh lúc đó
                .build();

        consignmentReceiptRepository.save(receipt);
        return new CompleteInspectionResponse(receipt.getId());
    }

    private String generateReceiptNo() {
        // KG-2026-000123
        return "KG-%d-%06d".formatted(Year.now(VN_ZONE).getValue(), consignmentReceiptRepository.nextReceiptSequence());
    }

    private Account currentModerator() {
        String name = SecurityContextHolder.getContext().getAuthentication().getName();
        return accountRepository.findByEmail(name)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
    }


}
