package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.FundLedgerEntryResponse;
import com.rehome.rehome.dto.response.FundReconciliationResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.enums.WalletTransactionType;

public interface AdminFundService {

    /** Sổ quỹ nền tảng: các bút toán đối ứng phía quỹ, mới nhất trước. from/to/type tùy chọn. */
    PageResponse<FundLedgerEntryResponse> ledger(String from, String to, WalletTransactionType type,
                                                 int page, int size);

    /** Đối soát quỹ nền tảng với sổ bút toán tại thời điểm gọi. */
    FundReconciliationResponse reconciliation();
}
