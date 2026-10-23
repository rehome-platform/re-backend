package com.rehome.rehome.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Payload GHN POST về theo "Callback trạng thái đơn" - field PascalCase theo đúng chuẩn GHN
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GhnWebhookPayload {

    @JsonProperty("OrderCode")
    private String orderCode;

    @JsonProperty("ClientOrderCode")
    private String clientOrderCode;

    @JsonProperty("Status")
    private String status;

    @JsonProperty("Reason")
    private String reason;

    @JsonProperty("ReasonCode")
    private String reasonCode;

    @JsonProperty("Description")
    private String description;

    @JsonProperty("Time")
    private String time;
}
