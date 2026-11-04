package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectOrganizationRequest {
    // Lý do để tổ chức biết phải sửa gì rồi nộp lại (API 4, F-003)
    private String reason;

    // Chấm từng giấy tờ đạt/chưa đạt khi từ chối, để tổ chức biết đúng giấy tờ nào cần sửa
    // thay vì chỉ có 1 lý do chung. Tùy chọn - bỏ trống thì giữ nguyên accepted cũ của giấy tờ.
    private List<DocumentDecision> documents;

    public RejectOrganizationRequest(String reason) {
        this.reason = reason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DocumentDecision {
        private UUID documentId;
        private Boolean accepted;
    }
}
