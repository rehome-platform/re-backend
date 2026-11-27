package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnApi;
import com.rehome.rehome.client.VnPayApi;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.component.BusinessNotifications;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.request.GhnCreateOrderRequest;
import com.rehome.rehome.dto.request.PayShipBackFeeRequest;
import com.rehome.rehome.dto.response.GhnCreateOrderResponse;
import com.rehome.rehome.dto.response.PayShipBackFeeResponse;
import com.rehome.rehome.dto.response.QueryVnPayResponse;
import com.rehome.rehome.dto.response.ReturnRequestResponse;
import com.rehome.rehome.dto.response.ShipBackPaymentStatusResponse;
import com.rehome.rehome.dto.response.VnPayIpnResult;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.InvalidReturnChoice;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.ShipBackPaymentMethod;
import com.rehome.rehome.entity.enums.ShipBackStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.PlatformFund;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.exception.GhnException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.PlatformFundRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.service.ReturnRequestService;
import com.rehome.rehome.service.SystemConfigReader;
import com.rehome.rehome.util.GatewaySignature;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReturnShipBackServiceImplTest {

    private static final String EMAIL = "buyer@rehome.com";
    private static final String TMN = "TMN123";
    private static final String SECRET = "secret-key-test";
    private static final String GHN_TOKEN = "ghn-token";
    private static final String GHN_SHOP = "ghn-shop";

    @Mock private AccountRepository accountRepository;
    @Mock private ReturnRequestRepository returnRequestRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private ShippingAddressRepository shippingAddressRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private PlatformFundRepository platformFundRepository;
    @Mock private VnPayApi vnPayApi;
    @Mock private GhnApi ghnApi;
    @Mock private QueueService queueService;
    @Mock private ReturnRequestService returnRequestService;
    @Mock private SystemConfigReader systemConfigReader;
    @Mock private BusinessNotifications businessNotifications;
    @Mock private AuditLogger auditLogger;

    @InjectMocks
    private ReturnShipBackServiceImpl service;

    private Account buyer;
    private Account moderator;
    private Warehouse warehouse;
    private Order order;
    private ReturnRequest returnRequest;
    private UUID rrId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "vnpTmnCode", TMN);
        ReflectionTestUtils.setField(service, "vnpHashSecret", SECRET);
        ReflectionTestUtils.setField(service, "vnpPayUrl", "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        ReflectionTestUtils.setField(service, "vnpReturnUrl", "http://localhost:3000/vnpay-return");
        ReflectionTestUtils.setField(service, "vnpVersion", "2.1.0");
        ReflectionTestUtils.setField(service, "ghnToken", GHN_TOKEN);
        ReflectionTestUtils.setField(service, "ghnShopId", GHN_SHOP);
        ReflectionTestUtils.setField(service, "defaultPackageWeightGrams", 500);
        ReflectionTestUtils.setField(service, "defaultPackageLengthCm", 20);
        ReflectionTestUtils.setField(service, "defaultPackageWidthCm", 15);
        ReflectionTestUtils.setField(service, "defaultPackageHeightCm", 10);

        when(systemConfigReader.getInt(eq(SystemConfigKeys.RETURN_SHIP_BACK_SHIP_WITHIN_DAYS), anyInt())).thenReturn(2);

        UUID buyerId = UUID.randomUUID();
        buyer = Account.builder().id(buyerId).email(EMAIL).build();
        when(accountRepository.findByEmail(EMAIL)).thenReturn(Optional.of(buyer));

        warehouse = Warehouse.builder()
                .id(UUID.randomUUID())
                .name("Kho Quận 7")
                .phone("0281234567")
                .address("100 Nguyễn Thị Thập")
                .wardName("Phường Tân Phú")
                .provinceName("Hồ Chí Minh")
                .isNewAddress(true)
                .build();

        moderator = Account.builder()
                .id(UUID.randomUUID())
                .role(Role.MODERATOR)
                .warehouse(warehouse)
                .build();

        order = Order.builder()
                .id(UUID.randomUUID())
                .orderNo("ORD-2026-0001")
                .buyer(buyer)
                .warehouse(warehouse)
                .subtotal(new BigDecimal("300000"))
                .shippingFee(new BigDecimal("25000"))
                .totalAmount(new BigDecimal("325000"))
                .build();

        rrId = UUID.randomUUID();
        returnRequest = ReturnRequest.builder()
                .id(rrId)
                .order(order)
                .status(ReturnRequestStatus.INVALID)
                .invalidReturnChoice(InvalidReturnChoice.SHIP_BACK)
                .shipBackStatus(ShipBackStatus.AWAITING_PAYMENT)
                .shipBackFee(new BigDecimal("25000"))
                .shipBackFeeDeadline(Instant.now().plus(20, ChronoUnit.MINUTES))
                .shipBackAddressId(UUID.randomUUID())
                .shipBackRecipientName("Nguyễn Văn Mua")
                .shipBackPhone("0909000111")
                .shipBackAddressDetail("12 Lê Lợi")
                .shipBackWardName("Phường Bến Nghé")
                .shipBackProvinceName("Hồ Chí Minh")
                .pickupDeadline(Instant.now().plus(6, ChronoUnit.DAYS))
                .handoverDeadline(Instant.now().minus(1, ChronoUnit.DAYS))
                .reason("Không vừa")
                .build();

        when(returnRequestRepository.findByIdForUpdate(rrId)).thenReturn(Optional.of(returnRequest));
    }

    @Test
    @DisplayName("payFee WALLET: trừ ví, ghi bút toán đối ứng + doanh thu RETURN_SHIP_BACK_FEE_REVENUE, set PAID và đẩy vào hàng đợi RETURN_SHIP_BACK")
    void payFee_wallet_success() {
        Wallet wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .member(buyer)
                .balance(new BigDecimal("100000"))
                .lockedBalance(new BigDecimal("20000"))
                .build();
        PlatformFund fund = PlatformFund.builder()
                .id(UUID.randomUUID())
                .balance(new BigDecimal("500000"))
                .build();
        when(walletRepository.findByMemberIdForUpdate(buyer.getId())).thenReturn(Optional.of(wallet));
        when(platformFundRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(fund));

        PayShipBackFeeResponse res = service.payFee(EMAIL, rrId, new PayShipBackFeeRequest("WALLET"), "127.0.0.1");

        assertThat(res.getPaid()).isTrue();
        assertThat(res.getPayUrl()).isNull();
        assertThat(wallet.getBalance()).isEqualByComparingTo("75000");
        assertThat(fund.getBalance()).isEqualByComparingTo("525000");
        assertThat(returnRequest.getShipBackStatus()).isEqualTo(ShipBackStatus.PAID);
        assertThat(returnRequest.getShipBackPaymentMethod()).isEqualTo(ShipBackPaymentMethod.WALLET);
        assertThat(returnRequest.getShipBackPaidAt()).isNotNull();

        ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, times(3)).save(txCaptor.capture());
        List<WalletTransaction> savedTxs = txCaptor.getAllValues();

        assertThat(savedTxs.get(0).getWalletType()).isEqualTo(WalletType.MEMBER);
        assertThat(savedTxs.get(0).getType()).isEqualTo(WalletTransactionType.RETURN_SHIP_BACK_FEE);
        assertThat(savedTxs.get(0).getAmount()).isEqualByComparingTo("-25000");

        assertThat(savedTxs.get(1).getWalletType()).isEqualTo(WalletType.PLATFORM);
        assertThat(savedTxs.get(1).getType()).isEqualTo(WalletTransactionType.RETURN_SHIP_BACK_FEE);
        assertThat(savedTxs.get(1).getAmount()).isEqualByComparingTo("25000");
        assertThat(savedTxs.get(1).getCounterpartTransaction()).isSameAs(savedTxs.get(0));

        assertThat(savedTxs.get(2).getWalletType()).isEqualTo(WalletType.PLATFORM);
        assertThat(savedTxs.get(2).getType()).isEqualTo(WalletTransactionType.RETURN_SHIP_BACK_FEE_REVENUE);
        assertThat(savedTxs.get(2).getAmount()).isEqualByComparingTo("25000");
        assertThat(savedTxs.get(2).getCounterpartTransaction()).isNull();

        verify(queueService).enqueue(QueueType.RETURN_SHIP_BACK, rrId.toString(), warehouse);
        verify(businessNotifications).returned(eq(returnRequest), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("payFee: kiểm tra đủ 3 lỗi 409 ALREADY_PAID, FEE_EXPIRED, INSUFFICIENT_BALANCE")
    void payFee_conflictErrors() {
        returnRequest.setShipBackStatus(ShipBackStatus.PAID);
        assertThatThrownBy(() -> service.payFee(EMAIL, rrId, new PayShipBackFeeRequest("WALLET"), "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("RETURN_SHIP_BACK_ALREADY_PAID");

        returnRequest.setShipBackStatus(ShipBackStatus.AWAITING_PAYMENT);
        returnRequest.setShipBackFeeDeadline(Instant.now().minusSeconds(5));
        assertThatThrownBy(() -> service.payFee(EMAIL, rrId, new PayShipBackFeeRequest("WALLET"), "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("RETURN_SHIP_BACK_FEE_EXPIRED");

        returnRequest.setShipBackFeeDeadline(Instant.now().plus(10, ChronoUnit.MINUTES));
        Wallet lowWallet = Wallet.builder().id(UUID.randomUUID()).member(buyer)
                .balance(new BigDecimal("30000")).lockedBalance(new BigDecimal("10000")).build();
        when(walletRepository.findByMemberIdForUpdate(buyer.getId())).thenReturn(Optional.of(lowWallet));
        assertThatThrownBy(() -> service.payFee(EMAIL, rrId, new PayShipBackFeeRequest("WALLET"), "127.0.0.1"))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("INSUFFICIENT_BALANCE");
    }

    @Test
    @DisplayName("payFee VNPAY + handleVnPayIpn + syncPaymentStatus")
    void payFee_vnpay_ipnAndSync() {
        PlatformFund fund = PlatformFund.builder().id(UUID.randomUUID()).balance(new BigDecimal("500000")).build();
        when(platformFundRepository.findTopByOrderByIdAsc()).thenReturn(Optional.of(fund));

        PayShipBackFeeResponse payRes = service.payFee(EMAIL, rrId, new PayShipBackFeeRequest("VNPAY"), "1.2.3.4");
        assertThat(payRes.getPayUrl()).contains("vnp_TxnRef=RSB-");
        assertThat(payRes.getPaid()).isNull();
        String txnRef = returnRequest.getShipBackVnpTxnRef();
        assertThat(txnRef).startsWith("RSB-");

        when(returnRequestRepository.findByShipBackVnpTxnRefForUpdate(txnRef)).thenReturn(Optional.of(returnRequest));

        // Sai số tiền -> 04
        assertThat(service.handleVnPayIpn(txnRef, "99900", "00", "TX1").rspCode()).isEqualTo("04");
        assertThat(returnRequest.getShipBackStatus()).isEqualTo(ShipBackStatus.AWAITING_PAYMENT);

        // Sync qua querydr khi IPN chưa tới
        String fields = String.join("|", "resp-1", "querydr", "00", "OK", TMN, txnRef,
                "2500000", "NCB", "20261127133000", "TX123", "01", "00", "ShipBack", "", "");
        QueryVnPayResponse queryRes = QueryVnPayResponse.builder()
                .responseId("resp-1").command("querydr").responseCode("00").message("OK").tmnCode(TMN)
                .txnRef(txnRef).amount(2500000L).bankCode("NCB").payDate("20261127133000")
                .transactionNo("TX123").transactionType("01").transactionStatus("00").orderInfo("ShipBack")
                .secureHash(GatewaySignature.hmacSHA512(SECRET, fields))
                .build();
        when(vnPayApi.queryTransaction(any())).thenReturn(queryRes);

        ShipBackPaymentStatusResponse synced = service.syncPaymentStatus(EMAIL, rrId);
        assertThat(synced.isPaid()).isTrue();
        assertThat(synced.getShipBackStatus()).isEqualTo("PAID");
        assertThat(returnRequest.getShipBackPaymentMethod()).isEqualTo(ShipBackPaymentMethod.VNPAY);
        assertThat(fund.getBalance()).isEqualByComparingTo("500000");

        ArgumentCaptor<WalletTransaction> txCaptor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository, times(1)).save(txCaptor.capture());
        assertThat(txCaptor.getValue().getType()).isEqualTo(WalletTransactionType.RETURN_SHIP_BACK_FEE_REVENUE);
        assertThat(txCaptor.getValue().getAmount()).isEqualByComparingTo("25000");
        verify(queueService).enqueue(QueueType.RETURN_SHIP_BACK, rrId.toString(), warehouse);

        // IPN đến sau khi đã sync -> 02 Order already confirmed
        VnPayIpnResult duplicateIpn = service.handleVnPayIpn(txnRef, "2500000", "00", "TX123");
        assertThat(duplicateIpn.rspCode()).isEqualTo("02");
    }

    @Test
    @DisplayName("ship: kiểm kho Moderator, chặn chưa PAID (409), lỗi GHN trả 502 giữ PAID, thành công chuyển SHIPPING + dequeue + audit log")
    void ship_moderatorFlow() {
        // Khác kho -> 403
        Account otherMod = Account.builder()
                .id(UUID.randomUUID()).role(Role.MODERATOR)
                .warehouse(Warehouse.builder().id(UUID.randomUUID()).build())
                .build();
        assertThatThrownBy(() -> service.ship(otherMod, rrId)).isInstanceOf(ForbiddenException.class);

        // Chưa PAID -> 409 RETURN_SHIP_BACK_NOT_PAID
        assertThatThrownBy(() -> service.ship(moderator, rrId))
                .isInstanceOf(ConflictException.class)
                .extracting("code")
                .isEqualTo("RETURN_SHIP_BACK_NOT_PAID");

        // GHN lỗi -> 502 GhnException, giữ nguyên PAID để thử lại
        returnRequest.setShipBackStatus(ShipBackStatus.PAID);
        Product product = Product.builder().id("Q7-AO-001").name("Áo sơ mi").build();
        OrderItem item = OrderItem.builder().id(UUID.randomUUID()).order(order)
                .product(product).priceAtOrder(new BigDecimal("300000")).build();
        when(orderItemRepository.findAllByOrder_IdWithProductOrderByIdAsc(order.getId())).thenReturn(List.of(item));
        when(ghnApi.createOrder(eq(GHN_TOKEN), eq(GHN_SHOP), any())).thenThrow(new RuntimeException("GHN down"));

        assertThatThrownBy(() -> service.ship(moderator, rrId)).isInstanceOf(GhnException.class);
        assertThat(returnRequest.getShipBackStatus()).isEqualTo(ShipBackStatus.PAID);
        verify(queueService, never()).dequeue(any(), anyString());

        // GHN thành công -> SHIPPING, COD = 0, dequeue, audit log SHIP_BACK_RETURN
        GhnCreateOrderResponse okResp = org.mockito.Mockito.mock(
                GhnCreateOrderResponse.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(okResp.getData().getOrderCode()).thenReturn("GHN-SB-999");
        org.mockito.Mockito.doReturn(okResp).when(ghnApi).createOrder(eq(GHN_TOKEN), eq(GHN_SHOP), any());
        when(returnRequestService.getDetailForModerator(rrId))
                .thenReturn(ReturnRequestResponse.builder().id(rrId.toString()).shipBackTrackingCode("GHN-SB-999").build());

        ReturnRequestResponse res = service.ship(moderator, rrId);

        assertThat(res.getShipBackTrackingCode()).isEqualTo("GHN-SB-999");
        assertThat(returnRequest.getShipBackStatus()).isEqualTo(ShipBackStatus.SHIPPING);
        assertThat(returnRequest.getShipBackTrackingCode()).isEqualTo("GHN-SB-999");
        assertThat(returnRequest.getShipBackShippedAt()).isNotNull();

        ArgumentCaptor<GhnCreateOrderRequest> ghnReqCaptor = ArgumentCaptor.forClass(GhnCreateOrderRequest.class);
        verify(ghnApi, times(2)).createOrder(eq(GHN_TOKEN), eq(GHN_SHOP), ghnReqCaptor.capture());
        GhnCreateOrderRequest sent = ghnReqCaptor.getValue();
        assertThat(sent.getCodAmount()).isZero();
        assertThat(sent.getFromName()).isEqualTo("Kho Quận 7");
        assertThat(sent.getToName()).isEqualTo("Nguyễn Văn Mua");
        assertThat(sent.getToWardName()).isEqualTo("Phường Bến Nghé");
        assertThat(sent.getToProvinceName()).isEqualTo("Hồ Chí Minh");

        verify(queueService).dequeue(QueueType.RETURN_SHIP_BACK, rrId.toString());
        verify(auditLogger).logWarehouseOp(eq(moderator), eq(warehouse), any(),
                eq("SHIP_BACK_RETURN"), eq("RETURN_REQUEST"), eq(rrId.toString()), eq("GHN-SB-999"), eq("ORD-2026-0001"));
        verify(businessNotifications).returned(eq(returnRequest), any(), anyString(), anyString());
    }
}
