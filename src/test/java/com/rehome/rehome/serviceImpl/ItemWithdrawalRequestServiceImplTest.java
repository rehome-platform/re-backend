package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.MomoApi;
import com.rehome.rehome.client.VnPayApi;
import com.rehome.rehome.dto.request.CreateItemWithdrawalRequest;
import com.rehome.rehome.dto.request.PayItemWithdrawalFeeRequest;
import com.rehome.rehome.dto.request.RejectItemWithdrawalRequest;
import com.rehome.rehome.dto.response.CreateMomoResponse;
import com.rehome.rehome.dto.response.ItemWithdrawalDetailResponse;
import com.rehome.rehome.dto.response.ItemWithdrawalResponse;
import com.rehome.rehome.dto.response.PayItemWithdrawalFeeResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.WithdrawalFeePaymentMethod;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ItemWithdrawalRequestRepository;
import com.rehome.rehome.repository.PlatformFundRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import com.rehome.rehome.service.QueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.service.SystemConfigReader;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ItemWithdrawalRequestServiceImplTest {

    @Mock ProductRepository productRepository;
    @Mock ProductPhotoRepository productPhotoRepository;
    @Mock ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    @Mock WalletRepository walletRepository;
    @Mock WalletTransactionRepository walletTransactionRepository;
    @Mock PlatformFundRepository platformFundRepository;
    @Mock MomoApi momoApi;
    @Mock VnPayApi vnPayApi;
    @Mock QueueService queueService;
    @Mock SystemConfigReader systemConfigReader;

    @InjectMocks
    ItemWithdrawalRequestServiceImpl service;

    static final long PICKUP_DEADLINE_DAYS = 7;
    static final long FEE_PAYMENT_DEADLINE_MINUTES = 15;
    static final String PRODUCT_ID = "Q7-AK-000845";

    Account owner;
    Account moderator;
    ConsignmentReceipt receipt;
    Product product;
    UUID withdrawalId;

    @BeforeEach
    void setup() {
        when(systemConfigReader.getInt(eq(SystemConfigKeys.COUNTER_PICKUP_DAYS), anyInt()))
                .thenReturn((int) PICKUP_DEADLINE_DAYS);        ReflectionTestUtils.setField(service, "feePaymentDeadlineMinutes", FEE_PAYMENT_DEADLINE_MINUTES);
        ReflectionTestUtils.setField(service, "momoPartnerCode", "MOMO");
        ReflectionTestUtils.setField(service, "momoAccessKey", "access-key");
        ReflectionTestUtils.setField(service, "momoSecretKey", "secret-key");
        ReflectionTestUtils.setField(service, "momoRedirectUrl", "https://fe.example/momo-return");
        ReflectionTestUtils.setField(service, "momoIpnUrl", "https://api.example/api/momo/ipn-handler");
        ReflectionTestUtils.setField(service, "momoRequestType", "payWithMethod");
        ReflectionTestUtils.setField(service, "vnpTmnCode", "TMN001");
        ReflectionTestUtils.setField(service, "vnpHashSecret", "hash-secret");
        ReflectionTestUtils.setField(service, "vnpPayUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(service, "vnpReturnUrl", "https://fe.example/vnpay-return");
        ReflectionTestUtils.setField(service, "vnpVersion", "2.1.0");

        owner = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();

        Warehouse warehouse = Warehouse.builder().id(UUID.randomUUID()).build();
        moderator = Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR).warehouse(warehouse).build();

        receipt = ConsignmentReceipt.builder()
                .freeWithdrawalWindowDays(7)
                .withdrawalFee(new BigDecimal("50000"))
                .build();

        product = Product.builder()
                .id(PRODUCT_ID)
                .member(owner)
                .consignmentReceipt(receipt)
                .status(ProductStatus.ON_SALE)
                .termStartedAt(Instant.now())
                .warehouse(warehouse)
                .build();

        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));

        // Giả lập JPA gán id khi save yêu cầu rút hàng mới
        when(itemWithdrawalRequestRepository.save(any())).thenAnswer(invocation -> {
            ItemWithdrawalRequest saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            return saved;
        });

        withdrawalId = UUID.randomUUID();
    }

    private CreateItemWithdrawalRequest request(String productId) {
        return new CreateItemWithdrawalRequest(productId);
    }

    private ItemWithdrawalRequest withdrawal(BigDecimal feeCharged) {
        ItemWithdrawalRequest r = ItemWithdrawalRequest.builder()
                .id(withdrawalId)
                .product(product)
                .member(owner)
                .feeCharged(feeCharged)
                .status(ItemWithdrawalStatus.PENDING_PICKUP)
                .pickupDeadline(Instant.now().plus(PICKUP_DEADLINE_DAYS, ChronoUnit.DAYS))
                .build();
        when(itemWithdrawalRequestRepository.findById(withdrawalId)).thenReturn(Optional.of(r));
        return r;
    }

    private PayItemWithdrawalFeeRequest payRequest(String method) {
        PayItemWithdrawalFeeRequest req = new PayItemWithdrawalFeeRequest();
        req.setPaymentMethod(method);
        return req;
    }

    @Test
    @DisplayName("thiếu productId -> BadRequestException")
    void create_missingProductId_throws() {
        assertThatThrownBy(() -> service.create(owner, request(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("productId là bắt buộc.");

        verifyNoInteractions(productRepository, itemWithdrawalRequestRepository);
    }

    @Test
    @DisplayName("không tìm thấy sản phẩm -> PRODUCT_NOT_FOUND")
    void create_productNotFound_throws() {
        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.empty());

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.create(owner, request(PRODUCT_ID)));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        verify(itemWithdrawalRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("sản phẩm của Member khác -> PRODUCT_FORBIDDEN")
    void create_notOwner_throws() {
        Account other = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.create(other, request(PRODUCT_ID)));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_FORBIDDEN);
        verify(itemWithdrawalRequestRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = "ON_SALE", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("sản phẩm không ON_SALE (đã có đơn/đã xử lý) -> PRODUCT_ALREADY_ORDERED")
    void create_notOnSale_throws(ProductStatus status) {
        product.setStatus(status);

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.create(owner, request(PRODUCT_ID)));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_ALREADY_ORDERED);
        verify(itemWithdrawalRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("sản phẩm đang có yêu cầu rút hàng chờ xử lý -> ITEM_WITHDRAWAL_ALREADY_PENDING")
    void create_alreadyPending_throws() {
        when(itemWithdrawalRequestRepository.existsByProduct_IdAndStatusIn(
                PRODUCT_ID, List.of(ItemWithdrawalStatus.PENDING_APPROVAL, ItemWithdrawalStatus.PENDING_PICKUP)))
                .thenReturn(true);

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.create(owner, request(PRODUCT_ID)));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ITEM_WITHDRAWAL_ALREADY_PENDING);
        verify(itemWithdrawalRequestRepository, never()).save(any());
    }

    private void assertCreateRejectedAsIncomplete() {
        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.create(owner, request(PRODUCT_ID)));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PRODUCT_RECEIPT_INCOMPLETE);
        // Không để lại dấu vết dở dang: không tạo yêu cầu, không vào hàng đợi, sản phẩm vẫn đang bán
        verify(itemWithdrawalRequestRepository, never()).save(any());
        verify(queueService, never()).enqueue(any(), any(), any());
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    @DisplayName("sản phẩm thiếu biên nhận ký gửi -> 409 PRODUCT_RECEIPT_INCOMPLETE, không phải NPE")
    void create_missingReceipt_throwsIncomplete() {
        product.setConsignmentReceipt(null);

        assertCreateRejectedAsIncomplete();
    }

    @Test
    @DisplayName("sản phẩm thiếu termStartedAt -> 409 PRODUCT_RECEIPT_INCOMPLETE")
    void create_missingTermStartedAt_throwsIncomplete() {
        product.setTermStartedAt(null);

        assertCreateRejectedAsIncomplete();
    }

    @Test
    @DisplayName("biên nhận thiếu freeWithdrawalWindowDays -> 409 PRODUCT_RECEIPT_INCOMPLETE")
    void create_missingFreeWindowDays_throwsIncomplete() {
        receipt.setFreeWithdrawalWindowDays(null);

        assertCreateRejectedAsIncomplete();
    }

    @Test
    @DisplayName("ngoài cửa sổ miễn phí mà biên nhận thiếu withdrawalFee -> 409 PRODUCT_RECEIPT_INCOMPLETE")
    void create_afterFreeWindow_missingFee_throwsIncomplete() {
        product.setTermStartedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        receipt.setWithdrawalFee(null);

        assertCreateRejectedAsIncomplete();
    }

    @Test
    @DisplayName("trong cửa sổ miễn phí thì biên nhận thiếu withdrawalFee vẫn rút được (phí = 0)")
    void create_withinFreeWindow_missingFee_stillFree() {
        product.setTermStartedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        receipt.setWithdrawalFee(null);

        ItemWithdrawalResponse response = service.create(owner, request(PRODUCT_ID));

        assertThat(response.getFeeCharged()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("rút trong cửa sổ miễn phí -> feeCharged = 0, PENDING_APPROVAL, chưa có hạn (chưa duyệt)")
    void create_withinFreeWindow_feeIsZero() {
        product.setTermStartedAt(Instant.now().minus(2, ChronoUnit.DAYS));

        ItemWithdrawalResponse response = service.create(owner, request(PRODUCT_ID));

        assertThat(response.getProductId()).isEqualTo(PRODUCT_ID);
        assertThat(response.getStatus()).isEqualTo("PENDING_APPROVAL");
        assertThat(response.getFeeCharged()).isEqualByComparingTo(BigDecimal.ZERO);
        // Chưa duyệt -> chưa có hạn lấy hàng
        assertThat(response.getPickupDeadline()).isNull();

        ArgumentCaptor<ItemWithdrawalRequest> captor = ArgumentCaptor.forClass(ItemWithdrawalRequest.class);
        verify(itemWithdrawalRequestRepository).save(captor.capture());
        ItemWithdrawalRequest saved = captor.getValue();
        assertThat(saved.getProduct()).isEqualTo(product);
        assertThat(saved.getMember()).isEqualTo(owner);
        assertThat(saved.getFeeCharged()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(saved.getStatus()).isEqualTo(ItemWithdrawalStatus.PENDING_APPROVAL);
        assertThat(saved.getPickupDeadline()).isNull();
        // Miễn phí -> không có gì phải trả, không cần hạn trả phí
        assertThat(saved.getFeePaymentDeadline()).isNull();
        assertThat(response.getFeePaymentDeadline()).isNull();
    }

    @Test
    @DisplayName("create: thành công -> sản phẩm ngừng bán ngay (ON_SALE -> WITHDRAWN)")
    void create_success_withdrawsProductImmediately() {
        service.create(owner, request(PRODUCT_ID));

        assertThat(product.getStatus()).isEqualTo(ProductStatus.WITHDRAWN);
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("create: thành công -> mở việc WITHDRAWAL trong hàng đợi của kho giữ sản phẩm")
    void create_success_enqueuesWithdrawalQueueItem() {
        ItemWithdrawalResponse response = service.create(owner, request(PRODUCT_ID));

        verify(queueService).enqueue(QueueType.WITHDRAWAL, response.getId(), product.getWarehouse());
    }

    @Test
    @DisplayName("rút đúng ngày cuối cửa sổ miễn phí (chưa qua hạn) -> vẫn miễn phí")
    void create_exactlyAtFreeWindowBoundary_feeIsZero() {
        product.setTermStartedAt(Instant.now().minus(7, ChronoUnit.DAYS).plusSeconds(30));

        ItemWithdrawalResponse response = service.create(owner, request(PRODUCT_ID));

        assertThat(response.getFeeCharged()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("rút ngoài cửa sổ miễn phí -> feeCharged = withdrawalFee snapshot trên biên nhận")
    void create_afterFreeWindow_chargesSnapshotFee() {
        product.setTermStartedAt(Instant.now().minus(10, ChronoUnit.DAYS));

        ItemWithdrawalResponse response = service.create(owner, request(PRODUCT_ID));

        assertThat(response.getFeeCharged()).isEqualByComparingTo(new BigDecimal("50000"));

        ArgumentCaptor<ItemWithdrawalRequest> captor = ArgumentCaptor.forClass(ItemWithdrawalRequest.class);
        verify(itemWithdrawalRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getFeeCharged()).isEqualByComparingTo(new BigDecimal("50000"));
    }

    @Test
    @DisplayName("có phí phải trả -> create() chưa đặt hạn trả phí, chờ Moderator duyệt")
    void create_withFee_doesNotSetFeePaymentDeadlineYet() {
        product.setTermStartedAt(Instant.now().minus(10, ChronoUnit.DAYS));

        ItemWithdrawalResponse response = service.create(owner, request(PRODUCT_ID));

        assertThat(response.getFeePaymentDeadline()).isNull();

        ArgumentCaptor<ItemWithdrawalRequest> captor = ArgumentCaptor.forClass(ItemWithdrawalRequest.class);
        verify(itemWithdrawalRequestRepository).save(captor.capture());
        assertThat(captor.getValue().getFeePaymentDeadline()).isNull();
        assertThat(captor.getValue().getPickupDeadline()).isNull();
    }

    @Test
    @DisplayName("phí rút dùng giá trị snapshot trên biên nhận, không phải cấu hình sống")
    void create_usesReceiptSnapshotFee_notLiveConfig() {
        product.setTermStartedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        receipt.setWithdrawalFee(new BigDecimal("123456"));

        ItemWithdrawalResponse response = service.create(owner, request(PRODUCT_ID));

        assertThat(response.getFeeCharged()).isEqualByComparingTo(new BigDecimal("123456"));
    }

    // ==================== approve / reject (Moderator duyệt / từ chối) ====================

    private ItemWithdrawalRequest pendingApproval(BigDecimal feeCharged) {
        ItemWithdrawalRequest r = ItemWithdrawalRequest.builder()
                .id(withdrawalId)
                .product(product)
                .member(owner)
                .feeCharged(feeCharged)
                .status(ItemWithdrawalStatus.PENDING_APPROVAL)
                .build();
        when(itemWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.of(r));
        return r;
    }

    private RejectItemWithdrawalRequest rejectRequest(String reason) {
        RejectItemWithdrawalRequest req = new RejectItemWithdrawalRequest();
        req.setReason(reason);
        return req;
    }

    @Test
    @DisplayName("approve: không tìm thấy -> ITEM_WITHDRAWAL_NOT_FOUND")
    void approve_notFound_throws() {
        when(itemWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.empty());

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.approve(moderator, withdrawalId));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ITEM_WITHDRAWAL_NOT_FOUND);
    }

    @Test
    @DisplayName("approve: Moderator chưa được gán kho -> MODERATOR_NO_WAREHOUSE")
    void approve_moderatorNoWarehouse_throws() {
        pendingApproval(new BigDecimal("50000"));
        Account noWarehouseModerator = Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR).build();

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.approve(noWarehouseModerator, withdrawalId));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MODERATOR_NO_WAREHOUSE);
        verify(itemWithdrawalRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve: Moderator thuộc kho khác -> ITEM_WITHDRAWAL_FORBIDDEN")
    void approve_wrongWarehouse_throws() {
        pendingApproval(new BigDecimal("50000"));
        Account otherModerator = Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR)
                .warehouse(Warehouse.builder().id(UUID.randomUUID()).build()).build();

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.approve(otherModerator, withdrawalId));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ITEM_WITHDRAWAL_FORBIDDEN);
        verify(itemWithdrawalRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("approve: không còn PENDING_APPROVAL -> ITEM_WITHDRAWAL_NOT_PENDING_APPROVAL")
    void approve_notPendingApproval_throws() {
        ItemWithdrawalRequest r = pendingApproval(new BigDecimal("50000"));
        r.setStatus(ItemWithdrawalStatus.PENDING_PICKUP);

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.approve(moderator, withdrawalId));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ITEM_WITHDRAWAL_NOT_PENDING_APPROVAL);
    }

    @Test
    @DisplayName("approve: miễn phí -> PENDING_PICKUP, gắn moderator, đặt pickupDeadline +7 ngày, không có hạn trả phí")
    void approve_freeFee_setsPickupDeadlineOnly() {
        ItemWithdrawalRequest r = pendingApproval(BigDecimal.ZERO);

        Instant before = Instant.now();
        ItemWithdrawalResponse response = service.approve(moderator, withdrawalId);

        assertThat(response.getStatus()).isEqualTo("PENDING_PICKUP");
        assertThat(r.getStatus()).isEqualTo(ItemWithdrawalStatus.PENDING_PICKUP);
        assertThat(r.getModerator()).isEqualTo(moderator);
        assertThat(r.getReviewedAt()).isNotNull();
        assertThat(r.getPickupDeadline())
                .isAfter(before.plus(PICKUP_DEADLINE_DAYS, ChronoUnit.DAYS).minusSeconds(5))
                .isBefore(Instant.now().plus(PICKUP_DEADLINE_DAYS, ChronoUnit.DAYS).plusSeconds(5));
        assertThat(r.getFeePaymentDeadline()).isNull();
        verify(itemWithdrawalRequestRepository).save(r);
    }

    @Test
    @DisplayName("approve: có phí -> đặt hạn trả phí +15 phút, ngắn hơn pickupDeadline")
    void approve_withFee_setsFeePaymentDeadline() {
        ItemWithdrawalRequest r = pendingApproval(new BigDecimal("50000"));

        Instant before = Instant.now();
        service.approve(moderator, withdrawalId);

        assertThat(r.getFeePaymentDeadline())
                .isAfter(before.plus(FEE_PAYMENT_DEADLINE_MINUTES, ChronoUnit.MINUTES).minusSeconds(5))
                .isBefore(Instant.now().plus(FEE_PAYMENT_DEADLINE_MINUTES, ChronoUnit.MINUTES).plusSeconds(5));
        assertThat(r.getFeePaymentDeadline()).isBefore(r.getPickupDeadline());
    }

    @Test
    @DisplayName("reject: thiếu reason -> BadRequestException, không lưu, không đổi gì")
    void reject_missingReason_throws() {
        pendingApproval(new BigDecimal("50000"));

        assertThatThrownBy(() -> service.reject(moderator, withdrawalId, rejectRequest(null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.reject(moderator, withdrawalId, rejectRequest("  ")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.reject(moderator, withdrawalId, null))
                .isInstanceOf(BadRequestException.class);

        verify(itemWithdrawalRequestRepository, never()).save(any());
        verify(queueService, never()).dequeue(any(), any());
    }

    @Test
    @DisplayName("reject: không tìm thấy -> ITEM_WITHDRAWAL_NOT_FOUND")
    void reject_notFound_throws() {
        when(itemWithdrawalRequestRepository.findByIdForUpdate(withdrawalId)).thenReturn(Optional.empty());

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.reject(moderator, withdrawalId, rejectRequest("Hàng không khớp mô tả")));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ITEM_WITHDRAWAL_NOT_FOUND);
    }

    @Test
    @DisplayName("reject: Moderator thuộc kho khác -> ITEM_WITHDRAWAL_FORBIDDEN")
    void reject_wrongWarehouse_throws() {
        pendingApproval(new BigDecimal("50000"));
        Account otherModerator = Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR)
                .warehouse(Warehouse.builder().id(UUID.randomUUID()).build()).build();

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.reject(otherModerator, withdrawalId, rejectRequest("Sai kho")));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ITEM_WITHDRAWAL_FORBIDDEN);
        verify(itemWithdrawalRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("reject: không còn PENDING_APPROVAL -> ITEM_WITHDRAWAL_NOT_PENDING_APPROVAL")
    void reject_notPendingApproval_throws() {
        ItemWithdrawalRequest r = pendingApproval(new BigDecimal("50000"));
        r.setStatus(ItemWithdrawalStatus.PENDING_PICKUP);

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.reject(moderator, withdrawalId, rejectRequest("Sai")));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ITEM_WITHDRAWAL_NOT_PENDING_APPROVAL);
    }

    @Test
    @DisplayName("reject: thành công -> REJECTED, lưu lý do, đóng việc trong hàng đợi, trả sản phẩm về ON_SALE")
    void reject_success_relistsProduct() {
        ItemWithdrawalRequest r = pendingApproval(new BigDecimal("50000"));
        product.setStatus(ProductStatus.WITHDRAWN);

        ItemWithdrawalResponse response = service.reject(
                moderator, withdrawalId, rejectRequest("Hàng không khớp mô tả"));

        assertThat(response.getStatus()).isEqualTo("REJECTED");
        assertThat(response.getRejectReason()).isEqualTo("Hàng không khớp mô tả");
        assertThat(r.getStatus()).isEqualTo(ItemWithdrawalStatus.REJECTED);
        assertThat(r.getModerator()).isEqualTo(moderator);
        assertThat(r.getReviewedAt()).isNotNull();
        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        verify(queueService).dequeue(QueueType.WITHDRAWAL, withdrawalId.toString());
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("reject: sản phẩm không còn WITHDRAWN -> không ghi đè lại trạng thái niêm yết")
    void reject_productNotWithdrawn_doesNotOverwriteStatus() {
        pendingApproval(new BigDecimal("50000"));
        product.setStatus(ProductStatus.ON_SALE);

        service.reject(moderator, withdrawalId, rejectRequest("Sai"));

        verify(productRepository, never()).save(product);
    }

    // ==================== payFee (API 91) ====================

    @Test
    @DisplayName("payFee: không tìm thấy yêu cầu -> ITEM_WITHDRAWAL_NOT_FOUND")
    void payFee_notFound_throws() {
        when(itemWithdrawalRequestRepository.findById(withdrawalId)).thenReturn(Optional.empty());

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.payFee(owner, withdrawalId, payRequest("WALLET"), "127.0.0.1"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ITEM_WITHDRAWAL_NOT_FOUND);
    }

    @Test
    @DisplayName("payFee: không phải chủ yêu cầu -> ITEM_WITHDRAWAL_FORBIDDEN")
    void payFee_notOwner_throws() {
        withdrawal(new BigDecimal("50000"));
        Account other = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.payFee(other, withdrawalId, payRequest("WALLET"), "127.0.0.1"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ITEM_WITHDRAWAL_FORBIDDEN);
        verifyNoInteractions(walletRepository, momoApi, vnPayApi);
    }

    @Test
    @DisplayName("payFee: yêu cầu đã EXPIRED (quá hạn trả phí hoặc quá hạn lấy hàng) -> ITEM_WITHDRAWAL_NOT_PENDING")
    void payFee_alreadyExpired_throws() {
        ItemWithdrawalRequest r = withdrawal(new BigDecimal("50000"));
        r.setStatus(ItemWithdrawalStatus.EXPIRED);

        AppException ex = org.junit.jupiter.api.Assertions.assertThrows(AppException.class,
                () -> service.payFee(owner, withdrawalId, payRequest("WALLET"), "127.0.0.1"));

        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ITEM_WITHDRAWAL_NOT_PENDING);
        verifyNoInteractions(walletRepository, momoApi, vnPayApi);
    }

    @Test
    @DisplayName("payFee: feeCharged = 0 -> coi như đã xong, không cần paymentMethod")
    void payFee_zeroFee_isNoop() {
        ItemWithdrawalRequest r = withdrawal(BigDecimal.ZERO);

        PayItemWithdrawalFeeResponse response = service.payFee(owner, withdrawalId, null, "127.0.0.1");

        assertThat(response.getId()).isEqualTo(withdrawalId.toString());
        assertThat(response.getFeeCharged()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getStatus()).isEqualTo("PENDING_PICKUP");
        assertThat(response.getPayUrl()).isNull();
        assertThat(r.getFeePaidAt()).isNull();
        verifyNoInteractions(walletRepository, momoApi, vnPayApi);
    }

    @Test
    @DisplayName("payFee: đã thanh toán xong trước đó -> idempotent, không trừ ví lần nữa")
    void payFee_alreadyPaid_isIdempotent() {
        ItemWithdrawalRequest r = withdrawal(new BigDecimal("50000"));
        r.setFeePaymentMethod(WithdrawalFeePaymentMethod.WALLET);
        r.setFeePaidAt(Instant.now());

        PayItemWithdrawalFeeResponse response = service.payFee(owner, withdrawalId, payRequest("WALLET"), "127.0.0.1");

        assertThat(response.getFeeCharged()).isEqualByComparingTo(new BigDecimal("50000"));
        verifyNoInteractions(walletRepository, momoApi, vnPayApi);
    }

    @Test
    @DisplayName("payFee: thiếu paymentMethod -> BadRequestException")
    void payFee_missingPaymentMethod_throws() {
        withdrawal(new BigDecimal("50000"));

        assertThatThrownBy(() -> service.payFee(owner, withdrawalId, payRequest(null), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("payFee: paymentMethod không hợp lệ -> BadRequestException")
    void payFee_invalidPaymentMethod_throws() {
        withdrawal(new BigDecimal("50000"));

        assertThatThrownBy(() -> service.payFee(owner, withdrawalId, payRequest("MIXED"), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("payFee: WALLET đủ tiền -> trừ ví ngay, chốt WALLET + feePaidAt")
    void payFee_wallet_success() {
        ItemWithdrawalRequest r = withdrawal(new BigDecimal("50000"));
        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).member(owner).balance(new BigDecimal("100000")).build();
        when(walletRepository.findByMemberIdForUpdate(owner.getId())).thenReturn(Optional.of(wallet));

        PayItemWithdrawalFeeResponse response = service.payFee(owner, withdrawalId, payRequest("WALLET"), "127.0.0.1");

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("50000"));
        verify(walletRepository).save(wallet);
        verify(walletTransactionRepository, org.mockito.Mockito.atLeast(2)).save(any());

        assertThat(r.getFeePaymentMethod()).isEqualTo(WithdrawalFeePaymentMethod.WALLET);
        assertThat(r.getFeePaidAt()).isNotNull();
        assertThat(response.getStatus()).isEqualTo("PENDING_PICKUP");
        assertThat(response.getFeeCharged()).isEqualByComparingTo(new BigDecimal("50000"));
        assertThat(response.getPayUrl()).isNull();
    }

    @Test
    @DisplayName("payFee: WALLET không có ví -> INSUFFICIENT_BALANCE")
    void payFee_wallet_noWallet_throws() {
        withdrawal(new BigDecimal("50000"));
        when(walletRepository.findByMemberIdForUpdate(owner.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.payFee(owner, withdrawalId, payRequest("WALLET"), "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    @DisplayName("payFee: WALLET không đủ số dư khả dụng -> INSUFFICIENT_BALANCE, không trừ ví")
    void payFee_wallet_insufficientBalance_throws() {
        ItemWithdrawalRequest r = withdrawal(new BigDecimal("50000"));
        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).member(owner)
                .balance(new BigDecimal("30000")).build();
        when(walletRepository.findByMemberIdForUpdate(owner.getId())).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.payFee(owner, withdrawalId, payRequest("WALLET"), "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("INSUFFICIENT_BALANCE");

        assertThat(wallet.getBalance()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(r.getFeePaidAt()).isNull();
        verify(walletRepository, never()).save(any());
    }

    @Test
    @DisplayName("payFee: WALLET khóa tiền trong lockedBalance không được tiêu")
    void payFee_wallet_locksRespected_throws() {
        withdrawal(new BigDecimal("50000"));
        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).member(owner)
                .balance(new BigDecimal("80000")).lockedBalance(new BigDecimal("40000")).build();
        when(walletRepository.findByMemberIdForUpdate(owner.getId())).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> service.payFee(owner, withdrawalId, payRequest("WALLET"), "127.0.0.1"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    @DisplayName("payFee: MOMO -> tạo giao dịch, trả payUrl, chưa chốt feePaidAt")
    void payFee_momo_success() {
        ItemWithdrawalRequest r = withdrawal(new BigDecimal("50000"));
        when(momoApi.createMomoQR(any())).thenReturn(CreateMomoResponse.builder()
                .resultCode(0)
                .payUrl("https://payment.momo.vn/v2/gateway/pay?token=abc")
                .build());

        PayItemWithdrawalFeeResponse response = service.payFee(owner, withdrawalId, payRequest("MOMO"), "127.0.0.1");

        assertThat(response.getPayUrl()).isEqualTo("https://payment.momo.vn/v2/gateway/pay?token=abc");
        assertThat(response.getId()).isNull();
        assertThat(response.getFeeCharged()).isNull();
        assertThat(response.getStatus()).isNull();
        assertThat(r.getMomoOrderId()).startsWith("IWR-" + withdrawalId);
        assertThat(r.getMomoRequestId()).isNotBlank();
        assertThat(r.getFeePaidAt()).isNull();
        verify(itemWithdrawalRequestRepository).save(r);
    }

    @Test
    @DisplayName("payFee: MOMO thất bại -> BadRequestException, không lưu tracking")
    void payFee_momo_failure_throws() {
        withdrawal(new BigDecimal("50000"));
        when(momoApi.createMomoQR(any())).thenReturn(CreateMomoResponse.builder()
                .resultCode(99)
                .message("Giao dich bi tu choi")
                .build());

        assertThatThrownBy(() -> service.payFee(owner, withdrawalId, payRequest("MOMO"), "127.0.0.1"))
                .isInstanceOf(BadRequestException.class);

        verify(itemWithdrawalRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("payFee: VNPAY -> build payUrl có chữ ký, chưa chốt feePaidAt")
    void payFee_vnpay_success() {
        ItemWithdrawalRequest r = withdrawal(new BigDecimal("50000"));

        PayItemWithdrawalFeeResponse response = service.payFee(owner, withdrawalId, payRequest("VNPAY"), "1.2.3.4");

        assertThat(response.getPayUrl()).startsWith(
                "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        assertThat(response.getPayUrl()).contains("vnp_SecureHash=");
        assertThat(r.getVnpTxnRef()).startsWith("IWR-" + withdrawalId);
        assertThat(r.getVnpCreateDate()).isNotBlank();
        assertThat(r.getFeePaidAt()).isNull();
        verify(itemWithdrawalRequestRepository).save(r);
    }

    // ==================== applyMomoFeePaymentResult / applyVnPayFeePaymentResult (IPN) ====================

    @Test
    @DisplayName("applyMomoFeePaymentResult: không khớp momoOrderId -> false")
    void applyMomoFeePaymentResult_notFound_returnsFalse() {
        when(itemWithdrawalRequestRepository.findByMomoOrderId("unknown")).thenReturn(Optional.empty());

        boolean handled = service.applyMomoFeePaymentResult("unknown", 0, "123");

        assertThat(handled).isFalse();
    }

    @Test
    @DisplayName("applyMomoFeePaymentResult: resultCode = 0 -> chốt WALLET... à MOMO, feePaidAt")
    void applyMomoFeePaymentResult_success_marksPaid() {
        ItemWithdrawalRequest r = withdrawal(new BigDecimal("50000"));
        when(itemWithdrawalRequestRepository.findByMomoOrderId("momo-order-1")).thenReturn(Optional.of(r));

        boolean handled = service.applyMomoFeePaymentResult("momo-order-1", 0, "999");

        assertThat(handled).isTrue();
        assertThat(r.getFeePaymentMethod()).isEqualTo(WithdrawalFeePaymentMethod.MOMO);
        assertThat(r.getFeePaidAt()).isNotNull();
        verify(itemWithdrawalRequestRepository).save(r);
    }

    @Test
    @DisplayName("applyMomoFeePaymentResult: resultCode khác 0 -> không chốt, vẫn trả true")
    void applyMomoFeePaymentResult_failure_doesNotMarkPaid() {
        ItemWithdrawalRequest r = withdrawal(new BigDecimal("50000"));
        when(itemWithdrawalRequestRepository.findByMomoOrderId("momo-order-1")).thenReturn(Optional.of(r));

        boolean handled = service.applyMomoFeePaymentResult("momo-order-1", 99, "999");

        assertThat(handled).isTrue();
        assertThat(r.getFeePaymentMethod()).isNull();
        assertThat(r.getFeePaidAt()).isNull();
        verify(itemWithdrawalRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyMomoFeePaymentResult: đã thanh toán trước đó -> idempotent, không lưu lại")
    void applyMomoFeePaymentResult_alreadyPaid_isIdempotent() {
        ItemWithdrawalRequest r = withdrawal(new BigDecimal("50000"));
        r.setFeePaymentMethod(WithdrawalFeePaymentMethod.MOMO);
        r.setFeePaidAt(Instant.now());
        when(itemWithdrawalRequestRepository.findByMomoOrderId("momo-order-1")).thenReturn(Optional.of(r));

        boolean handled = service.applyMomoFeePaymentResult("momo-order-1", 0, "999");

        assertThat(handled).isTrue();
        verify(itemWithdrawalRequestRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyVnPayFeePaymentResult: không khớp vnp_TxnRef -> false")
    void applyVnPayFeePaymentResult_notFound_returnsFalse() {
        when(itemWithdrawalRequestRepository.findByVnpTxnRef("unknown")).thenReturn(Optional.empty());

        boolean handled = service.applyVnPayFeePaymentResult("unknown", "00", "123");

        assertThat(handled).isFalse();
    }

    @Test
    @DisplayName("applyVnPayFeePaymentResult: responseCode 00 -> chốt VNPAY, feePaidAt")
    void applyVnPayFeePaymentResult_success_marksPaid() {
        ItemWithdrawalRequest r = withdrawal(new BigDecimal("50000"));
        when(itemWithdrawalRequestRepository.findByVnpTxnRef("vnp-ref-1")).thenReturn(Optional.of(r));

        boolean handled = service.applyVnPayFeePaymentResult("vnp-ref-1", "00", "999");

        assertThat(handled).isTrue();
        assertThat(r.getFeePaymentMethod()).isEqualTo(WithdrawalFeePaymentMethod.VNPAY);
        assertThat(r.getFeePaidAt()).isNotNull();
        verify(itemWithdrawalRequestRepository).save(r);
    }

    @Test
    @DisplayName("applyVnPayFeePaymentResult: responseCode khác 00 -> không chốt, vẫn trả true")
    void applyVnPayFeePaymentResult_failure_doesNotMarkPaid() {
        ItemWithdrawalRequest r = withdrawal(new BigDecimal("50000"));
        when(itemWithdrawalRequestRepository.findByVnpTxnRef("vnp-ref-1")).thenReturn(Optional.of(r));

        boolean handled = service.applyVnPayFeePaymentResult("vnp-ref-1", "07", "999");

        assertThat(handled).isTrue();
        assertThat(r.getFeePaymentMethod()).isNull();
        verify(itemWithdrawalRequestRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Member xem yêu cầu rút hàng của mình (chi tiết + danh sách)
    // ------------------------------------------------------------------

    private Account member() {
        return Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
    }

    private ItemWithdrawalRequest withdrawalFor(Account member, BigDecimal fee,
                                                Instant feePaidAt, WithdrawalFeePaymentMethod method,
                                                ItemWithdrawalStatus status) {
        Warehouse warehouse = Warehouse.builder().id(UUID.randomUUID()).name("Kho Q7")
                .address("123 Nguyễn Văn Linh, Q7").openingHours("08:00 - 21:00").build();
        Product product = Product.builder()
                .id("Q7-AK-000845")
                .name("Áo khoác Uniqlo")
                .member(member)
                .warehouse(warehouse)
                .build();
        return ItemWithdrawalRequest.builder()
                .id(UUID.randomUUID())
                .product(product)
                .member(member)
                .feeCharged(fee)
                .feePaidAt(feePaidAt)
                .feePaymentMethod(method)
                .status(status)
                .pickupDeadline(Instant.parse("2026-11-08T00:00:00Z"))
                .createdAt(Instant.parse("2026-11-01T00:00:00Z"))
                .build();
    }

    @Test
    void getMine_returnsDetailWithProductAndWarehouseName() {
        Account member = member();
        ItemWithdrawalRequest withdrawal = withdrawalFor(member, new BigDecimal("30000"),
                Instant.parse("2026-11-01T01:00:00Z"), WithdrawalFeePaymentMethod.MOMO,
                ItemWithdrawalStatus.PENDING_PICKUP);
        when(itemWithdrawalRequestRepository.findDetailById(withdrawal.getId()))
                .thenReturn(Optional.of(withdrawal));

        ItemWithdrawalDetailResponse res = service.getMine(member, withdrawal.getId());

        assertThat(res.getId()).isEqualTo(withdrawal.getId().toString());
        assertThat(res.getProductId()).isEqualTo("Q7-AK-000845");
        assertThat(res.getProductName()).isEqualTo("Áo khoác Uniqlo");
        assertThat(res.getStatus()).isEqualTo("PENDING_PICKUP");
        assertThat(res.getFeeAmount()).isEqualByComparingTo("30000");
        assertThat(res.isFeePaid()).isTrue();
        assertThat(res.getPaymentMethod()).isEqualTo("MOMO");
        assertThat(res.getPickupDeadline()).isEqualTo(Instant.parse("2026-11-08T00:00:00Z"));
        assertThat(res.getWarehouseName()).isEqualTo("Kho Q7");
        assertThat(res.getWarehouseAddress()).isEqualTo("123 Nguyễn Văn Linh, Q7");
        assertThat(res.getWarehouseOpeningHours()).isEqualTo("08:00 - 21:00");
        assertThat(res.getCreatedAt()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
    }

    @Test
    @DisplayName("getMine: trả photoUrl (ảnh đầu, displayOrder nhỏ nhất) và completedAt sau khi bàn giao")
    void getMine_returnsPhotoUrlAndCompletedAt() {
        Account member = member();
        ItemWithdrawalRequest withdrawal = withdrawalFor(member, BigDecimal.ZERO, null, null,
                ItemWithdrawalStatus.COMPLETED);
        withdrawal.setCompletedAt(Instant.parse("2026-11-05T09:00:00Z"));
        when(itemWithdrawalRequestRepository.findDetailById(withdrawal.getId()))
                .thenReturn(Optional.of(withdrawal));
        when(productPhotoRepository.findByProduct_IdOrderByDisplayOrderAscIdAsc("Q7-AK-000845"))
                .thenReturn(List.of(
                        com.rehome.rehome.entity.marketplace.ProductPhoto.builder()
                                .imageUrl("https://cdn/first.jpg").displayOrder(0).build(),
                        com.rehome.rehome.entity.marketplace.ProductPhoto.builder()
                                .imageUrl("https://cdn/second.jpg").displayOrder(1).build()));

        ItemWithdrawalDetailResponse res = service.getMine(member, withdrawal.getId());

        assertThat(res.getPhotoUrl()).isEqualTo("https://cdn/first.jpg");
        assertThat(res.getCompletedAt()).isEqualTo(Instant.parse("2026-11-05T09:00:00Z"));
    }

    @Test
    @DisplayName("getMine: sản phẩm chưa có ảnh -> photoUrl = null")
    void getMine_productWithoutPhoto_photoUrlIsNull() {
        Account member = member();
        ItemWithdrawalRequest withdrawal = withdrawalFor(member, BigDecimal.ZERO, null, null,
                ItemWithdrawalStatus.PENDING_PICKUP);
        when(itemWithdrawalRequestRepository.findDetailById(withdrawal.getId()))
                .thenReturn(Optional.of(withdrawal));
        when(productPhotoRepository.findByProduct_IdOrderByDisplayOrderAscIdAsc("Q7-AK-000845"))
                .thenReturn(List.of());

        ItemWithdrawalDetailResponse res = service.getMine(member, withdrawal.getId());

        assertThat(res.getPhotoUrl()).isNull();
    }

    @Test
    @DisplayName("listMine: ảnh của cả trang lấy bằng một query, không N+1")
    void listMine_batchesPhotosAcrossResults() {
        Account member = member();
        ItemWithdrawalRequest withdrawal = withdrawalFor(member, BigDecimal.ZERO, null, null,
                ItemWithdrawalStatus.PENDING_PICKUP);
        when(itemWithdrawalRequestRepository.findDetailsByMember(member.getId(), null))
                .thenReturn(List.of(withdrawal));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(List.of("Q7-AK-000845")))
                .thenReturn(List.of(
                        com.rehome.rehome.entity.marketplace.ProductPhoto.builder()
                                .imageUrl("https://cdn/first.jpg").product(withdrawal.getProduct()).build()));

        List<ItemWithdrawalDetailResponse> results = service.listMine(member, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getPhotoUrl()).isEqualTo("https://cdn/first.jpg");
        verify(productPhotoRepository, never()).findByProduct_IdOrderByDisplayOrderAscIdAsc(any());
    }

    @Test
    void getMine_feePaidIsTrue_whenWithdrawalIsFree() {
        Account member = member();
        ItemWithdrawalRequest withdrawal = withdrawalFor(member, BigDecimal.ZERO, null, null,
                ItemWithdrawalStatus.PENDING_PICKUP);
        when(itemWithdrawalRequestRepository.findDetailById(withdrawal.getId()))
                .thenReturn(Optional.of(withdrawal));

        ItemWithdrawalDetailResponse res = service.getMine(member, withdrawal.getId());

        // Rút trong cửa sổ 7 ngày miễn phí: không có gì phải trả nên app không hiện nút trả phí
        assertThat(res.isFeePaid()).isTrue();
        assertThat(res.getPaymentMethod()).isNull();
    }

    @Test
    void getMine_feePaidIsFalse_whenFeeChargedButNotPaid() {
        Account member = member();
        ItemWithdrawalRequest withdrawal = withdrawalFor(member, new BigDecimal("30000"), null, null,
                ItemWithdrawalStatus.PENDING_PICKUP);
        when(itemWithdrawalRequestRepository.findDetailById(withdrawal.getId()))
                .thenReturn(Optional.of(withdrawal));

        ItemWithdrawalDetailResponse res = service.getMine(member, withdrawal.getId());

        assertThat(res.isFeePaid()).isFalse();
        assertThat(res.getPaymentMethod()).isNull();
    }

    @Test
    @DisplayName("chưa trả phí -> trả feePaymentDeadline để mobile hiện đồng hồ đếm ngược")
    void getMine_feeNotPaid_exposesFeePaymentDeadline() {
        Account member = member();
        ItemWithdrawalRequest withdrawal = withdrawalFor(member, new BigDecimal("30000"), null, null,
                ItemWithdrawalStatus.PENDING_PICKUP);
        withdrawal.setFeePaymentDeadline(Instant.parse("2026-11-01T00:15:00Z"));
        when(itemWithdrawalRequestRepository.findDetailById(withdrawal.getId()))
                .thenReturn(Optional.of(withdrawal));

        ItemWithdrawalDetailResponse res = service.getMine(member, withdrawal.getId());

        assertThat(res.getFeePaymentDeadline()).isEqualTo(Instant.parse("2026-11-01T00:15:00Z"));
    }

    @Test
    @DisplayName("đã trả phí -> không còn hạn phải trả, feePaymentDeadline = null")
    void getMine_feeAlreadyPaid_hidesFeePaymentDeadline() {
        Account member = member();
        ItemWithdrawalRequest withdrawal = withdrawalFor(member, new BigDecimal("30000"),
                Instant.parse("2026-11-01T00:05:00Z"), WithdrawalFeePaymentMethod.WALLET,
                ItemWithdrawalStatus.PENDING_PICKUP);
        withdrawal.setFeePaymentDeadline(Instant.parse("2026-11-01T00:15:00Z"));
        when(itemWithdrawalRequestRepository.findDetailById(withdrawal.getId()))
                .thenReturn(Optional.of(withdrawal));

        ItemWithdrawalDetailResponse res = service.getMine(member, withdrawal.getId());

        assertThat(res.getFeePaymentDeadline()).isNull();
    }

    @Test
    void getMine_throwsNotFound_whenMissing() {
        UUID id = UUID.randomUUID();
        when(itemWithdrawalRequestRepository.findDetailById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMine(member(), id))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_WITHDRAWAL_NOT_FOUND);
    }

    @Test
    void getMine_throwsForbidden_whenRequestBelongsToAnotherMember() {
        ItemWithdrawalRequest withdrawal = withdrawalFor(member(), BigDecimal.ZERO, null, null,
                ItemWithdrawalStatus.PENDING_PICKUP);
        when(itemWithdrawalRequestRepository.findDetailById(withdrawal.getId()))
                .thenReturn(Optional.of(withdrawal));

        assertThatThrownBy(() -> service.getMine(member(), withdrawal.getId()))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ErrorCode.ITEM_WITHDRAWAL_FORBIDDEN);
    }

    @Test
    void listMine_passesNullStatus_whenFilterIsEmpty() {
        Account member = member();
        when(itemWithdrawalRequestRepository.findDetailsByMember(member.getId(), null))
                .thenReturn(List.of(withdrawalFor(member, BigDecimal.ZERO, null, null,
                        ItemWithdrawalStatus.COMPLETED)));

        assertThat(service.listMine(member, null)).hasSize(1);
        assertThat(service.listMine(member, "  ")).hasSize(1);

        verify(itemWithdrawalRequestRepository, times(2)).findDetailsByMember(member.getId(), null);
    }

    @Test
    void listMine_filtersByStatus_caseInsensitive() {
        Account member = member();
        when(itemWithdrawalRequestRepository.findDetailsByMember(
                member.getId(), ItemWithdrawalStatus.PENDING_PICKUP))
                .thenReturn(List.of(withdrawalFor(member, BigDecimal.ZERO, null, null,
                        ItemWithdrawalStatus.PENDING_PICKUP)));

        assertThat(service.listMine(member, "pending_pickup")).hasSize(1);

        verify(itemWithdrawalRequestRepository)
                .findDetailsByMember(member.getId(), ItemWithdrawalStatus.PENDING_PICKUP);
    }

    @Test
    void listMine_throwsBadRequest_whenStatusIsUnknown() {
        Account member = member();

        assertThatThrownBy(() -> service.listMine(member, "DA_HUY"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("status");

        verify(itemWithdrawalRequestRepository, never()).findDetailsByMember(any(), any());
    }
}