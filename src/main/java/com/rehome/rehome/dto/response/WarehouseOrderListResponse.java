package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseOrderListResponse {
    private PageResponse<WarehouseOrderResponse> orders;
    private Map<String, Long> statusCounts;
}
