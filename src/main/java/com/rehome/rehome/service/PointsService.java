package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.RedeemPointsRequest;
import com.rehome.rehome.dto.response.PointsSummaryResponse;
import com.rehome.rehome.entity.marketplace.Order;

public interface PointsService {

    PointsSummaryResponse getSummary(String email);

    PointsSummaryResponse redeem(String email, RedeemPointsRequest request);

    int awardForOrder(Order order);
}
