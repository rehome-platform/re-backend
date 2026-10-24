package com.rehome.rehome.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Chỉ được gửi đúng 1 trong 3 field
@Getter
@Setter
@NoArgsConstructor
public class CounterLookupRequest {
    private String memberCode;
    private String phone;
    private String qrPayload;
}