package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.config.ConsignmentProperties;
import com.rehome.rehome.dto.request.DecideEndOfTermRequest;
import com.rehome.rehome.dto.request.ReConsignTermsRequest;
import com.rehome.rehome.dto.response.DecideEndOfTermResponse;
import com.rehome.rehome.dto.response.PendingReConsignResponse;
import com.rehome.rehome.dto.response.ReConsignTermsResponse;
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
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EndOfTermDecisionServiceImpl implements EndOfTermDecisionService {
    private final EndOfTermDecisionRepository decisionRepository;
    private final ProductRepository productRepository;
    private final ConsignmentReceiptRepository consignmentReceiptRepository;
    private final ConsignmentProperties consignmentProperties;
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
        }
        // RE_CONSIGN: chưa tạo biên nhận. Moderator chốt giá và điều khoản ở setReConsignTerms,
        // lúc đó biên nhận mới mới được tạo và chờ Member xác nhận.

        decision.setDecision(choice);
        decision.setDecidedAt(now);
        decisionRepository.save(decision);

        return new DecideEndOfTermResponse(choice, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingReConsignResponse> listPendingPricing(Account moderator) {
        requireWarehouse(moderator);

        return decisionRepository.findPendingPricingByWarehouse(moderator.getWarehouse().getId())
                .stream()
                .map(d -> {
                    Product p = d.getProduct();
                    return new PendingReConsignResponse(
                            d.getId(),
                            p.getId(),
                            p.getName(),
                            p.getCurrentPrice(),
                            p.getConsignmentReceipt().getAgreedPrice(),
                            d.getDecidedAt());
                })
                .toList();
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
        ConsignmentReceipt newReceipt = consignmentReceiptRepository.save(ConsignmentReceipt.builder()
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
                .build());

        decision.setNewReceipt(newReceipt);
        decisionRepository.save(decision);

        return new ReConsignTermsResponse(decision.getId(), newReceipt.getId());
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
