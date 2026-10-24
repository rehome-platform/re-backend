package com.rehome.rehome.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Body cho POST /v2/shipping-order/fee - field snake_case theo đúng chuẩn GHN.
// Dùng hệ địa chỉ mới (ward_name/province_name + is_new_*_address) giống GhnCreateOrderRequest,
// vì GHN sandbox của shop này đã xác nhận nhận được hệ mới qua API Tạo đơn (F-127).
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GhnCalculateFeeRequest {

    @JsonProperty("to_ward_name")
    private String toWardName;
    @JsonProperty("to_district_name")
    private String toDistrictName;
    @JsonProperty("to_province_name")
    private String toProvinceName;
    @JsonProperty("is_new_to_address")
    private Boolean isNewToAddress;

    @JsonProperty("from_ward_name")
    private String fromWardName;
    @JsonProperty("from_district_name")
    private String fromDistrictName;
    @JsonProperty("from_province_name")
    private String fromProvinceName;
    @JsonProperty("is_new_from_address")
    private Boolean isNewFromAddress;

    private Integer weight;
    private Integer length;
    private Integer width;
    private Integer height;

    @JsonProperty("service_type_id")
    private Integer serviceTypeId;

    @JsonProperty("insurance_value")
    private Integer insuranceValue;
}
