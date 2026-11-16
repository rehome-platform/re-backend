package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GET /points và kết quả POST /points/redeem. Các đơn giá lấy từ system_configs nên mobile hiển thị
 * theo đây, không hard-code.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PointsSummaryResponse {

    private int pointsBalance;
    // Lượt mua bằng điểm còn lại, chỉ bị trừ khi quota của gói trong ngày/tháng đã hết
    private int bonusAiTryonCredits;
    private int bonusLiveTryonCredits;

    // Bao nhiêu đồng của đơn thì được 1 điểm; 0 = đang tắt tích điểm
    private int vndPerPoint;
    private int costPerAiTryon;
    private int costPerLiveTryon;
}
