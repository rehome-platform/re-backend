package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// GET /consignment-forms: trang form kèm số form đang chờ duyệt để mobile hiện "3/5 form chờ"
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsignmentFormPageResponse {
    private List<ConsignmentFormListItemResponse> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    // Tổng số form PENDING của Member, không phụ thuộc bộ lọc status/trang
    private long pendingCount;
    // Giới hạn tối đa (consignment.max-pending-forms)
    private int maxPending;
}
