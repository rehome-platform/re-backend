package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Cùng hình dạng với OrderListResponse/SoldOrderListResponse: danh sách phân trang + số
 * lượng theo từng trạng thái cho thanh tab, nhưng đếm trên cả kho thay vì một buyer/seller.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WarehouseOrderListResponse {
    private PageResponse<WarehouseOrderResponse> orders;
    private Map<String, Long> statusCounts;
}
