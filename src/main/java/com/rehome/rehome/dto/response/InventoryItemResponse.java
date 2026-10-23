package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.ProductStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class InventoryItemResponse {
    private String id;
    private String name;
    private String shelfLocation;
    private ProductStatus status;
}