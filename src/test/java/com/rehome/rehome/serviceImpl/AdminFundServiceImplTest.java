package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.FundReconciliationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AdminFundServiceImplTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static FundReconciliationResponse build(String fund, String ledger, String wallets, String memberLedger,
                                                    long unmatched, long unbalanced) {
        return AdminFundServiceImpl.buildReconciliation(d(fund), d(ledger), d(wallets), d(memberLedger),
                unmatched, unbalanced, Instant.parse("2026-11-11T00:00:00Z"));
    }

    @Test
    @DisplayName("đối soát: quỹ khớp sổ, sổ kép cân, không dòng mồ côi -> BALANCED, chênh lệch 0")
    void balanced() {
        // Quỹ = -(tổng ví Member): hai phía ngược dấu nhau
        FundReconciliationResponse r = build("-500000", "-500000", "500000", "500000", 0, 0);

        assertThat(r.getStatus()).isEqualTo(FundReconciliationResponse.Status.BALANCED);
        assertThat(r.getDifference()).isEqualByComparingTo("0");
        assertThat(r.getLedgerNetTotal()).isEqualByComparingTo("0");
        assertThat(r.getCheckedAt()).isEqualTo(Instant.parse("2026-11-11T00:00:00Z"));
    }

    @Test
    @DisplayName("đối soát: số dư quỹ lệch sổ -> MISMATCHED, difference = số dư quỹ - tổng sổ")
    void fundDiffersFromLedger() {
        FundReconciliationResponse r = build("-499000", "-500000", "500000", "500000", 0, 0);

        assertThat(r.getStatus()).isEqualTo(FundReconciliationResponse.Status.MISMATCHED);
        assertThat(r.getDifference()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("đối soát: có bút toán Member chưa có đối ứng hoặc cặp không triệt tiêu -> MISMATCHED")
    void orphanOrUnbalancedPair() {
        assertThat(build("-500000", "-500000", "500000", "500000", 1, 0).getStatus())
                .isEqualTo(FundReconciliationResponse.Status.MISMATCHED);
        assertThat(build("-500000", "-500000", "500000", "500000", 0, 2).getStatus())
                .isEqualTo(FundReconciliationResponse.Status.MISMATCHED);
    }

    @Test
    @DisplayName("đối soát: tổng ví Member lệch sổ ví -> MISMATCHED, memberDifference khác 0")
    void memberWalletsDifferFromLedger() {
        FundReconciliationResponse r = build("-500000", "-500000", "520000", "500000", 0, 0);

        assertThat(r.getStatus()).isEqualTo(FundReconciliationResponse.Status.MISMATCHED);
        assertThat(r.getMemberDifference()).isEqualByComparingTo("20000");
    }
}
