package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreatePayoutRequestRequest;
import com.rehome.rehome.dto.response.OrderRevenueResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.PayoutRequestResponse;
import com.rehome.rehome.dto.response.WalletBalanceResponse;
import com.rehome.rehome.dto.response.WalletConfigResponse;
import com.rehome.rehome.dto.response.WalletTransactionResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.PayoutRequest;
import com.rehome.rehome.entity.marketplace.PlatformFund;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.SystemConfig;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.PayoutRequestRepository;
import com.rehome.rehome.repository.PlatformFundRepository;
import com.rehome.rehome.repository.SystemConfigRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock AccountRepository accountRepository;
    @Mock WalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock PayoutRequestRepository payoutRequestRepository;
    @Mock SystemConfigRepository systemConfigRepository;
    @Mock PlatformFundRepository platformFundRepository;

    @InjectMocks
    WalletServiceImpl walletService;

    Account member;
    String email;
    Wallet wallet;

    @BeforeEach
    void setup() {
        email = "member@example.com";
        member = Account.builder().id(UUID.randomUUID()).email(email).build();
        wallet = Wallet.builder().id(UUID.randomUUID()).member(member).balance(new BigDecimal("100000")).build();
    }

    private void stubRepositoryPage(Page<WalletTransaction> page) {
        when(walletTransactionRepository.findAll(
                ArgumentMatchers.<Specification<WalletTransaction>>any(), any(Pageable.class)))
                .thenReturn(page);
    }

    @Test
    void getBalance_throwsNotFound_whenAccountDoesNotExist() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getBalance(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");
    }

    @Test
    void getBalance_returnsZeroBalances_whenMemberHasNoWallet() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        WalletBalanceResponse response = walletService.getBalance(email);

        assertThat(response.getBalance()).isEqualByComparingTo("0");
        assertThat(response.getLockedBalance()).isEqualByComparingTo("0");
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("0");
    }

    @Test
    void getBalance_returnsBalanceAndAvailableBalance_whenWalletExists() {
        wallet.setBalance(new BigDecimal("500000"));
        wallet.setLockedBalance(new BigDecimal("100000"));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));

        WalletBalanceResponse response = walletService.getBalance(email);

        assertThat(response.getBalance()).isEqualByComparingTo("500000");
        assertThat(response.getLockedBalance()).isEqualByComparingTo("100000");
        assertThat(response.getAvailableBalance()).isEqualByComparingTo("400000");
    }

    @Test
    void getTransactions_throwsNotFound_whenAccountDoesNotExist() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getTransactions(email, null, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");
    }

    @Test
    void getTransactions_returnsEmptyPage_whenMemberHasNoWallet() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        PageResponse<WalletTransactionResponse> result = walletService.getTransactions(email, null, null, 0, 20);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getTransactions_throwsBadRequest_whenTypeIsInvalid() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> walletService.getTransactions(email, "ORDER_PAYOUT,NOT_A_TYPE", null, 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("type không hợp lệ: NOT_A_TYPE");
    }

    @Test
    void getTransactions_throwsBadRequest_whenDirectionIsInvalid() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> walletService.getTransactions(email, null, "SIDEWAYS", 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("direction phải là IN hoặc OUT.");
    }

    @Test
    void getTransactions_mapsFieldsIncludingOrderNoAndItemName() {
        Order order = Order.builder().id(UUID.randomUUID()).orderNo("OD-2026-000123").build();
        Product product = Product.builder().id("Q7-AK-000845").name("Áo khoác denim").build();
        OrderItem item = OrderItem.builder().id(UUID.randomUUID()).order(order).product(product).build();

        WalletTransaction tx = WalletTransaction.builder()
                .id(UUID.randomUUID())
                .transactionNo("WT-2026-000001")
                .walletType(WalletType.MEMBER)
                .walletId(wallet.getId())
                .type(WalletTransactionType.ORDER_PAYOUT)
                .amount(new BigDecimal("198000"))
                .balanceAfter(new BigDecimal("298000"))
                .relatedOrder(order)
                .relatedOrderItem(item)
                .status(TransactionStatus.SUCCESS)
                .note("Thanh toán đơn hàng OD-2026-000123")
                .createdAt(Instant.parse("2026-10-13T11:00:00Z"))
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        stubRepositoryPage(new PageImpl<>(List.of(tx)));

        PageResponse<WalletTransactionResponse> result = walletService.getTransactions(
                email, "ORDER_PAYOUT", "IN", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        WalletTransactionResponse response = result.getContent().get(0);
        assertThat(response.getTransactionNo()).isEqualTo("WT-2026-000001");
        assertThat(response.getType()).isEqualTo("ORDER_PAYOUT");
        assertThat(response.getAmount()).isEqualTo(new BigDecimal("198000"));
        assertThat(response.getBalanceAfter()).isEqualTo(new BigDecimal("298000"));
        assertThat(response.getRelatedOrderId()).isEqualTo(order.getId().toString());
        assertThat(response.getRelatedOrderNo()).isEqualTo("OD-2026-000123");
        assertThat(response.getItemName()).isEqualTo("Áo khoác denim");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getNote()).isEqualTo("Thanh toán đơn hàng OD-2026-000123");
    }

    @Test
    void getTransactions_itemNameIsNull_whenNoRelatedOrderItem() {
        WalletTransaction tx = WalletTransaction.builder()
                .id(UUID.randomUUID())
                .transactionNo("WT-2026-000002")
                .walletType(WalletType.MEMBER)
                .walletId(wallet.getId())
                .type(WalletTransactionType.REFUND)
                .amount(new BigDecimal("245000"))
                .status(TransactionStatus.SUCCESS)
                .createdAt(Instant.now())
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        stubRepositoryPage(new PageImpl<>(List.of(tx)));

        PageResponse<WalletTransactionResponse> result = walletService.getTransactions(email, null, null, 0, 20);

        assertThat(result.getContent().get(0).getItemName()).isNull();
        assertThat(result.getContent().get(0).getRelatedOrderId()).isNull();
    }

    @Test
    void getTransactionDetail_throwsNotFound_whenAccountDoesNotExist() {
        UUID txId = UUID.randomUUID();
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getTransactionDetail(email, txId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");
    }

    @Test
    void getTransactionDetail_throwsNotFound_whenMemberHasNoWallet() {
        UUID txId = UUID.randomUUID();
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getTransactionDetail(email, txId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy giao dịch.");
    }

    @Test
    void getTransactionDetail_throwsNotFound_whenTransactionDoesNotExist() {
        UUID txId = UUID.randomUUID();
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findById(txId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getTransactionDetail(email, txId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy giao dịch.");
    }

    @Test
    void getTransactionDetail_throwsForbidden_whenTransactionBelongsToAnotherWallet() {
        UUID txId = UUID.randomUUID();
        WalletTransaction tx = WalletTransaction.builder()
                .id(txId)
                .walletType(WalletType.MEMBER)
                .walletId(UUID.randomUUID())
                .type(WalletTransactionType.REFUND)
                .amount(new BigDecimal("10000"))
                .status(TransactionStatus.SUCCESS)
                .createdAt(Instant.now())
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        assertThatThrownBy(() -> walletService.getTransactionDetail(email, txId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không có quyền xem giao dịch này.");
    }

    @Test
    void getTransactionDetail_returnsMappedResponse_onSuccess() {
        UUID txId = UUID.randomUUID();
        WalletTransaction tx = WalletTransaction.builder()
                .id(txId)
                .transactionNo("WT-2026-000003")
                .walletType(WalletType.MEMBER)
                .walletId(wallet.getId())
                .type(WalletTransactionType.REFUND)
                .amount(new BigDecimal("245000"))
                .balanceAfter(new BigDecimal("345000"))
                .status(TransactionStatus.SUCCESS)
                .note("Hoàn tiền hủy đơn OD-2026-000001")
                .createdAt(Instant.now())
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        when(walletTransactionRepository.findById(txId)).thenReturn(Optional.of(tx));

        WalletTransactionResponse response = walletService.getTransactionDetail(email, txId);

        assertThat(response.getId()).isEqualTo(txId.toString());
        assertThat(response.getTransactionNo()).isEqualTo("WT-2026-000003");
        assertThat(response.getType()).isEqualTo("REFUND");
        assertThat(response.getAmount()).isEqualTo(new BigDecimal("245000"));
        assertThat(response.getBalanceAfter()).isEqualTo(new BigDecimal("345000"));
        assertThat(response.getNote()).isEqualTo("Hoàn tiền hủy đơn OD-2026-000001");
    }

    @Test
    void getRevenue_throwsNotFound_whenAccountDoesNotExist() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getRevenue(email, null, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");
    }

    @Test
    void getRevenue_returnsEmptyPage_whenMemberHasNoWallet() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        PageResponse<OrderRevenueResponse> result = walletService.getRevenue(email, null, 0, 20);

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void getRevenue_throwsBadRequest_whenMonthIsInvalid() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> walletService.getRevenue(email, "2026/10", 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("month không hợp lệ, định dạng yyyy-MM.");
    }

    @Test
    void getRevenue_mapsSalePriceCommissionTaxAndNetAmount_fromOrderPayoutTransaction() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNo("OD-2026-000123")
                .completedAt(Instant.parse("2026-10-13T11:00:00Z"))
                .build();
        Product product = Product.builder().id("Q7-AK-000845").name("Áo khoác denim").build();
        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .product(product)
                .priceAtOrder(new BigDecimal("220000"))
                .commissionRateApplied(new BigDecimal("0.10"))
                .taxRateApplied(BigDecimal.ZERO)
                .termApplied((short) 1)
                .build();

        WalletTransaction tx = WalletTransaction.builder()
                .id(UUID.randomUUID())
                .transactionNo("WT-2026-000001")
                .walletType(WalletType.MEMBER)
                .walletId(wallet.getId())
                .type(WalletTransactionType.ORDER_PAYOUT)
                .amount(new BigDecimal("198000"))
                .balanceAfter(new BigDecimal("298000"))
                .relatedOrder(order)
                .relatedOrderItem(item)
                .status(TransactionStatus.SUCCESS)
                .note("Thanh toán đơn hàng OD-2026-000123")
                .createdAt(Instant.parse("2026-10-13T11:00:00Z"))
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        stubRepositoryPage(new PageImpl<>(List.of(tx)));

        PageResponse<OrderRevenueResponse> result = walletService.getRevenue(email, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        OrderRevenueResponse response = result.getContent().get(0);
        assertThat(response.getOrderId()).isEqualTo(order.getId().toString());
        assertThat(response.getOrderNo()).isEqualTo("OD-2026-000123");
        assertThat(response.getProductName()).isEqualTo("Áo khoác denim");
        assertThat(response.getTermNo()).isEqualTo((short) 1);
        assertThat(response.getSalePrice()).isEqualByComparingTo("220000");
        assertThat(response.getCommissionRate()).isEqualByComparingTo("0.10");
        assertThat(response.getCommission()).isEqualByComparingTo("22000");
        assertThat(response.getTax()).isEqualByComparingTo("0");
        assertThat(response.getNetAmount()).isEqualByComparingTo("198000");
        assertThat(response.getCompletedAt()).isEqualTo(Instant.parse("2026-10-13T11:00:00Z"));
    }

    @Test
    void getRevenue_filtersByMonth_whenMonthIsValid() {
        Order order = Order.builder()
                .id(UUID.randomUUID())
                .orderNo("OD-2026-000124")
                .completedAt(Instant.parse("2026-10-20T11:00:00Z"))
                .build();
        Product product = Product.builder().id("Q7-AK-000846").name("Quần jean").build();
        OrderItem item = OrderItem.builder()
                .id(UUID.randomUUID())
                .order(order)
                .product(product)
                .priceAtOrder(new BigDecimal("150000"))
                .commissionRateApplied(new BigDecimal("0.10"))
                .taxRateApplied(BigDecimal.ZERO)
                .build();

        WalletTransaction tx = WalletTransaction.builder()
                .id(UUID.randomUUID())
                .transactionNo("WT-2026-000005")
                .walletType(WalletType.MEMBER)
                .walletId(wallet.getId())
                .type(WalletTransactionType.ORDER_PAYOUT)
                .amount(new BigDecimal("135000"))
                .relatedOrder(order)
                .relatedOrderItem(item)
                .status(TransactionStatus.SUCCESS)
                .createdAt(Instant.parse("2026-10-20T11:00:00Z"))
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        stubRepositoryPage(new PageImpl<>(List.of(tx)));

        PageResponse<OrderRevenueResponse> result = walletService.getRevenue(email, "2026-10", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getOrderNo()).isEqualTo("OD-2026-000124");
    }

    @Test
    void getRevenue_fieldsAreNull_whenNoRelatedOrderItem() {
        WalletTransaction tx = WalletTransaction.builder()
                .id(UUID.randomUUID())
                .transactionNo("WT-2026-000004")
                .walletType(WalletType.MEMBER)
                .walletId(wallet.getId())
                .type(WalletTransactionType.ORDER_PAYOUT)
                .amount(new BigDecimal("198000"))
                .status(TransactionStatus.SUCCESS)
                .createdAt(Instant.now())
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        stubRepositoryPage(new PageImpl<>(List.of(tx)));

        PageResponse<OrderRevenueResponse> result = walletService.getRevenue(email, null, 0, 20);

        OrderRevenueResponse response = result.getContent().get(0);
        assertThat(response.getOrderId()).isNull();
        assertThat(response.getOrderNo()).isNull();
        assertThat(response.getProductName()).isNull();
        assertThat(response.getTermNo()).isNull();
        assertThat(response.getSalePrice()).isNull();
        assertThat(response.getCommissionRate()).isNull();
        assertThat(response.getCommission()).isNull();
        assertThat(response.getTax()).isNull();
        assertThat(response.getCompletedAt()).isNull();
        assertThat(response.getNetAmount()).isEqualByComparingTo("198000");
    }

    @Test
    void createPayoutRequest_throwsNotFound_whenAccountDoesNotExist() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(new BigDecimal("100000"));

        assertThatThrownBy(() -> walletService.createPayoutRequest(email, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");

        verify(walletRepository, never()).findByMemberIdForUpdate(any());
        verify(payoutRequestRepository, never()).save(any());
    }

    @Test
    void createPayoutRequest_throwsBadRequest_whenAmountIsNull() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));

        CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(null);

        assertThatThrownBy(() -> walletService.createPayoutRequest(email, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("amount phải lớn hơn 0.");

        verify(walletRepository, never()).findByMemberIdForUpdate(any());
    }

    @Test
    void createPayoutRequest_throwsBadRequest_whenAmountIsZeroOrNegative() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));

        CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(new BigDecimal("-1000"));

        assertThatThrownBy(() -> walletService.createPayoutRequest(email, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("amount phải lớn hơn 0.");

        verify(walletRepository, never()).findByMemberIdForUpdate(any());
    }

    @Test
    void createPayoutRequest_throwsConflict_whenBelowDefaultMinimum() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));

        CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(new BigDecimal("10000"));

        assertThatThrownBy(() -> walletService.createPayoutRequest(email, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("BELOW_MIN_PAYOUT: số tiền rút dưới mức tối thiểu.");

        verify(walletRepository, never()).findByMemberIdForUpdate(any());
    }

    @Test
    void createPayoutRequest_usesConfiguredMinimum_whenSystemConfigOverridesDefault() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(systemConfigRepository.findByKey("min_payout_amount"))
                .thenReturn(Optional.of(SystemConfig.builder()
                        .key("min_payout_amount")
                        .value("200000")
                        .build()));

        CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(new BigDecimal("100000"));

        assertThatThrownBy(() -> walletService.createPayoutRequest(email, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("BELOW_MIN_PAYOUT: số tiền rút dưới mức tối thiểu.");

        verify(walletRepository, never()).findByMemberIdForUpdate(any());
    }

    @Test
    void createPayoutRequest_throwsNotFound_whenWalletDoesNotExist() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMemberIdForUpdate(member.getId())).thenReturn(Optional.empty());

        CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(new BigDecimal("100000"));

        assertThatThrownBy(() -> walletService.createPayoutRequest(email, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy ví.");

        verify(payoutRequestRepository, never()).save(any());
    }

    @Test
    void createPayoutRequest_throwsConflict_whenInsufficientBalance() {
        wallet.setBalance(new BigDecimal("100000"));
        wallet.setLockedBalance(new BigDecimal("50000"));

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMemberIdForUpdate(member.getId())).thenReturn(Optional.of(wallet));

        CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(new BigDecimal("60000"));

        assertThatThrownBy(() -> walletService.createPayoutRequest(email, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("INSUFFICIENT_BALANCE: ví không đủ số dư khả dụng.");

        verify(walletRepository, never()).save(any());
        verify(payoutRequestRepository, never()).save(any());
    }

    @Test
    void createPayoutRequest_throwsConflict_whenDailyLimitReached() {
        wallet.setBalance(new BigDecimal("1000000"));
        wallet.setLockedBalance(BigDecimal.ZERO);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMemberIdForUpdate(member.getId())).thenReturn(Optional.of(wallet));
        when(payoutRequestRepository.countByWallet_IdAndCreatedAtBetween(
                eq(wallet.getId()), any(Instant.class), any(Instant.class)))
                .thenReturn(3L);

        CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(new BigDecimal("100000"));

        assertThatThrownBy(() -> walletService.createPayoutRequest(email, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("DAILY_PAYOUT_LIMIT_REACHED: đã đạt số lần rút tối đa trong ngày.");

        verify(walletRepository, never()).save(any());
        verify(payoutRequestRepository, never()).save(any());
    }

    @Test
    void createPayoutRequest_succeeds_simulatesPayoutAndReleasesLockedBalance() {
        wallet.setBalance(new BigDecimal("1000000"));
        wallet.setLockedBalance(BigDecimal.ZERO);

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMemberIdForUpdate(member.getId())).thenReturn(Optional.of(wallet));
        when(payoutRequestRepository.countByWallet_IdAndCreatedAtBetween(
                eq(wallet.getId()), any(Instant.class), any(Instant.class)))
                .thenReturn(0L);
        when(payoutRequestRepository.save(any())).thenAnswer(invocation -> {
            PayoutRequest saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });
        when(walletTransactionRepository.countByTransactionNoStartingWith(any())).thenReturn(0L);
        when(payoutRequestRepository.countByPayoutNoStartingWith(any())).thenReturn(0L);
        when(platformFundRepository.save(any())).thenAnswer(invocation -> {
            PlatformFund saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(new BigDecimal("100000"));

        PayoutRequestResponse response = walletService.createPayoutRequest(email, request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getPayoutNo()).isEqualTo("RT-00001");
        assertThat(response.getAmount()).isEqualByComparingTo("100000");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getProcessedAt()).isNotNull();
        assertThat(wallet.getBalance()).isEqualByComparingTo("900000");
        assertThat(wallet.getLockedBalance()).isEqualByComparingTo("0");

        verify(walletRepository, times(2)).save(wallet);

        ArgumentCaptor<PayoutRequest> payoutCaptor = ArgumentCaptor.forClass(PayoutRequest.class);
        verify(payoutRequestRepository, times(2)).save(payoutCaptor.capture());
        PayoutRequest savedPayoutRequest = payoutCaptor.getValue();
        assertThat(savedPayoutRequest.getWallet()).isEqualTo(wallet);
        assertThat(savedPayoutRequest.getAmount()).isEqualByComparingTo("100000");
        assertThat(savedPayoutRequest.getStatus()).isEqualTo(TransactionStatus.SUCCESS);
        assertThat(savedPayoutRequest.getProcessedAt()).isNotNull();

        ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, times(2)).save(txCaptor.capture());
        WalletTransaction memberTx = txCaptor.getAllValues().stream()
                .filter(tx -> tx.getWalletType() == WalletType.MEMBER).findFirst().orElseThrow();
        WalletTransaction platformTx = txCaptor.getAllValues().stream()
                .filter(tx -> tx.getWalletType() == WalletType.PLATFORM).findFirst().orElseThrow();

        assertThat(memberTx.getWalletId()).isEqualTo(wallet.getId());
        assertThat(memberTx.getType()).isEqualTo(WalletTransactionType.WITHDRAWAL);
        assertThat(memberTx.getAmount()).isEqualByComparingTo("-100000");
        assertThat(memberTx.getBalanceAfter()).isEqualByComparingTo("900000");
        assertThat(memberTx.getStatus()).isEqualTo(TransactionStatus.SUCCESS);

        assertThat(platformTx.getType()).isEqualTo(WalletTransactionType.WITHDRAWAL);
        assertThat(platformTx.getAmount()).isEqualByComparingTo("100000");
        assertThat(platformTx.getCounterpartTransaction()).isEqualTo(memberTx);

        // P4.4: bút toán rút tiền phải trỏ về yêu cầu rút, để app mở được chi tiết từ lịch sử ví
        assertThat(memberTx.getRelatedPayoutRequest()).isEqualTo(savedPayoutRequest);
        assertThat(platformTx.getRelatedPayoutRequest()).isEqualTo(savedPayoutRequest);
    }

    // ==================== getWalletConfig (P4.3) ====================

    @Test
    void getWalletConfig_returnsDefaults_whenAdminHasNotConfiguredAnything() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        when(payoutRequestRepository.countByWallet_IdAndCreatedAtBetween(
                eq(wallet.getId()), any(Instant.class), any(Instant.class)))
                .thenReturn(2L);

        WalletConfigResponse result = walletService.getWalletConfig(email);

        assertThat(result.getMinPayout()).isEqualByComparingTo("50000");
        assertThat(result.getMaxPayoutsPerDay()).isEqualTo(3);
        assertThat(result.getUsedToday()).isEqualTo(2L);
    }

    @Test
    void getWalletConfig_reflectsSystemConfigOverrides() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        when(systemConfigRepository.findByKey("min_payout_amount"))
                .thenReturn(Optional.of(SystemConfig.builder()
                        .key("min_payout_amount").value("10000").build()));
        when(systemConfigRepository.findByKey("max_payout_requests_per_day"))
                .thenReturn(Optional.of(SystemConfig.builder()
                        .key("max_payout_requests_per_day").value("5").build()));
        when(payoutRequestRepository.countByWallet_IdAndCreatedAtBetween(
                eq(wallet.getId()), any(Instant.class), any(Instant.class)))
                .thenReturn(1L);

        WalletConfigResponse result = walletService.getWalletConfig(email);

        assertThat(result.getMinPayout()).isEqualByComparingTo("10000");
        assertThat(result.getMaxPayoutsPerDay()).isEqualTo(5);
        assertThat(result.getUsedToday()).isEqualTo(1L);
    }

    // Member chưa từng có giao dịch nào thì chưa có ví, không được nổ NPE ở màn rút tiền
    @Test
    void getWalletConfig_usedTodayIsZero_whenMemberHasNoWallet() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        WalletConfigResponse result = walletService.getWalletConfig(email);

        assertThat(result.getUsedToday()).isZero();
        verify(payoutRequestRepository, never())
                .countByWallet_IdAndCreatedAtBetween(any(), any(), any());
    }

    @Test
    void createPayoutRequest_forcesFailure_whenConfigFlagIsSet_andResetsFlagAfterUse() {
        wallet.setBalance(new BigDecimal("1000000"));
        wallet.setLockedBalance(BigDecimal.ZERO);

        SystemConfig forceFailureConfig = SystemConfig.builder()
                .key("force_payout_failure")
                .value("true")
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMemberIdForUpdate(member.getId())).thenReturn(Optional.of(wallet));
        when(payoutRequestRepository.countByWallet_IdAndCreatedAtBetween(
                eq(wallet.getId()), any(Instant.class), any(Instant.class)))
                .thenReturn(0L);
        when(payoutRequestRepository.countByPayoutNoStartingWith(any())).thenReturn(0L);
        when(systemConfigRepository.findByKey("min_payout_amount")).thenReturn(Optional.empty());
        when(systemConfigRepository.findByKey("max_payout_requests_per_day")).thenReturn(Optional.empty());
        when(systemConfigRepository.findByKey("force_payout_failure"))
                .thenReturn(Optional.of(forceFailureConfig));
        when(payoutRequestRepository.save(any())).thenAnswer(invocation -> {
            PayoutRequest saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        CreatePayoutRequestRequest request = new CreatePayoutRequestRequest(new BigDecimal("100000"));

        PayoutRequestResponse response = walletService.createPayoutRequest(email, request);

        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getFailureReason()).isEqualTo("Giao dịch chi tiền giả lập thất bại.");
        assertThat(response.getProcessedAt()).isNotNull();
        assertThat(wallet.getBalance()).isEqualByComparingTo("1000000");
        assertThat(wallet.getLockedBalance()).isEqualByComparingTo("0");

        assertThat(forceFailureConfig.getValue()).isEqualTo("false");
        verify(systemConfigRepository).save(forceFailureConfig);
        verify(walletTransactionRepository, never()).save(any());
        verify(platformFundRepository, never()).save(any());
    }

    @Test
    void getPayoutRequestDetail_throwsNotFound_whenAccountDoesNotExist() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getPayoutRequestDetail(email, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");

        verify(payoutRequestRepository, never()).findById(any());
    }

    @Test
    void getPayoutRequestDetail_throwsNotFound_whenWalletDoesNotExist() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getPayoutRequestDetail(email, UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy yêu cầu rút tiền.");

        verify(payoutRequestRepository, never()).findById(any());
    }

    @Test
    void getPayoutRequestDetail_throwsNotFound_whenPayoutRequestDoesNotExist() {
        UUID payoutRequestId = UUID.randomUUID();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        when(payoutRequestRepository.findById(payoutRequestId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getPayoutRequestDetail(email, payoutRequestId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy yêu cầu rút tiền.");
    }

    @Test
    void getPayoutRequestDetail_throwsNotFound_whenPayoutRequestBelongsToAnotherWallet() {
        UUID payoutRequestId = UUID.randomUUID();
        Wallet otherWallet = Wallet.builder().id(UUID.randomUUID()).build();
        PayoutRequest payoutRequest = PayoutRequest.builder()
                .id(payoutRequestId)
                .wallet(otherWallet)
                .amount(new BigDecimal("100000"))
                .status(TransactionStatus.PENDING)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        when(payoutRequestRepository.findById(payoutRequestId)).thenReturn(Optional.of(payoutRequest));

        assertThatThrownBy(() -> walletService.getPayoutRequestDetail(email, payoutRequestId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy yêu cầu rút tiền.");
    }

    @Test
    void getPayoutRequestDetail_returnsResponse_whenFound() {
        UUID payoutRequestId = UUID.randomUUID();
        PayoutRequest payoutRequest = PayoutRequest.builder()
                .id(payoutRequestId)
                .wallet(wallet)
                .amount(new BigDecimal("100000"))
                .status(TransactionStatus.FAILED)
                .failureReason("Giao dịch giả lập thất bại")
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        when(payoutRequestRepository.findById(payoutRequestId)).thenReturn(Optional.of(payoutRequest));

        PayoutRequestResponse response = walletService.getPayoutRequestDetail(email, payoutRequestId);

        assertThat(response.getId()).isEqualTo(payoutRequestId.toString());
        assertThat(response.getAmount()).isEqualByComparingTo("100000");
        assertThat(response.getStatus()).isEqualTo("FAILED");
        assertThat(response.getFailureReason()).isEqualTo("Giao dịch giả lập thất bại");
    }

    @Test
    void getPayoutRequests_throwsNotFound_whenAccountDoesNotExist() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> walletService.getPayoutRequests(email, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account không tồn tại");
    }

    @Test
    void getPayoutRequests_returnsEmptyPage_whenMemberHasNoWallet() {
        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.empty());

        PageResponse<PayoutRequestResponse> result = walletService.getPayoutRequests(email, 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(payoutRequestRepository, never()).findByWallet_Id(any(), any());
    }

    @Test
    void getPayoutRequests_returnsMappedPage_whenWalletExists() {
        PayoutRequest payoutRequest = PayoutRequest.builder()
                .id(UUID.randomUUID())
                .wallet(wallet)
                .amount(new BigDecimal("100000"))
                .status(TransactionStatus.SUCCESS)
                .build();

        when(accountRepository.findByEmail(email)).thenReturn(Optional.of(member));
        when(walletRepository.findByMember_Id(member.getId())).thenReturn(Optional.of(wallet));
        when(payoutRequestRepository.findByWallet_Id(eq(wallet.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(payoutRequest)));

        PageResponse<PayoutRequestResponse> result = walletService.getPayoutRequests(email, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        PayoutRequestResponse response = result.getContent().get(0);
        assertThat(response.getId()).isEqualTo(payoutRequest.getId().toString());
        assertThat(response.getAmount()).isEqualByComparingTo("100000");
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getFailureReason()).isNull();
    }
}
