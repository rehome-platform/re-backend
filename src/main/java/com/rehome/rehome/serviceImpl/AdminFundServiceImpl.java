package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.FundLedgerEntryResponse;
import com.rehome.rehome.dto.response.FundReconciliationResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.repository.AdminReportQueryRepository;
import com.rehome.rehome.service.AdminFundService;
import com.rehome.rehome.util.AdminDateRange;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminFundServiceImpl implements AdminFundService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AdminReportQueryRepository reportRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FundLedgerEntryResponse> ledger(String from, String to, WalletTransactionType type,
                                                        int page, int size) {
        AdminDateRange range = AdminDateRange.optional(from, to);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));

        Page<WalletTransaction> result = reportRepository.findPlatformLedger(range.from(), range.to(), type, pageable);

        // Tài khoản liên quan = chủ ví Member của dòng đối ứng; nạp một lượt cho cả trang
        List<UUID> memberWalletIds = result.getContent().stream()
                .map(WalletTransaction::getCounterpartTransaction)
                .filter(Objects::nonNull)
                .filter(c -> c.getWalletType() == WalletType.MEMBER)
                .map(WalletTransaction::getWalletId)
                .distinct()
                .toList();
        Map<UUID, Wallet> wallets = reportRepository.findWalletsWithMember(memberWalletIds);

        return PageResponse.from(result.map(t -> toEntry(t, wallets)));
    }

    @Override
    @Transactional(readOnly = true)
    public FundReconciliationResponse reconciliation() {
        BigDecimal fundBalance = reportRepository.sumFundBalance();
        BigDecimal ledgerBalance = reportRepository.sumSuccessfulTransactions(WalletType.PLATFORM);
        BigDecimal memberWalletBalance = reportRepository.sumMemberWalletBalance();
        BigDecimal memberLedgerBalance = reportRepository.sumSuccessfulTransactions(WalletType.MEMBER);
        long unmatched = reportRepository.countMemberTransactionsWithoutCounterpart();
        long unbalancedPairs = reportRepository.countUnbalancedCounterpartPairs();

        return buildReconciliation(fundBalance, ledgerBalance, memberWalletBalance, memberLedgerBalance,
                unmatched, unbalancedPairs, Instant.now());
    }

    static FundReconciliationResponse buildReconciliation(BigDecimal fundBalance, BigDecimal ledgerBalance,
                                                          BigDecimal memberWalletBalance,
                                                          BigDecimal memberLedgerBalance,
                                                          long unmatchedEntries, long unbalancedPairs,
                                                          Instant checkedAt) {
        BigDecimal difference = fundBalance.subtract(ledgerBalance);
        BigDecimal memberDifference = memberWalletBalance.subtract(memberLedgerBalance);
        // Sổ kép: mọi bút toán Member đều có dòng quỹ ngược dấu nên tổng hai phía phải bằng 0
        BigDecimal ledgerNetTotal = ledgerBalance.add(memberLedgerBalance);

        boolean balanced = difference.signum() == 0
                && memberDifference.signum() == 0
                && ledgerNetTotal.signum() == 0
                && unmatchedEntries == 0
                && unbalancedPairs == 0;

        return FundReconciliationResponse.builder()
                .status(balanced ? FundReconciliationResponse.Status.BALANCED
                        : FundReconciliationResponse.Status.MISMATCHED)
                .difference(difference)
                .fundBalance(fundBalance)
                .ledgerBalance(ledgerBalance)
                .memberWalletBalance(memberWalletBalance)
                .memberLedgerBalance(memberLedgerBalance)
                .memberDifference(memberDifference)
                .ledgerNetTotal(ledgerNetTotal)
                .unmatchedEntries(unmatchedEntries)
                .unbalancedPairs(unbalancedPairs)
                .checkedAt(checkedAt)
                .build();
    }

    private static FundLedgerEntryResponse toEntry(WalletTransaction t, Map<UUID, Wallet> wallets) {
        WalletTransaction counterpart = t.getCounterpartTransaction();

        FundLedgerEntryResponse.RelatedAccount account = null;
        FundLedgerEntryResponse.ReferenceTransaction reference = null;
        if (counterpart != null) {
            Wallet wallet = wallets.get(counterpart.getWalletId());
            if (wallet != null) {
                account = FundLedgerEntryResponse.RelatedAccount.builder()
                        .accountId(wallet.getMember().getId())
                        .email(wallet.getMember().getEmail())
                        .build();
            }
            reference = FundLedgerEntryResponse.ReferenceTransaction.builder()
                    .id(counterpart.getId())
                    .transactionNo(counterpart.getTransactionNo())
                    .type(counterpart.getType())
                    .amount(counterpart.getAmount())
                    .build();
        }

        // Hoa hồng của món hiện ngay trên dòng trả tiền người bán (bút toán COMMISSION_REVENUE riêng nằm ở dòng riêng của sổ)
        BigDecimal commission = null;
        OrderItem item = t.getRelatedOrderItem();
        if (t.getType() == WalletTransactionType.ORDER_PAYOUT && item != null) {
            commission = item.getPriceAtOrder().multiply(item.getCommissionRateApplied());
        }

        return FundLedgerEntryResponse.builder()
                .id(t.getId())
                .transactionNo(t.getTransactionNo())
                .type(t.getType())
                .amount(t.getAmount())
                .balanceAfter(t.getBalanceAfter())
                .status(t.getStatus())
                .note(t.getNote())
                .createdAt(t.getCreatedAt())
                .relatedAccount(account)
                .referenceTransaction(reference)
                .relatedOrderId(t.getRelatedOrder() == null ? null : t.getRelatedOrder().getId())
                .relatedOrderNo(t.getRelatedOrder() == null ? null : t.getRelatedOrder().getOrderNo())
                .relatedPayoutRequestId(
                        t.getRelatedPayoutRequest() == null ? null : t.getRelatedPayoutRequest().getId())
                .relatedItemWithdrawalRequestId(t.getRelatedItemWithdrawalRequest() == null
                        ? null : t.getRelatedItemWithdrawalRequest().getId())
                .relatedDegradedItemCaseId(
                        t.getRelatedDegradedItemCase() == null ? null : t.getRelatedDegradedItemCase().getId())
                .commissionAmount(commission)
                .build();
    }
}
