package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Kết quả gửi nhiều nháp cùng lúc: mỗi món thành công hoặc lỗi riêng, không món nào làm hỏng món khác. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitConsignmentFormsResponse {
    private UUID warehouseId;
    private int successCount;
    private int failureCount;
    private List<Result> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private UUID formId;
        private boolean success;
        // Trạng thái hiện tại của form; null nếu không tìm thấy form
        private ConsignmentFormStatus status;
        // Chỉ có khi success = true
        private String formNo;
        // Chỉ có khi success = false: tên ErrorCode và lý do
        private String errorCode;
        private String message;
    }
}
