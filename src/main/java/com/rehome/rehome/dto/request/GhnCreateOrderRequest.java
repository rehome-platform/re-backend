package com.rehome.rehome.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Body cho POST /v2/shipping-order/create - field snake_case theo đúng chuẩn GHN
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GhnCreateOrderRequest {

    @JsonProperty("to_name")
    private String toName;
    @JsonProperty("to_phone")
    private String toPhone;
    @JsonProperty("to_address")
    private String toAddress;
    @JsonProperty("to_ward_name")
    private String toWardName;
    @JsonProperty("to_district_name")
    private String toDistrictName;
    @JsonProperty("to_province_name")
    private String toProvinceName;
    @JsonProperty("is_new_to_address")
    private Boolean isNewToAddress;

    @JsonProperty("from_name")
    private String fromName;
    @JsonProperty("from_phone")
    private String fromPhone;
    @JsonProperty("from_address")
    private String fromAddress;
    @JsonProperty("from_ward_name")
    private String fromWardName;
    @JsonProperty("from_district_name")
    private String fromDistrictName;
    @JsonProperty("from_province_name")
    private String fromProvinceName;
    @JsonProperty("is_new_from_address")
    private Boolean isNewFromAddress;

    @JsonProperty("client_order_code")
    private String clientOrderCode;

    private Integer weight;
    private Integer length;
    private Integer width;
    private Integer height;

    private String content;

    @JsonProperty("service_type_id")
    private Integer serviceTypeId;
    @JsonProperty("payment_type_id")
    private Integer paymentTypeId;

    @JsonProperty("cod_amount")
    private Integer codAmount;
    @JsonProperty("order_value")
    private Integer orderValue;
    @JsonProperty("insurance_value")
    private Integer insuranceValue;

    @JsonProperty("required_note")
    private String requiredNote;

    private List<GhnOrderItem> items;
}
