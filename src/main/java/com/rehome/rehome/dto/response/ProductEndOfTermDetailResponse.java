package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.ProductStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductEndOfTermDetailResponse {
    private String id;
    private String productName;
    private BigDecimal currentPrice;
    private ProductStatus status;
    private EndOfTermDecisionResponse endOfTermDecision; // null nếu không chờ xử lý cuối kỳ

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EndOfTermDecisionResponse {
        private Instant deadline;
        private EndOfTermChoice decision; // TAKE_BACK / RE_CONSIGN / UNCLAIMED, null nếu Member chưa chọn
    }
}