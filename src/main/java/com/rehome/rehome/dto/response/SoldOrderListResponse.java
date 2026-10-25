package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Cùng hình dạng với OrderListResponse (phía người mua) để FE dùng lại được component:
 * danh sách phân trang + số lượng theo từng trạng thái cho thanh tab.
 * statusCounts luôn đủ mọi OrderStatus, kể cả PENDING_PAYMENT và CANCELLED là hai
 * trạng thái bị loại khỏi danh sách mặc định.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SoldOrderListResponse {
    private PageResponse<SoldOrderResponse> orders;
    private Map<String, Long> statusCounts;
}
