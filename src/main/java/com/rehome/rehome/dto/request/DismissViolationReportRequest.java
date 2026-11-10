package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DismissViolationReportRequest {
    // Lý do Moderator bỏ qua, ví dụ "không đủ căn cứ"
    private String reason;
}
