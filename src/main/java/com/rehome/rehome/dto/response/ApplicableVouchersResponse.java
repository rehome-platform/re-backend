package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.VoucherType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicableVouchersResponse {

    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    @Builder.Default
    private List<Item> vouchers = List.of();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Item {
        private String id;
        private String code;
        private String name;
        private String description;
        private VoucherType type;
        private BigDecimal minOrderAmount;
        private Instant expiresAt;
        private boolean applicable;
        private BigDecimal discountAmount;
        private String reason;
    }
}
