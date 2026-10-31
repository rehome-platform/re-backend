package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DecideDegradedItemCaseRequest {
    // FULL_COMPENSATION / PARTIAL_WITH_ITEM_BACK
    private String choice;
}
