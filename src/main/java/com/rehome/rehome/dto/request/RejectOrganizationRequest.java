package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectOrganizationRequest {
    // Lý do để tổ chức biết phải sửa gì rồi nộp lại (API 4, F-003)
    private String reason;
}
