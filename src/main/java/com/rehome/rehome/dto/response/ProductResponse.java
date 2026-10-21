package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ProductResponse {
    private String id;
    private String name;
    private ProductStatus status;
}
