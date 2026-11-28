package com.rehome.rehome.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GhnCalculateFeeRequest {

    @JsonProperty("is_new_to_address")
    private Boolean isNewToAddress;
    @JsonProperty("to_ward_id_v2")
    private Integer toWardIdV2;
    @JsonProperty("to_address_v2")
    private String toAddressV2;

    @JsonProperty("is_new_from_address")
    private Boolean isNewFromAddress;
    @JsonProperty("from_ward_id_v2")
    private Integer fromWardIdV2;
    @JsonProperty("from_address_v2")
    private String fromAddressV2;

    private Integer weight;
    private Integer length;
    private Integer width;
    private Integer height;

    @JsonProperty("service_type_id")
    private Integer serviceTypeId;

    @JsonProperty("insurance_value")
    private Integer insuranceValue;
}
