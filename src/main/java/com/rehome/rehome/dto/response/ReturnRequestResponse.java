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
public class ReturnRequestResponse {
    private String id;
    private String orderId;
    private String orderNo;
    private String status;
    private String reason;
    private List<String> photoUrls;
    private List<String> packingPhotoUrls;

    private String ghnReturnTrackingCode;
    private boolean ghnFallbackUsed;
    private Instant handoverDeadline;

    private String invalidReturnChoice;
    private Instant pickupDeadline;

    private String resolutionNote;
    private Boolean matchesOriginal;
    private Boolean degraded;
    private Boolean counterfeitSuspected;
    private String conditionNote;
    private String itemAction;
    private BigDecimal refundAmount;

    private Instant createdAt;
    private Instant resolvedAt;
    private Instant pickedUpAt;

    private String shipBackStatus;
    private BigDecimal shipBackFee;
    private Instant shipBackFeeDeadline;
    private Instant shipBackPaidAt;
    private String shipBackPaymentMethod;
    private Instant shipBackShipDeadline;
    private String shipBackTrackingCode;
    private Instant shipBackShippedAt;
    private Instant shipBackDeliveredAt;
    private Instant shipBackFailedAt;
    private String shipBackAddressId;
    private String shipBackRecipientName;
    private String shipBackPhone;
    private String shipBackAddressDetail;
    private String shipBackWardName;
    private String shipBackProvinceName;

    private String buyerName;
    private String buyerPhone;
    private String sellerName;

    private List<String> initialConsignmentPhotoUrls;

    private List<ReturnRequestHistoryItem> returnHistory;

    private BigDecimal compensationAmount;
    private BigDecimal halfCompensationAmount;

    private String sku;
    private String productName;
    private String brandName;
    private String size;

    private String sellerCode;

    private Short currentTerm;
    private Integer daysIntoTerm;
    private Integer termLengthDays;

    private BigDecimal commissionRate;
    private BigDecimal degradedCompensationRate;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ReturnRequestHistoryItem {
        private String id;
        private String status;
        private Boolean degraded;
        private Instant createdAt;
        private Instant resolvedAt;
    }
}
