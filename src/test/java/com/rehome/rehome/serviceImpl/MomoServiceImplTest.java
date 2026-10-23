package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.MomoApi;
import com.rehome.rehome.dto.request.CreateMomoRequest;
import com.rehome.rehome.dto.request.MomoIpnRequest;
import com.rehome.rehome.dto.response.CreateMomoResponse;
import com.rehome.rehome.dto.response.PaymentStatusResponse;
import com.rehome.rehome.dto.response.QueryMomoResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.PaymentStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.Payment;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MomoServiceImplTest {

    @Mock MomoApi momoApi;
    @Mock OrderRepository orderRepository;
    @Mock PaymentRepository paymentRepository;

    @InjectMocks
    MomoServiceImpl momoService;

    static final String PARTNER_CODE = "MOMO";
    static final String ACCESS_KEY = "accessKeyTest";
    static final String SECRET_KEY = "secretKeyTest";
    static final String DEFAULT_REDIRECT_URL = "http://localhost:3000/";
    static final String IPN_URL = "http://localhost:8080/api/momo/ipn-handler";
    static final String REQUEST_TYPE = "captureWallet";

    String email;
    UUID orderId;
    Order order;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(momoService, "partnerCode", PARTNER_CODE);
        ReflectionTestUtils.setField(momoService, "accessKey", ACCESS_KEY);
        ReflectionTestUtils.setField(momoService, "secretKey", SECRET_KEY);
        ReflectionTestUtils.setField(momoService, "redirectUrl", DEFAULT_REDIRECT_URL);
        ReflectionTestUtils.setField(momoService, "ipnUrl", IPN_URL);
        ReflectionTestUtils.setField(momoService, "requestType", REQUEST_TYPE);

        email = "buyer@example.com";
        orderId = UUID.randomUUID();
        Account buyer = Account.builder().id(UUID.randomUUID()).email(email).build();
        order = Order.builder()
                .id(orderId)
                .orderNo("ORD-0001")
                .buyer(buyer)
                .totalAmount(new BigDecimal("150000.00"))
                .status(OrderStatus.PENDING_PAYMENT)
                .build();
    }

    private static String hmacSha256(String data, String key) throws Exception {
        Mac hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    private static String resultSignature(long amount, String extraData, String message, String orderId,
                                           String orderInfo, String orderType, String payType, String requestId,
                                           long responseTime, int resultCode, long transId) throws Exception {
        String raw = String.format(
                "accessKey=%s&amount=%s&extraData=%s&message=%s&orderId=%s&orderInfo=%s&orderType=%s&partnerCode=%s&payType=%s&requestId=%s&responseTime=%s&resultCode=%s&transId=%s",
                ACCESS_KEY, amount, extraData, message, orderId, orderInfo, orderType, PARTNER_CODE,
                payType, requestId, responseTime, resultCode, transId);
        return hmacSha256(raw, SECRET_KEY);
    }

    // ==================== createPaymentForOrder ====================

    @Test
    void createPaymentForOrder_orderNotFound_throws() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> momoService.createPaymentForOrder(email, orderId, null))
                .isInstanceOf(IllegalArgumentException.class);

        verify(momoApi, never()).createMomoQR(any());
    }

    @Test
    void createPaymentForOrder_notOwner_throwsAccessDenied() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> momoService.createPaymentForOrder("other@example.com", orderId, null))
                .isInstanceOf(AccessDeniedException.class);

        verify(momoApi, never()).createMomoQR(any());
    }

    @Test
    void createPaymentForOrder_notPendingPayment_throwsConflict() {
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> momoService.createPaymentForOrder(email, orderId, null))
                .isInstanceOf(ConflictException.class);

        verify(momoApi, never()).createMomoQR(any());
    }

    @Test
    void createPaymentForOrder_alreadyPaid_throwsConflict() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        Payment paidPayment = Payment.builder().order(order).status(PaymentStatus.SUCCESS).build();
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.of(paidPayment));

        assertThatThrownBy(() -> momoService.createPaymentForOrder(email, orderId, null))
                .isInstanceOf(ConflictException.class);

        verify(momoApi, never()).createMomoQR(any());
    }

    @Test
    void createPaymentForOrder_momoRejects_throwsBadRequest() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.empty());
        when(momoApi.createMomoQR(any())).thenReturn(
                CreateMomoResponse.builder().resultCode(99).message("Loi tu MoMo").build());

        assertThatThrownBy(() -> momoService.createPaymentForOrder(email, orderId, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Loi tu MoMo");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void createPaymentForOrder_success_savesNewPendingPayment() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.empty());
        when(momoApi.createMomoQR(any())).thenReturn(
                CreateMomoResponse.builder().resultCode(0).message("Thanh cong").payUrl("https://momo/pay").build());

        CreateMomoResponse response = momoService.createPaymentForOrder(email, orderId, null);

        assertThat(response.getPayUrl()).isEqualTo("https://momo/pay");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getOrder()).isSameAs(order);
        assertThat(saved.getAmount()).isEqualTo(order.getTotalAmount());
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getMomoOrderId()).startsWith(orderId.toString());
        assertThat(saved.getMomoRequestId()).isNotBlank();
    }

    @Test
    void createPaymentForOrder_retry_reusesExistingPaymentRow() {
        Payment existing = Payment.builder()
                .order(order)
                .amount(order.getTotalAmount())
                .status(PaymentStatus.FAILED)
                .momoOrderId("old-momo-order-id")
                .build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.of(existing));
        when(momoApi.createMomoQR(any())).thenReturn(
                CreateMomoResponse.builder().resultCode(0).message("Thanh cong").build());

        momoService.createPaymentForOrder(email, orderId, null);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue()).isSameAs(existing);
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(captor.getValue().getMomoOrderId()).isNotEqualTo("old-momo-order-id");
    }

    @Test
    void createPaymentForOrder_customRedirectUrl_overridesDefault() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.empty());
        when(momoApi.createMomoQR(any())).thenReturn(
                CreateMomoResponse.builder().resultCode(0).message("Thanh cong").build());

        momoService.createPaymentForOrder(email, orderId, "rehome://payment-result");

        ArgumentCaptor<CreateMomoRequest> captor = ArgumentCaptor.forClass(CreateMomoRequest.class);
        verify(momoApi).createMomoQR(captor.capture());
        assertThat(captor.getValue().getRedirectUrl()).isEqualTo("rehome://payment-result");
    }

    // ==================== handleIpn ====================

    @Test
    void handleIpn_invalidSignature_throwsBadRequest() {
        MomoIpnRequest ipn = new MomoIpnRequest();
        ipn.setOrderId("some-momo-order-id");
        ipn.setResultCode(0);
        ipn.setSignature("chu-ky-sai");

        assertThatThrownBy(() -> momoService.handleIpn(ipn))
                .isInstanceOf(BadRequestException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handleIpn_paymentNotFound_doesNothing() throws Exception {
        MomoIpnRequest ipn = validIpn("unknown-momo-order-id", 0, 12345L);
        when(paymentRepository.findByMomoOrderId("unknown-momo-order-id")).thenReturn(Optional.empty());

        momoService.handleIpn(ipn);

        verify(paymentRepository, never()).save(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void handleIpn_alreadyProcessed_skipsUpdate() throws Exception {
        Payment payment = Payment.builder().order(order).status(PaymentStatus.SUCCESS).momoOrderId("momo-1").build();
        MomoIpnRequest ipn = validIpn("momo-1", 0, 12345L);
        when(paymentRepository.findByMomoOrderId("momo-1")).thenReturn(Optional.of(payment));

        momoService.handleIpn(ipn);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    void handleIpn_resultCodeZero_marksSuccessAndPaysOrder() throws Exception {
        Payment payment = Payment.builder().order(order).status(PaymentStatus.PENDING).momoOrderId("momo-1").build();
        MomoIpnRequest ipn = validIpn("momo-1", 0, 999L);
        when(paymentRepository.findByMomoOrderId("momo-1")).thenReturn(Optional.of(payment));

        momoService.handleIpn(ipn);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(payment.getMomoTransactionId()).isEqualTo("999");
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(orderRepository).save(order);
        verify(paymentRepository).save(payment);
    }

    @Test
    void handleIpn_resultCodeNonZero_marksFailed() throws Exception {
        Payment payment = Payment.builder().order(order).status(PaymentStatus.PENDING).momoOrderId("momo-1").build();
        MomoIpnRequest ipn = validIpn("momo-1", 1006, 999L);
        when(paymentRepository.findByMomoOrderId("momo-1")).thenReturn(Optional.of(payment));

        momoService.handleIpn(ipn);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(orderRepository, never()).save(any());
        verify(paymentRepository).save(payment);
    }

    private MomoIpnRequest validIpn(String momoOrderId, int resultCode, long transId) throws Exception {
        MomoIpnRequest ipn = new MomoIpnRequest();
        ipn.setPartnerCode(PARTNER_CODE);
        ipn.setOrderId(momoOrderId);
        ipn.setRequestId("req-1");
        ipn.setAmount(150000);
        ipn.setOrderInfo("Thanh toan don hang: ORD-0001");
        ipn.setOrderType("momo_wallet");
        ipn.setTransId(transId);
        ipn.setResultCode(resultCode);
        ipn.setMessage("Thanh cong");
        ipn.setPayType("qr");
        ipn.setResponseTime(1700000000000L);
        ipn.setExtraData("");

        String signature = resultSignature(ipn.getAmount(), ipn.getExtraData(), ipn.getMessage(), ipn.getOrderId(),
                ipn.getOrderInfo(), ipn.getOrderType(), ipn.getPayType(), ipn.getRequestId(), ipn.getResponseTime(),
                ipn.getResultCode(), ipn.getTransId());
        ipn.setSignature(signature);
        return ipn;
    }

    // ==================== checkPaymentStatus ====================

    @Test
    void checkPaymentStatus_orderNotFound_throws() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> momoService.checkPaymentStatus(email, orderId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checkPaymentStatus_notOwner_throwsAccessDenied() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> momoService.checkPaymentStatus("other@example.com", orderId))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void checkPaymentStatus_noPaymentRow_throwsNotFound() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> momoService.checkPaymentStatus(email, orderId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void checkPaymentStatus_alreadyFinal_doesNotQueryMomo() {
        Payment payment = Payment.builder().order(order).status(PaymentStatus.SUCCESS)
                .amount(order.getTotalAmount()).momoOrderId("momo-1").build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.of(payment));

        PaymentStatusResponse response = momoService.checkPaymentStatus(email, orderId);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(momoApi, never()).queryTransaction(any());
    }

    @Test
    void checkPaymentStatus_pending_syncsSuccessFromMomo() throws Exception {
        Payment payment = Payment.builder().order(order).status(PaymentStatus.PENDING)
                .amount(order.getTotalAmount()).momoOrderId("momo-1").momoRequestId("req-1").build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.of(payment));

        QueryMomoResponse momoResponse = queryResponse("momo-1", "req-1", 0, 555L);
        when(momoApi.queryTransaction(any())).thenReturn(momoResponse);

        PaymentStatusResponse response = momoService.checkPaymentStatus(email, orderId);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getMomoTransactionId()).isEqualTo("555");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void checkPaymentStatus_pendingStillProcessing_staysPending() throws Exception {
        Payment payment = Payment.builder().order(order).status(PaymentStatus.PENDING)
                .amount(order.getTotalAmount()).momoOrderId("momo-1").momoRequestId("req-1").build();
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrder_Id(orderId)).thenReturn(Optional.of(payment));

        QueryMomoResponse momoResponse = queryResponse("momo-1", "req-1", 7002, 0L);
        when(momoApi.queryTransaction(any())).thenReturn(momoResponse);

        PaymentStatusResponse response = momoService.checkPaymentStatus(email, orderId);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).save(any());
    }

    private QueryMomoResponse queryResponse(String momoOrderId, String requestId, int resultCode, long transId) throws Exception {
        QueryMomoResponse response = QueryMomoResponse.builder()
                .partnerCode(PARTNER_CODE)
                .orderId(momoOrderId)
                .requestId(requestId)
                .amount(150000)
                .extraData("")
                .orderInfo("Thanh toan don hang: ORD-0001")
                .orderType("momo_wallet")
                .payType("qr")
                .transId(transId)
                .resultCode(resultCode)
                .message("Thanh cong")
                .responseTime(1700000000000L)
                .build();

        String signature = resultSignature(response.getAmount(), response.getExtraData(), response.getMessage(),
                response.getOrderId(), response.getOrderInfo(), response.getOrderType(), response.getPayType(),
                response.getRequestId(), response.getResponseTime(), response.getResultCode(), response.getTransId());
        response.setSignature(signature);
        return response;
    }
}
