package com.rehome.rehome.dto.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CounterLookupRequest {
    private String memberCode;
    private String phone;
    private String qrPayload;
}