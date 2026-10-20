package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.WalletTransactionResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock AccountRepository accountRepository;
    @Mock WalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;

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
}
