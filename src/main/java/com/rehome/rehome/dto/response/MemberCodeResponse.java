package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MemberCodeResponse {
    private String memberCode;

    // Chuỗi để app Member tự vẽ QR, backend không render ảnh
    private String qrPayload;
}