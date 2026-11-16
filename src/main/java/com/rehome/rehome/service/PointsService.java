package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.RedeemPointsRequest;
import com.rehome.rehome.dto.response.PointsSummaryResponse;
import com.rehome.rehome.entity.marketplace.Order;

public interface PointsService {

    PointsSummaryResponse getSummary(String email);

    PointsSummaryResponse redeem(String email, RedeemPointsRequest request);

    // Cộng điểm cho người mua của đơn. Chỉ gọi từ OrderServiceImpl.payoutOrder (đã idempotent qua payoutAt)
    // nên đơn trả hàng/hủy không bao giờ được cộng và không cần luồng thu hồi điểm.
    // Trả số điểm đã cộng (0 nếu tính năng tắt hoặc đơn quá nhỏ).
    int awardForOrder(Order order);
}
