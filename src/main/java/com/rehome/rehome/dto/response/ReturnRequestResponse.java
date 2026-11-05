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

    private String ghnReturnTrackingCode;
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

    // Người mua / người bán (F-036, TH-04..TH-07) - để Moderator xem chi tiết lúc kiểm hàng
    private String buyerName;
    private String buyerPhone;
    private String sellerName;

    // Ảnh sản phẩm lúc ký gửi/đăng bán, để so sánh với ảnh hàng trả về
    private List<String> initialConsignmentPhotoUrls;

    // Các yêu cầu trả hàng khác (nếu có) của cùng đơn, mới nhất trước
    private List<ReturnRequestHistoryItem> returnHistory;

    // Snapshot mức đền (giá bán - hoa hồng áp dụng lúc đặt đơn) và mức đền một phần nếu Member
    // chọn nhận lại hàng - chỉ có giá trị khi hàng bị đánh dấu degraded (API 85)
    private BigDecimal compensationAmount;
    private BigDecimal halfCompensationAmount;

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
