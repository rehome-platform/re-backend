package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderDetailResponse {
    private String id;
    private String orderNo;
    private String status;

    private Instant createdAt;
    private String paymentMethod;
    private Instant paidAt;

    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;

    private String warehouseName;
    private String shippingRecipientName;
    private String shippingPhone;
    private String shippingAddressDetail;

    private List<OrderDetailItemResponse> items;

    private String ghnTrackingCode;
    private Instant deliveredAt;
    private Instant autoCompleteDeadline;
    private Short serviceRating;
    private Instant ratedAt;

    private String cancelReason;
    private Instant cancelledAt;
    private BigDecimal refundAmount;
}
