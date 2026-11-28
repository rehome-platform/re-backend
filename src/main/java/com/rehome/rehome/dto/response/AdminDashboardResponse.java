package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
@AllArgsConstructor
public class AdminDashboardResponse {
    private final long revenue;
    private final long ordersCount;
    private final long inventoryCount;
    private final double avgServiceRating;
    private final long walletBalanceHeld;
    private final long platformRevenue;
    private final long commissionRevenue;
    private final long withdrawalFeeRevenue;
    private final long returnShipBackFeeRevenue;
    private final long membershipRevenue;
    private final long compensationExpense;
    private final long fundBalance;
    private final long heldOrderAmount;
    private final long heldOrderCount;
    private final Map<String, Long> stockByStatus;
    private final long overdueTaskCount;

    private final long term1SalesRevenue;
    private final long term1Commission;
    private final long term2SalesRevenue;
    private final long term2Commission;
    private final long term3SalesRevenue;
    private final long term3Commission;

    private final long term1StockCount;
    private final long term2StockCount;
    private final long term3StockCount;

    private final long totalSoldItemsCount;
    private final double netProfitMarginPercent;
}
