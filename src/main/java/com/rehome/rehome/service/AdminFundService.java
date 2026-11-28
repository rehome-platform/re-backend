package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.FundLedgerEntryResponse;
import com.rehome.rehome.dto.response.FundReconciliationResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.enums.WalletTransactionType;

public interface AdminFundService {

    PageResponse<FundLedgerEntryResponse> ledger(String from, String to, WalletTransactionType type,
                                                 int page, int size);

    FundReconciliationResponse reconciliation();
}
