package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.config.ConsignmentProperties;
import com.rehome.rehome.dto.request.CompleteInspectionRequest;
import com.rehome.rehome.dto.response.CompleteInspectionResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptConfirmResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ConsignmentReceiptMapper;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.service.ConsignmentReceiptService;
import com.rehome.rehome.service.ReceiptPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsignmentReceiptServiceImpl implements ConsignmentReceiptService {

    private final ConsignmentReceiptRepository consignmentReceiptRepository;
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
        receipt.setPdfUrl(receiptPdfService.generate(receipt)); // F-022

        // TODO: ghi audit log (account, receipt id, thời điểm xác nhận)

        consignmentReceiptRepository.save(receipt);

        return ConsignmentReceiptMapper.toConfirmResponse(receipt);
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
                .confirmedAt(null)   // Member xác nhận ở API 20
                .pdfUrl(null)        // sinh PDF khi xác nhận
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
