package com.rehome.rehome.service;

import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.dto.request.CreateItemWithdrawalRequest;
import com.rehome.rehome.dto.request.PayItemWithdrawalFeeRequest;
import com.rehome.rehome.dto.request.RejectItemWithdrawalRequest;
import com.rehome.rehome.dto.response.ItemWithdrawalDetailResponse;
import com.rehome.rehome.dto.response.ItemWithdrawalResponse;
import com.rehome.rehome.dto.response.PayItemWithdrawalFeeResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.List;
import java.util.UUID;

public interface ItemWithdrawalRequestService {
    void refundDonationFee(ItemWithdrawalRequest withdrawal);


    // API 90 (F-032) - Member yêu cầu rút lại một sản phẩm đang ký gửi
    ItemWithdrawalResponse create(Account account, CreateItemWithdrawalRequest request);

    // Moderator của kho giữ sản phẩm duyệt yêu cầu rút hàng -> PENDING_PICKUP
    ItemWithdrawalResponse approve(Account moderator, UUID id);

    // Moderator từ chối, bắt buộc có lý do -> REJECTED, sản phẩm trả lại ON_SALE
    ItemWithdrawalResponse reject(Account moderator, UUID id, RejectItemWithdrawalRequest request);

    // API 91 (F-033) - Member trả phí rút hàng bằng ví, MoMo hoặc VNPay (chọn đúng 1, không chia nhỏ)
    PayItemWithdrawalFeeResponse payFee(
            Account account, UUID id, PayItemWithdrawalFeeRequest request, String clientIp);

    // Webhook MoMo/VNPay gọi khi có kết quả thanh toán phí rút hàng; trả về true nếu khớp một yêu cầu
    boolean applyMomoFeePaymentResult(String momoOrderId, int resultCode, String transId);

    boolean applyVnPayFeePaymentResult(String vnpTxnRef, String responseCode, String transactionNo);

    // Member xem chi tiết một yêu cầu rút hàng của mình
    ItemWithdrawalDetailResponse getMine(Account account, UUID id);

    // Member xem danh sách yêu cầu rút hàng của mình; status = null/rỗng thì lấy tất cả
    List<ItemWithdrawalDetailResponse> listMine(Account account, String status);
}
