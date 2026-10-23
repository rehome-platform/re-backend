package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GhnWard {

    // Là chuỗi, có thể có số 0 ở đầu
    @JsonProperty("WardCode")
    private String wardCode;

    @JsonProperty("WardName")
    private String wardName;

    // 0: khóa, 1: chỉ lấy hàng, 2: chỉ giao hàng, 3: lấy + giao
    @JsonProperty("SupportType")
    private Integer supportType;
}
