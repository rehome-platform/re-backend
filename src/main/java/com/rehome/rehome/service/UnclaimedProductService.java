package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.ConfirmTransferRequest;
import com.rehome.rehome.dto.response.ProductStatusResponse;
import com.rehome.rehome.entity.auth.Account;

public interface UnclaimedProductService {

    // API 36: xác nhận đã chuyển hàng vô thừa nhận cho tổ chức từ thiện, ghi audit log
    ProductStatusResponse confirmTransfer(String productId, ConfirmTransferRequest request, Account moderator);
}