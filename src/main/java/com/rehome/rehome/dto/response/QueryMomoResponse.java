package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueryMomoResponse {
    private String partnerCode;
    private String orderId;
    private String requestId;
    private long amount;
    private String extraData;
    private String orderInfo;
    private String orderType;
    private String payType;
    private long transId;
    private int resultCode;
    private String message;
    private long responseTime;
    private String signature;
}
