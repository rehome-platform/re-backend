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

    /** Thời điểm Member quyết định; null khi chưa quyết định */
    private Instant decidedAt;

    /**
     * LUÔN null: hệ thống chưa có hạn cho Member quyết định (F-036 không quy định hạn,
     * không có cột trong DB và không có job đóng ca quá hạn). Giữ field để app không phải
     * đổi contract khi có hạn thật.
     */
    private Instant decisionDeadline;

    /** PENDING_DECISION / DECIDED, suy ra từ decision */
    private String status;

    /** FULL_COMPENSATION / PARTIAL_WITH_ITEM_BACK; null khi chưa quyết định */
    private String decision;
}
