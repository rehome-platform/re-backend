package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.CatalogStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CatalogStatusResponse {
    private CatalogStatus status;
}