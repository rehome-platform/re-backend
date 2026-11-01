package com.rehome.rehome.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Màn chi tiết / danh sách yêu cầu rút hàng của Member (F-032 -> F-035).
 *
 * <p>Khác {@link ItemWithdrawalResponse} (trả ngay sau khi tạo, chỉ có cái app cần để điều
 * hướng): ở đây có thêm tên món, tên kho và tình trạng phí để hiện đủ một màn chi tiết.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemWithdrawalDetailResponse {

    private String id;
    private String productId;
    private String productName;

    /** PENDING_PICKUP / COMPLETED / EXPIRED */
    private String status;

    /** Phí rút hàng; 0 nếu rút trong cửa sổ 7 ngày miễn phí hoặc hàng được miễn phí */
    private BigDecimal feeAmount;

    /** true khi không còn gì phải trả: phí bằng 0, hoặc đã thanh toán xong */
    private boolean feePaid;

    /** WALLET / MOMO / VNPAY; null khi chưa trả hoặc không phải trả */
    private String paymentMethod;

    private Instant pickupDeadline;
    private String warehouseName;
    private Instant createdAt;
}
