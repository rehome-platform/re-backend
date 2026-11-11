package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

/** GET /api/admin/fund/reconciliation - kết quả đối soát quỹ nền tảng với sổ bút toán, tính tại thời điểm gọi. */
@Getter
@Builder
@AllArgsConstructor
public class FundReconciliationResponse {

    public enum Status { BALANCED, MISMATCHED }

    // BALANCED khi mọi kiểm tra bên dưới đều khớp
    private final Status status;
    // Số chênh lệch chính: số dư quỹ hiện tại - tổng bút toán thành công phía quỹ (0 là khớp)
    private final BigDecimal difference;
    private final BigDecimal fundBalance;
    private final BigDecimal ledgerBalance;
    // Tổng số dư ví Member - tổng bút toán thành công phía ví Member (0 là khớp)
    private final BigDecimal memberWalletBalance;
    private final BigDecimal memberLedgerBalance;
    private final BigDecimal memberDifference;
    // Tổng mọi bút toán thành công của cả hai phía; sổ kép luôn phải bằng 0
    private final BigDecimal ledgerNetTotal;
    // Bút toán Member chưa có dòng đối ứng ở quỹ
    private final long unmatchedEntries;
    // Cặp đối ứng có số tiền hai dòng không triệt tiêu nhau
    private final long unbalancedPairs;
    // Thời điểm chạy kiểm tra
    private final Instant checkedAt;
}
