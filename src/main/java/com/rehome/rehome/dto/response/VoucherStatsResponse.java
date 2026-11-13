package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.VoucherType;
import java.math.BigDecimal;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /api/admin/voucher-stats - màn W-05.
 *
 * <p>{@code totalSpent} là <b>số tiền đã thực sự chi</b>, lấy từ các dòng sổ
 * {@code VOUCHER_DISCOUNT} (ghi lúc đơn hoàn tất), KHÔNG phải mệnh giá của mã đã phát: mã phát ra
 * mà không ai dùng thì không tốn đồng nào, và một mã PERCENT cho số tiền khác nhau tùy đơn.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherStatsResponse {

    private long totalIssued;
    private long totalUsed;
    private long totalExpired;
    // Tổng tiền khuyến mãi đã chi trong khoảng, số dương
    private BigDecimal totalSpent;
    @Builder.Default
    private List<TemplateStats> byTemplate = List.of();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TemplateStats {
        private String templateId;
        private String name;
        private VoucherType type;
        private long issued;
        private long used;
        private long expired;
        // used / issued, đơn vị %, 1 chữ số thập phân. 0 khi chưa phát mã nào.
        private BigDecimal usageRatePercent;
    }
}
