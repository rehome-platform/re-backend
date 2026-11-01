package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.DecideEndOfTermRequest;
import com.rehome.rehome.dto.response.DecideEndOfTermResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.EndOfTermDecisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EndOfTermDecisionServiceImpl implements EndOfTermDecisionService {
    private final EndOfTermDecisionRepository decisionRepository;
    private final ProductRepository productRepository;
    private final ConsignmentReceiptRepository consignmentReceiptRepository;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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

        // Khóa dòng để chống bấm đồng thời (tránh tạo 2 phiếu ký gửi lại)
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

        ConsignmentReceipt newReceipt = null;

        if (choice == EndOfTermChoice.TAKE_BACK) {
            product.setStatus(ProductStatus.WITHDRAWN);
            productRepository.save(product);
        } else {
            newReceipt = consignmentReceiptRepository.save(
                    buildReConsignReceipt(product.getConsignmentReceipt()));
        }

        decision.setDecision(choice);
        decision.setDecidedAt(now);
        decision.setNewReceipt(newReceipt);
        decisionRepository.save(decision);

        return new DecideEndOfTermResponse(choice, newReceipt != null ? newReceipt.getId() : null);
    }



    // Sao chép điều khoản từ phiếu cũ, chưa xác nhận, chưa có PDF
    private ConsignmentReceipt buildReConsignReceipt(ConsignmentReceipt old) {
        return ConsignmentReceipt.builder()
                .receiptNo(generateReceiptNo())
                .consignmentForm(old.getConsignmentForm())
                .member(old.getMember())
                .moderator(old.getModerator())
                .warehouse(old.getWarehouse())
                .conditionNote(old.getConditionNote())
                .moderatorNote(old.getModeratorNote())
                .agreedPrice(old.getAgreedPrice())
                .floorPrice(old.getFloorPrice())
                .termLengthDays(old.getTermLengthDays())
                .maxRenewals(old.getMaxRenewals())
                .freeWithdrawalWindowDays(old.getFreeWithdrawalWindowDays())
                .commissionRateTerm1(old.getCommissionRateTerm1())
                .commissionRateTerm2(old.getCommissionRateTerm2())
                .commissionRateTerm3(old.getCommissionRateTerm3())
                .withdrawalFee(old.getWithdrawalFee())
                .degradedCompensationRate(old.getDegradedCompensationRate())
                .confirmedAt(null)
                .build();
    }

    private String generateReceiptNo() {
        return "KG-%d-%06d".formatted(
                Year.now(VN_ZONE).getValue(),
                consignmentReceiptRepository.nextReceiptSequence());
    }
}
