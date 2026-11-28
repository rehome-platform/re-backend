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
    private Instant handedOverAt;
    private List<String> packingPhotoUrls;
    private String id;
    private String orderNo;
    private String status;

    private Instant createdAt;
    private String paymentMethod;
    private Instant paidAt;

    private Instant paymentDeadline;

    private BigDecimal subtotal;
    private BigDecimal shippingFee;
    private BigDecimal discountAmount;
    private String voucherCode;
    private String voucherName;
    private BigDecimal totalAmount;

    private String warehouseId;
    private String warehouseName;

    private String shippingRecipientName;
    private String shippingPhone;
    private String shippingAddressDetail;
    private String shippingWardName;
    private String shippingProvinceName;

    private List<OrderDetailItemResponse> items;

    private String ghnTrackingCode;
    private Instant expectedDeliveryDate;
    private Instant shipmentCreatedAt;
    private Instant deliveredAt;
    private Instant autoCompleteDeadline;
    private Instant completedAt;
    private Short serviceRating;
    private Instant ratedAt;
    private String ratingComment;
    private List<String> ratingImageUrls;

    private String cancelReason;
    private Instant cancelledAt;
    private BigDecimal refundAmount;
    private Instant addressChangedAt;

    private String returnRequestId;

    private Instant returnDeadline;

    private List<String> scannedProductIds;
    private Integer scannedCount;
    private Integer totalCount;

    private List<String> allowedActions;

    private String claimedBy;

    private String buyerNote;
}
