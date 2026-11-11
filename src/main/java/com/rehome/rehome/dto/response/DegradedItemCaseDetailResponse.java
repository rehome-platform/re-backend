package com.rehome.rehome.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Màn Member xem một ca hàng xuống cấp trước khi quyết định (F-036).
 *
 * <p>Khác {@link DegradedItemCaseResponse} (trả sau khi quyết định ở API 95): ở đây có ảnh,
 * lý do và ghi chú của Moderator — những thứ Member cần để biết món hàng xuống cấp thế nào.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DegradedItemCaseDetailResponse {

    private String id;
    private String productId;
    private String productName;

    /**
     * Ảnh bằng chứng của yêu cầu trả hàng dẫn đến ca này (người mua chụp lúc gửi yêu cầu).
     * Rỗng nếu ca không gắn với yêu cầu trả hàng nào.
     */
    private List<String> photos;

    /** Lý do người mua trả hàng, lấy từ yêu cầu trả hàng */
    private String reason;

    /** Ghi chú của Moderator lúc kiểm hàng về (conditionNote) — vì sao kết luận xuống cấp */
    private String moderatorNote;

    /** Mức đền đủ, snapshot lúc mở ca. Nhận lại hàng thì chỉ được một phần số này */
    private BigDecimal compensationAmount;

    /** Tỷ lệ áp dụng khi chọn PARTIAL_WITH_ITEM_BACK, snapshot trên biên nhận - để mobile tính
     * trước paidAmount dự kiến (= compensationAmount x rate) trước khi Member quyết định */
    private BigDecimal degradedCompensationRate;

    /** Số tiền thực tế đã cộng vào ví; null khi chưa quyết định */
    private BigDecimal paidAmount;

    /** Thời điểm Moderator kết luận hàng xuống cấp và mở ca - alias của createdAt */
    private Instant receivedAt;

    /** Thời điểm Member quyết định; null khi chưa quyết định */
    private Instant decidedAt;

    /** Hạn Member chọn phương án đền bù, mặc định 30 ngày kể từ lúc mở ca (F-036); quá hạn tự
     * chuyển vô thừa nhận. Hệ thống gửi nhắc nhở cách 7 ngày cho tới hạn này. */
    private Instant decisionDeadline;

    /**
     * LUÔN null: hạn để Member đến kho lấy hàng sau khi chọn PARTIAL_WITH_ITEM_BACK.
     * F-036 chưa quy định hạn này, không có cột trong DB và không có job tự đóng ca quá hạn.
     * Giữ field để app không phải đổi contract khi có hạn thật.
     */
    private Instant pickupDeadline;

    /** true khi quá decisionDeadline mà Member không chọn - hệ thống tự gán UNCLAIMED, không
     * phải Member chọn qua API 95 */
    private boolean autoDecided;

    /** Thời điểm Member đến quầy nhận lại hàng sau khi chọn PARTIAL_WITH_ITEM_BACK (Counter
     * handover, context DEGRADED_ITEM_PICKUP); null nếu chưa đến lấy hoặc không áp dụng
     * (FULL_COMPENSATION/UNCLAIMED không có bước nhận lại hàng) */
    private Instant pickedUpAt;

    /** PENDING_DECISION / DECIDED, suy ra từ decision */
    private String status;

    /** FULL_COMPENSATION / PARTIAL_WITH_ITEM_BACK; null khi chưa quyết định */
    private String decision;
}
