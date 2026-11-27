package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ReConsignStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Một dòng trong danh sách "chờ Moderator chốt giá ký gửi lại" (GET /end-of-term-decisions/pending-pricing).
// Gồm cả đợt chưa chốt giá lần nào (AWAITING_PRICE, memberCounterPrice = null) lẫn đợt Member vừa
// đề xuất lại giá (COUNTER_OFFERED) đang chờ Moderator phản hồi.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PendingReConsignResponse {
    private UUID decisionId;
    private String productId;
    private String productName;
    // Giá đang bán lúc hết kỳ
    private BigDecimal currentPrice;
    // Giá thỏa thuận của biên nhận cũ, để Moderator tham khảo
    private BigDecimal previousAgreedPrice;
    // Lúc Member chọn ký gửi lại
    private Instant decidedAt;

    // AWAITING_PRICE (chưa chốt giá lần nào) hoặc COUNTER_OFFERED (Member vừa đề xuất lại)
    private ReConsignStatus reConsignStatus;
    // Chỉ có khi COUNTER_OFFERED
    private BigDecimal memberCounterPrice;
    private String memberCounterNote;
    private Instant counteredAt;
    // counteredAt + re_consign_moderator_response_days; quá mốc này coi là quá hạn phản hồi
    private Instant moderatorResponseDeadline;
    private boolean overdue;
}
