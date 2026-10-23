package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GhnOrderItem {
    private String name;
    private String code;
    private Integer quantity;
    private Integer price;
}
