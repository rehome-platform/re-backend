package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Một đơn đã bán thuộc kho moderator đang quản lý. Khác SoldOrderResponse (phía người
 * ký gửi): ở đây là TOÀN BỘ đơn của kho, không lọc theo người bán, và có thông tin người
 * nhận để moderator đóng gói/giao hàng - thứ SoldOrderResponse cố tình giấu.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseOrderResponse {
    private String id;
    private String orderNo;
    private String status;
    private Instant createdAt;
    private BigDecimal totalAmount;

    private int itemCount;
    private String firstItemName;
    private String firstItemThumbnail;

    private String buyerName;
    private String buyerPhone;

    // Tiến độ quét SKU trước khi tạo vận đơn - xem packScan (API 69, F-127)
    private int scannedCount;
    private int totalCount;
    private String ghnTrackingCode;

    private Instant deliveredAt;
    private Instant completedAt;
    private Instant cancelledAt;
}
