package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.MembershipTier;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/admin/membership-stats - màn W-05.
 *
 * <p>Thẻ cảnh báo của W-05 so "doanh thu gói" với "chi voucher + chi phí AI ước tính" theo từng
 * hạng, nên {@link TierBreakdown} phải trả cả doanh thu lẫn số lượt thử đồ đã dùng - FE không tự
 * tính được phần sau.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipStatsResponse {

    // ---- Thẻ số ----

    // Tổng doanh thu bán gói trong khoảng from..to (chỉ tính payment SUCCESS)
    private BigDecimal totalRevenue;
    // Số subscription đang ACTIVE và còn hạn, tại thời điểm gọi API (không phụ thuộc from/to)
    private long activeSubscriptions;
    // Số lần mua/gia hạn có thu tiền trong khoảng
    private long purchasesInRange;
    // Gói hết hạn trong khoảng mà Member KHÔNG mua lại hạng nào sau đó
    private long notRenewedInRange;

    // ---- Biểu đồ cột: doanh thu theo tháng, chia màu theo hạng ----
    @Builder.Default
    private List<MonthlyRevenue> revenueByMonth = List.of();

    // ---- Bảng theo hạng ----
    @Builder.Default
    private List<TierBreakdown> byTier = List.of();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class MonthlyRevenue {
        // yyyy-MM theo giờ VN
        private String month;
        private MembershipTier tier;
        private BigDecimal revenue;
        private long purchases;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TierBreakdown {
        private MembershipTier tier;
        private String tierName;
        private BigDecimal revenue;
        private long purchases;
        private long activeSubscriptions;
        // Chi phí khuyến mãi đã ghi nhận cho voucher phát từ hạng này, số dương
        private BigDecimal voucherCost;
        // Số lượt thử đồ đã dùng, để Admin nhân với chi phí thật mỗi lượt và đối chiếu lãi/lỗ
        private long liveTryonUsed;
        private long aiTryonUsed;
    }
}
