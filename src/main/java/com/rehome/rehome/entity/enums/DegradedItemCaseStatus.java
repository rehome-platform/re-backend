package com.rehome.rehome.entity.enums;

/**
 * Trạng thái ca xử lý hàng xuống cấp (F-036) nhìn từ phía Member.
 *
 * <p>KHÔNG phải cột trong DB: suy ra từ {@code DegradedItemCase.choice} — chưa chọn thì
 * còn chờ Member quyết định, đã chọn thì xong. Có enum để API lọc được {@code ?status=}
 * và để app không phải tự đoán từ việc choice có null hay không.
 */
public enum DegradedItemCaseStatus {

    /** choice = null — đang chờ Member chọn nhận đền đủ hay nhận lại hàng kèm đền một phần */
    PENDING_DECISION,

    /** choice != null — Member đã quyết định, tiền đền đã vào ví (F-162) */
    DECIDED
}
