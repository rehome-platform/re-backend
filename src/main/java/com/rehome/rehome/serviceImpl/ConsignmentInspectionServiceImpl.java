package com.rehome.rehome.serviceImpl;

import static com.rehome.rehome.constant.SystemConfigKeys.*;

import com.rehome.rehome.component.ReceiptNoGenerator;
import com.rehome.rehome.dto.request.CompleteInspectionRequest;
import com.rehome.rehome.dto.response.CompleteInspectionResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.InspectionResult;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.service.ConsignmentInspectionService;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.service.SystemConfigReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConsignmentInspectionServiceImpl implements ConsignmentInspectionService {

    private final ConsignmentFormRepository consignmentFormRepository;
    private final ConsignmentReceiptRepository consignmentReceiptRepository;
    private final SystemConfigReader systemConfigReader;
    private final ReceiptNoGenerator receiptNoGenerator;
    private final QueueService queueService;

    @Override
    @Transactional
    public CompleteInspectionResponse completeInspection(
            UUID formId, CompleteInspectionRequest req, Account moderator) {
        validateComplete(req);
        Warehouse warehouse = requireWarehouse(moderator);
        ConsignmentForm form = loadFormInWarehouse(formId, warehouse);

        if (form.getStatus() != ConsignmentFormStatus.INSPECTING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_INSPECTING);
        }
        if (consignmentReceiptRepository.existsByConsignmentForm_Id(formId)) {
            throw new AppException(ErrorCode.CONSIGNMENT_RECEIPT_ALREADY_EXISTS);
        }

        BigDecimal agreedPrice = req.getAgreedPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal floorRatio = systemConfigReader.getDecimal(FLOOR_PRICE_RATIO, DEFAULT_FLOOR_PRICE_RATIO);
        BigDecimal floorPrice = agreedPrice.multiply(floorRatio).setScale(2, RoundingMode.HALF_UP);
        int termLengthDays = req.getTermLengthDays() != null
                ? req.getTermLengthDays()
                : systemConfigReader.getInt(TERM_LENGTH_DAYS, DEFAULT_TERM_LENGTH_DAYS);

        // Các giá trị snapshot: đọc config tại thời điểm tạo phiếu, đổi config sau không ảnh hưởng phiếu này
        ConsignmentReceipt receipt = ConsignmentReceipt.builder()
                .receiptNo(receiptNoGenerator.next())
                .consignmentForm(form)
                .member(form.getMember())
                .moderator(moderator)
                .warehouse(form.getWarehouse())
                .agreedPrice(agreedPrice)
                .floorPrice(floorPrice)
                .termLengthDays(termLengthDays)
                .maxRenewals(systemConfigReader.getInt(MAX_RENEWALS, DEFAULT_MAX_RENEWALS))
                .freeWithdrawalWindowDays(
                        systemConfigReader.getInt(FREE_WITHDRAWAL_WINDOW_DAYS, DEFAULT_FREE_WITHDRAWAL_WINDOW_DAYS))
                .commissionRateTerm1(systemConfigReader.getDecimal(COMMISSION_RATE_TERM1, DEFAULT_COMMISSION_RATE_TERM1))
                .commissionRateTerm2(systemConfigReader.getDecimal(COMMISSION_RATE_TERM2, DEFAULT_COMMISSION_RATE_TERM2))
                .commissionRateTerm3(systemConfigReader.getDecimal(COMMISSION_RATE_TERM3, DEFAULT_COMMISSION_RATE_TERM3))
                .withdrawalFee(systemConfigReader.getDecimal(WITHDRAWAL_FEE, DEFAULT_WITHDRAWAL_FEE))
                .degradedCompensationRate(
                        systemConfigReader.getDecimal(DEGRADED_COMPENSATION_RATE, DEFAULT_DEGRADED_COMPENSATION_RATE))
                .conditionNote(trimToNull(req.getConditionNote()))
                .build();

        ConsignmentReceipt saved = consignmentReceiptRepository.save(receipt);

        // Việc kiểm định này đã xong, đóng trong hàng đợi
        queueService.dequeue(QueueType.INSPECTION, formId.toString());

        return CompleteInspectionResponse.builder().receiptId(saved.getId()).build();
    }

    private void validateComplete(CompleteInspectionRequest req) {
        if (req.getResult() != InspectionResult.PASS) {
            throw new AppException(ErrorCode.INVALID_INSPECTION_RESULT);
        }
        if (req.getAgreedPrice() == null || req.getAgreedPrice().signum() <= 0) {
            throw new AppException(ErrorCode.INVALID_AGREED_PRICE);
        }
        if (req.getTermLengthDays() != null && req.getTermLengthDays() <= 0) {
            throw new AppException(ErrorCode.INVALID_TERM_LENGTH_DAYS);
        }
    }

    private Warehouse requireWarehouse(Account moderator) {
        if (moderator.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);
        }
        return moderator.getWarehouse();
    }

    // Khóa dòng form để 2 Moderator không xử lý cùng lúc; form kho khác coi như không tồn tại
    private ConsignmentForm loadFormInWarehouse(UUID formId, Warehouse warehouse) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(formId)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));
        if (!form.getWarehouse().getId().equals(warehouse.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);
        }
        return form;
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}