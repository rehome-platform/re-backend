package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.DecideDegradedItemCaseRequest;
import com.rehome.rehome.dto.response.DegradedItemCaseResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.DegradedItemChoice;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.DegradedItemCase;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.DegradedItemCaseRepository;
import com.rehome.rehome.repository.PlatformFundRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DegradedItemCaseServiceImplTest {

    @Mock DegradedItemCaseRepository degradedItemCaseRepository;
    @Mock ProductRepository productRepository;
    @Mock WalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock PlatformFundRepository platformFundRepository;

    @InjectMocks
    DegradedItemCaseServiceImpl service;

    static final String PRODUCT_ID = "Q7-AK-000845";

    Account seller;
    Product product;
    ConsignmentReceipt receipt;
    UUID caseId;

    @BeforeEach
    void setup() {
        seller = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
        receipt = ConsignmentReceipt.builder().degradedCompensationRate(new BigDecimal("0.50")).build();
        product = Product.builder().id(PRODUCT_ID).member(seller).consignmentReceipt(receipt)
                .status(ProductStatus.RETURNED).build();

        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));

        caseId = UUID.randomUUID();
    }

    private DegradedItemCase degradedCase(BigDecimal compensationAmount, DegradedItemChoice choice) {
        DegradedItemCase c = DegradedItemCase.builder()
                .id(caseId)
                .product(product)
                .moderator(Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR).build())
                .compensationAmount(compensationAmount)
                .choice(choice)
                .build();
        when(degradedItemCaseRepository.findById(caseId)).thenReturn(Optional.of(c));
        return c;
    }

    private DecideDegradedItemCaseRequest request(String choice) {
        DecideDegradedItemCaseRequest req = new DecideDegradedItemCaseRequest();
        req.setChoice(choice);
        return req;
    }

    @Test
    @DisplayName("thiếu choice -> BadRequestException")
    void decide_missingChoice_throws() {
        assertThatThrownBy(() -> service.decide(seller, caseId, request(null)))
                .isInstanceOf(BadRequestException.class);

        verify(degradedItemCaseRepository, never()).findById(any());
    }

    @Test
    @DisplayName("choice không hợp lệ -> BadRequestException")
    void decide_invalidChoice_throws() {
        assertThatThrownBy(() -> service.decide(seller, caseId, request("REFUND_ALL")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("không tìm thấy ca -> DEGRADED_ITEM_CASE_NOT_FOUND")
    void decide_notFound_throws() {
        when(degradedItemCaseRepository.findById(caseId)).thenReturn(Optional.empty());

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.decide(seller, caseId, request("FULL_COMPENSATION")));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEGRADED_ITEM_CASE_NOT_FOUND);
    }

    @Test
    @DisplayName("không phải chủ sản phẩm -> DEGRADED_ITEM_CASE_FORBIDDEN")
    void decide_notOwner_throws() {
        degradedCase(new BigDecimal("270000"), null);
        Account other = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.decide(other, caseId, request("FULL_COMPENSATION")));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEGRADED_ITEM_CASE_FORBIDDEN);
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("ca đã được quyết định trước đó -> DEGRADED_ITEM_CASE_ALREADY_DECIDED")
    void decide_alreadyDecided_throws() {
        degradedCase(new BigDecimal("270000"), DegradedItemChoice.FULL_COMPENSATION);

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.decide(seller, caseId, request("PARTIAL_WITH_ITEM_BACK")));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DEGRADED_ITEM_CASE_ALREADY_DECIDED);
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("FULL_COMPENSATION -> đền đủ, sản phẩm PLATFORM_OWNED, cộng ví người bán")
    void decide_fullCompensation_paysFullAndMarksPlatformOwned() {
        DegradedItemCase c = degradedCase(new BigDecimal("270000"), null);
        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).member(seller).balance(new BigDecimal("100000")).build();
        when(walletRepository.findByMember_Id(seller.getId())).thenReturn(Optional.of(wallet));

        DegradedItemCaseResponse response = service.decide(seller, caseId, request("FULL_COMPENSATION"));

        assertThat(c.getChoice()).isEqualTo(DegradedItemChoice.FULL_COMPENSATION);
        assertThat(c.getPaidAmount()).isEqualByComparingTo(new BigDecimal("270000"));
        assertThat(c.getDecidedAt()).isNotNull();
        verify(degradedItemCaseRepository).save(c);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.PLATFORM_OWNED);
        verify(productRepository).save(product);

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("370000"));
        verify(walletRepository).save(wallet);

        assertThat(response.getId()).isEqualTo(caseId.toString());
        assertThat(response.getChoice()).isEqualTo("FULL_COMPENSATION");
        assertThat(response.getPaidAmount()).isEqualByComparingTo(new BigDecimal("270000"));
    }

    @Test
    @DisplayName("PARTIAL_WITH_ITEM_BACK -> đền theo degradedCompensationRate snapshot, sản phẩm WITHDRAWN")
    void decide_partialWithItemBack_paysRateAndWithdrawsProduct() {
        DegradedItemCase c = degradedCase(new BigDecimal("270000"), null);
        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).member(seller).balance(BigDecimal.ZERO).build();
        when(walletRepository.findByMember_Id(seller.getId())).thenReturn(Optional.of(wallet));

        DegradedItemCaseResponse response = service.decide(seller, caseId, request("PARTIAL_WITH_ITEM_BACK"));

        assertThat(c.getChoice()).isEqualTo(DegradedItemChoice.PARTIAL_WITH_ITEM_BACK);
        // 270000 * 0.50 = 135000
        assertThat(c.getPaidAmount()).isEqualByComparingTo(new BigDecimal("135000"));

        assertThat(product.getStatus()).isEqualTo(ProductStatus.WITHDRAWN);
        verify(productRepository).save(product);

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("135000"));
        assertThat(response.getPaidAmount()).isEqualByComparingTo(new BigDecimal("135000"));
    }

    @Test
    @DisplayName("phí đền dùng rate snapshot trên biên nhận, không phải giá trị khác")
    void decide_usesReceiptSnapshotRate() {
        receipt.setDegradedCompensationRate(new BigDecimal("0.30"));
        degradedCase(new BigDecimal("200000"), null);
        when(walletRepository.findByMember_Id(seller.getId()))
                .thenReturn(Optional.of(Wallet.builder().id(UUID.randomUUID()).member(seller)
                        .balance(BigDecimal.ZERO).build()));

        DegradedItemCaseResponse response = service.decide(seller, caseId, request("PARTIAL_WITH_ITEM_BACK"));

        assertThat(response.getPaidAmount()).isEqualByComparingTo(new BigDecimal("60000"));
    }

    @Test
    @DisplayName("cộng ví tạo bút toán DEGRADED_COMPENSATION kèm đối ứng quỹ nền tảng")
    void decide_recordsWalletTransactionWithPlatformCounterpart() {
        DegradedItemCase c = degradedCase(new BigDecimal("270000"), null);
        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).member(seller).balance(BigDecimal.ZERO).build();
        when(walletRepository.findByMember_Id(seller.getId())).thenReturn(Optional.of(wallet));

        service.decide(seller, caseId, request("FULL_COMPENSATION"));

        ArgumentCaptor<com.rehome.rehome.entity.marketplace.WalletTransaction> captor =
                ArgumentCaptor.forClass(com.rehome.rehome.entity.marketplace.WalletTransaction.class);
        verify(walletTransactionRepository, org.mockito.Mockito.times(2)).save(captor.capture());

        com.rehome.rehome.entity.marketplace.WalletTransaction memberTx = captor.getAllValues().get(0);
        assertThat(memberTx.getType())
                .isEqualTo(com.rehome.rehome.entity.enums.WalletTransactionType.DEGRADED_COMPENSATION);
        assertThat(memberTx.getAmount()).isEqualByComparingTo(new BigDecimal("270000"));
        assertThat(memberTx.getRelatedDegradedItemCase()).isEqualTo(c);

        com.rehome.rehome.entity.marketplace.WalletTransaction platformTx = captor.getAllValues().get(1);
        assertThat(platformTx.getAmount()).isEqualByComparingTo(new BigDecimal("-270000"));
        assertThat(platformTx.getCounterpartTransaction()).isEqualTo(memberTx);
    }
}
