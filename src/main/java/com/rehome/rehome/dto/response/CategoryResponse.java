package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.enums.TryOnSlot;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private UUID id;
    private String name;
    private TryOnSlot tryonSlot;
    private CatalogStatus status;
}