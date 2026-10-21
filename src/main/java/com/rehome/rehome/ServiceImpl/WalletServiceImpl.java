package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.request.CreatePayoutRequestRequest;
import com.rehome.rehome.dto.response.OrderRevenueResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.PayoutRequestResponse;
import com.rehome.rehome.dto.response.WalletBalanceResponse;
import com.rehome.rehome.dto.response.WalletTransactionResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.PayoutRequest;
import com.rehome.rehome.entity.marketplace.SystemConfig;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.PayoutRequestRepository;
import com.rehome.rehome.repository.SystemConfigRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import com.rehome.rehome.repository.spec.WalletTransactionSpecifications;
import com.rehome.rehome.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WalletServiceImpl implements WalletService {

    private static final String CONFIG_MIN_PAYOUT_AMOUNT = "min_payout_amount";
    private static final String CONFIG_MAX_PAYOUT_PER_DAY = "max_payout_requests_per_day";
    private static final BigDecimal DEFAULT_MIN_PAYOUT_AMOUNT = new BigDecimal("50000");
    private static final int DEFAULT_MAX_PAYOUT_PER_DAY = 3;

    private final AccountRepository accountRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final SystemConfigRepository systemConfigRepository;

    @Override
    public WalletBalanceResponse getBalance(String email) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Wallet wallet = walletRepository.findByMember_Id(member.getId()).orElse(null);
        BigDecimal balance = wallet != null ? wallet.getBalance() : BigDecimal.ZERO;
        BigDecimal lockedBalance = wallet != null ? wallet.getLockedBalance() : BigDecimal.ZERO;

        return WalletBalanceResponse.builder()
                .balance(balance)
                .lockedBalance(lockedBalance)
                .availableBalance(balance.subtract(lockedBalance))
                .build();
    }

    @Override
    public PageResponse<WalletTransactionResponse> getTransactions(
            String email, String type, String direction, int page, int size) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        List<WalletTransactionType> types = parseTypes(type);
        validateDirection(direction);

        Wallet wallet = walletRepository.findByMember_Id(member.getId()).orElse(null);
        if (wallet == null) {
            return PageResponse.from(Page.empty());
        }

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<WalletTransaction> result = walletTransactionRepository.findAll(
                WalletTransactionSpecifications.filter(wallet.getId(), types, direction), pageable);

        return PageResponse.from(result.map(this::toResponse));
    }

    @Override
    public WalletTransactionResponse getTransactionDetail(String email, UUID transactionId) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Wallet wallet = walletRepository.findByMember_Id(member.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch."));

        WalletTransaction transaction = walletTransactionRepository.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch."));

        if (transaction.getWalletType() != WalletType.MEMBER || !transaction.getWalletId().equals(wallet.getId())) {
            throw new ForbiddenException("Bạn không có quyền xem giao dịch này.");
        }

        return toResponse(transaction);
    }

    @Override
    public PageResponse<OrderRevenueResponse> getRevenue(String email, String month, int page, int size) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        YearMonth parsedMonth = parseMonth(month);

        Wallet wallet = walletRepository.findByMember_Id(member.getId()).orElse(null);
        if (wallet == null) {
            return PageResponse.from(Page.empty());
        }

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<WalletTransaction> spec = WalletTransactionSpecifications.filter(
                wallet.getId(), List.of(WalletTransactionType.ORDER_PAYOUT), null);

        if (parsedMonth != null) {
            Instant from = parsedMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant to = parsedMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            spec = spec.and(WalletTransactionSpecifications.createdInRange(from, to));
        }

        Page<WalletTransaction> result = walletTransactionRepository.findAll(spec, pageable);

        return PageResponse.from(result.map(this::toRevenueResponse));
    }

    @Override
    @Transactional
    public PayoutRequestResponse createPayoutRequest(String email, CreatePayoutRequestRequest request) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        BigDecimal amount = request.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("amount phải lớn hơn 0.");
        }

        BigDecimal minAmount = getConfigAmount(CONFIG_MIN_PAYOUT_AMOUNT, DEFAULT_MIN_PAYOUT_AMOUNT);
        if (amount.compareTo(minAmount) < 0) {
            throw new ConflictException(
                    "BELOW_MIN_PAYOUT: số tiền rút dưới mức tối thiểu.", "BELOW_MIN_PAYOUT");
        }

        Wallet wallet = walletRepository.findByMemberIdForUpdate(member.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy ví."));

        BigDecimal availableBalance = wallet.getBalance().subtract(wallet.getLockedBalance());
        if (amount.compareTo(availableBalance) > 0) {
            throw new ConflictException(
                    "INSUFFICIENT_BALANCE: ví không đủ số dư khả dụng.", "INSUFFICIENT_BALANCE");
        }

        int maxPerDay = getConfigInt(CONFIG_MAX_PAYOUT_PER_DAY, DEFAULT_MAX_PAYOUT_PER_DAY);
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endOfDay = startOfDay.plusSeconds(24 * 60 * 60);
        long todayCount = payoutRequestRepository.countByWallet_IdAndCreatedAtBetween(
                wallet.getId(), startOfDay, endOfDay);
        if (todayCount >= maxPerDay) {
            throw new ConflictException(
                    "DAILY_PAYOUT_LIMIT_REACHED: đã đạt số lần rút tối đa trong ngày.", "DAILY_PAYOUT_LIMIT_REACHED");
        }

        wallet.setLockedBalance(wallet.getLockedBalance().add(amount));
        walletRepository.save(wallet);

        PayoutRequest payoutRequest = PayoutRequest.builder()
                .wallet(wallet)
                .amount(amount)
                .status(TransactionStatus.PENDING)
                .build();
        payoutRequestRepository.save(payoutRequest);

        simulatePayout(payoutRequest, wallet);
        payoutRequestRepository.save(payoutRequest);

        return toPayoutResponse(payoutRequest);
    }

    // Giả lập chi tiền: luôn SUCCESS ngay lập tức, chạy đồng bộ trong cùng transaction với tạo yêu cầu
    private void simulatePayout(PayoutRequest payoutRequest, Wallet wallet) {
        BigDecimal amount = payoutRequest.getAmount();

        wallet.setBalance(wallet.getBalance().subtract(amount));
        wallet.setLockedBalance(wallet.getLockedBalance().subtract(amount));
        walletRepository.save(wallet);

        WalletTransaction transaction = WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.MEMBER)
                .walletId(wallet.getId())
                .type(WalletTransactionType.WITHDRAWAL)
                .amount(amount.negate())
                .balanceAfter(wallet.getBalance())
                .status(TransactionStatus.SUCCESS)
                .note("Rút tiền về tài khoản ngân hàng.")
                .build();
        walletTransactionRepository.save(transaction);

        payoutRequest.setStatus(TransactionStatus.SUCCESS);
        payoutRequest.setProcessedAt(Instant.now());
    }

    private String generateTransactionNo() {
        String prefix = "WT-" + Year.now(ZoneOffset.UTC).getValue() + "-";
        long seq = walletTransactionRepository.countByTransactionNoStartingWith(prefix) + 1;
        return prefix + String.format("%06d", seq);
    }

    @Override
    public PayoutRequestResponse getPayoutRequestDetail(String email, UUID payoutRequestId) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Wallet wallet = walletRepository.findByMember_Id(member.getId())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút tiền."));

        PayoutRequest payoutRequest = payoutRequestRepository.findById(payoutRequestId)
                .filter(pr -> pr.getWallet().getId().equals(wallet.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu rút tiền."));

        return toPayoutResponse(payoutRequest);
    }

    @Override
    public PageResponse<PayoutRequestResponse> getPayoutRequests(String email, int page, int size) {

        Account member = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        Wallet wallet = walletRepository.findByMember_Id(member.getId()).orElse(null);
        if (wallet == null) {
            return PageResponse.from(Page.empty());
        }

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<PayoutRequest> result = payoutRequestRepository.findByWallet_Id(wallet.getId(), pageable);

        return PageResponse.from(result.map(this::toPayoutResponse));
    }

    private PayoutRequestResponse toPayoutResponse(PayoutRequest payoutRequest) {
        return PayoutRequestResponse.builder()
                .id(payoutRequest.getId() != null ? payoutRequest.getId().toString() : null)
                .amount(payoutRequest.getAmount())
                .status(payoutRequest.getStatus().name())
                .failureReason(payoutRequest.getFailureReason())
                .build();
    }

    private BigDecimal getConfigAmount(String key, BigDecimal defaultValue) {
        return systemConfigRepository.findByKey(key)
                .map(SystemConfig::getValue)
                .map(BigDecimal::new)
                .orElse(defaultValue);
    }

    private int getConfigInt(String key, int defaultValue) {
        return systemConfigRepository.findByKey(key)
                .map(SystemConfig::getValue)
                .map(Integer::parseInt)
                .orElse(defaultValue);
    }

    private YearMonth parseMonth(String month) {
        if (!StringUtils.hasText(month)) {
            return null;
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("month không hợp lệ, định dạng yyyy-MM.");
        }
    }

    private List<WalletTransactionType> parseTypes(String type) {
        if (!StringUtils.hasText(type)) {
            return List.of();
        }

        List<WalletTransactionType> types = new ArrayList<>();
        for (String token : type.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                types.add(WalletTransactionType.valueOf(trimmed.toUpperCase()));
            } catch (IllegalArgumentException ex) {
                throw new BadRequestException("type không hợp lệ: " + trimmed);
            }
        }
        return types;
    }

    private void validateDirection(String direction) {
        if (StringUtils.hasText(direction)
                && !"IN".equalsIgnoreCase(direction) && !"OUT".equalsIgnoreCase(direction)) {
            throw new BadRequestException("direction phải là IN hoặc OUT.");
        }
    }

    private WalletTransactionResponse toResponse(WalletTransaction tx) {
        return WalletTransactionResponse.builder()
                .id(tx.getId().toString())
                .transactionNo(tx.getTransactionNo())
                .type(tx.getType().name())
                .amount(tx.getAmount())
                .balanceAfter(tx.getBalanceAfter())
                .relatedOrderId(tx.getRelatedOrder() != null ? tx.getRelatedOrder().getId().toString() : null)
                .relatedOrderNo(tx.getRelatedOrder() != null ? tx.getRelatedOrder().getOrderNo() : null)
                .itemName(tx.getRelatedOrderItem() != null ? tx.getRelatedOrderItem().getProduct().getName() : null)
                .status(tx.getStatus().name())
                .note(tx.getNote())
                .createdAt(tx.getCreatedAt())
                .build();
    }

    private OrderRevenueResponse toRevenueResponse(WalletTransaction tx) {
        OrderItem item = tx.getRelatedOrderItem();
        BigDecimal salePrice = item != null ? item.getPriceAtOrder() : null;
        BigDecimal commissionRate = item != null ? item.getCommissionRateApplied() : null;
        BigDecimal commission = item != null
                ? item.getPriceAtOrder().multiply(item.getCommissionRateApplied())
                : null;
        BigDecimal tax = item != null
                ? item.getPriceAtOrder().multiply(item.getTaxRateApplied())
                : null;

        return OrderRevenueResponse.builder()
                .orderId(tx.getRelatedOrder() != null ? tx.getRelatedOrder().getId().toString() : null)
                .orderNo(tx.getRelatedOrder() != null ? tx.getRelatedOrder().getOrderNo() : null)
                .productName(item != null ? item.getProduct().getName() : null)
                .termNo(item != null ? item.getTermApplied() : null)
                .salePrice(salePrice)
                .commissionRate(commissionRate)
                .commission(commission)
                .tax(tax)
                .netAmount(tx.getAmount())
                .completedAt(tx.getRelatedOrder() != null ? tx.getRelatedOrder().getCompletedAt() : null)
                .build();
    }
}
