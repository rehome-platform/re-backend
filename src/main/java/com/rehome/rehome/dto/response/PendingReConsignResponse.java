package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// Một dòng trong danh sách "chờ Moderator chốt giá ký gửi lại"
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
}
