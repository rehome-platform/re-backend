package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface ConsignmentReceiptService {
    ConsignmentReceiptResponse getMyReceipt(Account account, UUID id);
}
