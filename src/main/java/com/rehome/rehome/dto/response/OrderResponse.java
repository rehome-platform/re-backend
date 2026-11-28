package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {
    private java.time.Instant handedOverAt;
    private String id;
    private String orderNo;
    private String warehouseId;
    private String shippingRecipientName;
    private String shippingPhone;
    private String shippingAddressDetail;
    private String shippingWardName;
    private String shippingProvinceName;
    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private String status;
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    private String voucherCode;
    private String voucherName;

    private Instant paymentDeadline;
    private Instant addressChangedAt;

    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;
}
