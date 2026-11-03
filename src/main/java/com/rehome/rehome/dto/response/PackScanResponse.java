package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PackScanResponse {
    private Boolean matched;
    private Integer scannedCount;
    private Integer totalCount;

    // SKU vừa quét đúng, null khi quét sai (matched = false)
    private String scannedProductId;

    // Chỉ có giá trị khi matched = false
    private WrongItemDetail wrongItem;
}
