package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lý do từ chối yêu cầu đổi thông tin pháp lý (API 131, F-140).
 * Bắt buộc để tổ chức biết phải sửa gì rồi xin đổi lại.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectLegalChangeRequest {
    private String reason;
}
