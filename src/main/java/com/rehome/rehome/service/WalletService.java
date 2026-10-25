package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreatePayoutRequestRequest;
import com.rehome.rehome.dto.response.OrderRevenueResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.PayoutRequestResponse;
import com.rehome.rehome.dto.response.WalletBalanceResponse;
import com.rehome.rehome.dto.response.WalletConfigResponse;
import com.rehome.rehome.dto.response.WalletTransactionResponse;

import java.util.UUID;

public interface WalletService {
    WalletBalanceResponse getBalance(String email);

    // Hạn mức rút tiền Admin cấu hình + số lượt đã dùng hôm nay, cho màn rút tiền
    WalletConfigResponse getWalletConfig(String email);

    PageResponse<WalletTransactionResponse> getTransactions(
            String email, String type, String direction, int page, int size);

    WalletTransactionResponse getTransactionDetail(String email, UUID transactionId);

    PageResponse<OrderRevenueResponse> getRevenue(String email, String month, int page, int size);

    PayoutRequestResponse createPayoutRequest(String email, CreatePayoutRequestRequest request);

    PayoutRequestResponse getPayoutRequestDetail(String email, UUID payoutRequestId);

    PageResponse<PayoutRequestResponse> getPayoutRequests(String email, int page, int size);
}
