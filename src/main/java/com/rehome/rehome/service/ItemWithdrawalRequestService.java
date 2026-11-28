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


    ItemWithdrawalResponse create(Account account, CreateItemWithdrawalRequest request);

    ItemWithdrawalResponse approve(Account moderator, UUID id);

    ItemWithdrawalResponse reject(Account moderator, UUID id, RejectItemWithdrawalRequest request);

    PayItemWithdrawalFeeResponse payFee(
            Account account, UUID id, PayItemWithdrawalFeeRequest request, String clientIp);

    boolean applyVnPayFeePaymentResult(String vnpTxnRef, String responseCode, String transactionNo);

    ItemWithdrawalDetailResponse getMine(Account account, UUID id);

    List<ItemWithdrawalDetailResponse> listMine(Account account, String status);
}
