package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateItemWithdrawalRequest;
import com.rehome.rehome.dto.request.PayItemWithdrawalFeeRequest;
import com.rehome.rehome.dto.response.ItemWithdrawalResponse;
import com.rehome.rehome.dto.response.PayItemWithdrawalFeeResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface ItemWithdrawalRequestService {

    // API 90 (F-032) - Member yêu cầu rút lại một sản phẩm đang ký gửi
    ItemWithdrawalResponse create(Account account, CreateItemWithdrawalRequest request);

    // API 91 (F-033) - Member trả phí rút hàng bằng ví, MoMo hoặc VNPay (chọn đúng 1, không chia nhỏ)
    PayItemWithdrawalFeeResponse payFee(
            Account account, UUID id, PayItemWithdrawalFeeRequest request, String clientIp);

    // Webhook MoMo/VNPay gọi khi có kết quả thanh toán phí rút hàng; trả về true nếu khớp một yêu cầu
    boolean applyMomoFeePaymentResult(String momoOrderId, int resultCode, String transId);

    boolean applyVnPayFeePaymentResult(String vnpTxnRef, String responseCode, String transactionNo);
}
