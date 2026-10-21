package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.OrderRevenueResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.WalletTransactionResponse;

import java.util.UUID;

public interface WalletService {
    PageResponse<WalletTransactionResponse> getTransactions(
            String email, String type, String direction, int page, int size);

    WalletTransactionResponse getTransactionDetail(String email, UUID transactionId);

    PageResponse<OrderRevenueResponse> getRevenue(String email, int page, int size);
}
