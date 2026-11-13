package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.MembershipTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /tryon/quota - hạn mức phối đồ AI (ảnh tĩnh) theo gói, tính theo NGÀY, reset 00:00 giờ VN.
 *
 * <p>Đếm số <b>lượt sinh ảnh</b> trong ngày, không đếm số ảnh đang còn trong bộ sưu tập: Member
 * xóa ảnh <b>không</b> được hoàn lượt. Nên ghi rõ điều này ở dialog xác nhận xóa ảnh (M-06) để
 * tránh hiểu nhầm.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiTryOnQuotaResponse {

    private MembershipTier tier;
    private String tierName;
    private Integer limitPerDay;
    private long usedToday;
    private Integer remainingToday;
    private Integer limitPerMonth;
    private long usedThisMonth;
    private Integer remainingThisMonth;
    private boolean canGenerate;
    private MembershipTier upsellTier;
}
