package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.request.RedeemPointsRequest;
import com.rehome.rehome.dto.response.PointsSummaryResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.enums.PointRedeemType;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.service.PointsService;
import com.rehome.rehome.service.SystemConfigReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PointsServiceImpl implements PointsService {

    private static final int MAX_REDEEM_QUANTITY = 100;

    private final AccountRepository accountRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final SystemConfigReader systemConfigReader;

    @Override
    @Transactional(readOnly = true)
    public PointsSummaryResponse getSummary(String email) {
        return summaryOf(requireProfile(email));
    }

    @Override
    @Transactional
    public PointsSummaryResponse redeem(String email, RedeemPointsRequest request) {
        MemberProfile profile = requireProfile(email);

        int quantity = request.getQuantity();
        if (quantity < 1 || quantity > MAX_REDEEM_QUANTITY) {
            throw new AppException(ErrorCode.POINTS_INVALID_QUANTITY);                              // 400
        }

        int unitCost = request.getType() == PointRedeemType.AI_TRYON ? costPerAiTryon() : costPerLiveTryon();
        // Admin đặt đơn giá <= 0 nghĩa là đóng quy đổi, tránh đổi miễn phí do cấu hình sai
        if (unitCost <= 0) {
            throw new AppException(ErrorCode.POINTS_REDEEM_UNAVAILABLE);                            // 409
        }
        long totalCost = (long) unitCost * quantity;
        if (totalCost > Integer.MAX_VALUE) {
            throw new AppException(ErrorCode.POINTS_INVALID_QUANTITY);
        }

        // UPDATE có điều kiện points_balance >= cost: hai request đồng thời không thể cùng qua
        int updated = request.getType() == PointRedeemType.AI_TRYON
                ? memberProfileRepository.redeemForAiTryon(profile.getAccountId(), (int) totalCost, quantity)
                : memberProfileRepository.redeemForLiveTryon(profile.getAccountId(), (int) totalCost, quantity);
        if (updated == 0) {
            throw new AppException(ErrorCode.POINTS_NOT_ENOUGH);                                    // 409
        }

        log.info(">>>> Doi diem: memberId={}, type={}, quantity={}, cost={}",
                profile.getAccountId(), request.getType(), quantity, totalCost);

        return summaryOf(memberProfileRepository.findByAccountId(profile.getAccountId()).orElseThrow());
    }

    @Override
    @Transactional
    public int awardForOrder(Order order) {
        int vndPerPoint = vndPerPoint();
        if (vndPerPoint <= 0 || order.getTotalAmount() == null) {
            return 0;
        }
        // total_amount đã trừ voucher: điểm tính trên số tiền khách thực trả
        long points = order.getTotalAmount()
                .divideToIntegralValue(BigDecimal.valueOf(vndPerPoint))
                .setScale(0, RoundingMode.DOWN)
                .longValue();
        if (points <= 0) {
            return 0;
        }
        int award = (int) Math.min(points, Integer.MAX_VALUE);
        memberProfileRepository.addPoints(order.getBuyer().getId(), award);
        log.info(">>>> Cong diem: memberId={}, orderNo={}, points={}",
                order.getBuyer().getId(), order.getOrderNo(), award);
        return award;
    }

    private MemberProfile requireProfile(String email) {
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));
        return memberProfileRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy hồ sơ thành viên."));
    }

    private PointsSummaryResponse summaryOf(MemberProfile profile) {
        return PointsSummaryResponse.builder()
                .pointsBalance(profile.getPointsBalance())
                .bonusAiTryonCredits(profile.getBonusAiTryonCredits())
                .bonusLiveTryonCredits(profile.getBonusLiveTryonCredits())
                .vndPerPoint(Math.max(vndPerPoint(), 0))
                .costPerAiTryon(costPerAiTryon())
                .costPerLiveTryon(costPerLiveTryon())
                .build();
    }

    private int vndPerPoint() {
        return systemConfigReader.getInt(
                SystemConfigKeys.POINTS_VND_PER_POINT, SystemConfigKeys.DEFAULT_POINTS_VND_PER_POINT);
    }

    private int costPerAiTryon() {
        return systemConfigReader.getInt(
                SystemConfigKeys.POINTS_COST_PER_AI_TRYON, SystemConfigKeys.DEFAULT_POINTS_COST_PER_AI_TRYON);
    }

    private int costPerLiveTryon() {
        return systemConfigReader.getInt(
                SystemConfigKeys.POINTS_COST_PER_LIVE_TRYON, SystemConfigKeys.DEFAULT_POINTS_COST_PER_LIVE_TRYON);
    }
}
