package com.rehome.rehome.dto.request;

import lombok.*;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class QueryMomoRequest {
    private String partnerCode;
    private String requestId;
    private String orderId;
    private String lang;
    private String signature;
}
