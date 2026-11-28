package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.ConfirmTransferRequest;
import com.rehome.rehome.dto.response.ProductStatusResponse;
import com.rehome.rehome.entity.auth.Account;

public interface UnclaimedProductService {

    ProductStatusResponse confirmTransfer(String productId, ConfirmTransferRequest request, Account moderator);
}