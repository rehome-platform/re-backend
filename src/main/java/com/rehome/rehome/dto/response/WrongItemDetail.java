package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WrongItemDetail {
    private String productId;
    private String name;
    private String brandName;
    private String size;

    private String belongsToOrderNo;

    private String instructions;
}
