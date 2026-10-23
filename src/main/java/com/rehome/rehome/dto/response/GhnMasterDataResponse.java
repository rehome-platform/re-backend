package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// Shape chung của các API master-data GHN: { code, message, data: [...] }
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GhnMasterDataResponse<T> {

    private Integer code;
    private String message;
    private List<T> data;
}
