package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.VnPayApi;
import com.rehome.rehome.dto.request.QueryVnPayRequest;
import com.rehome.rehome.dto.response.CreateVnPayResponse;
import com.rehome.rehome.dto.response.PaymentStatusResponse;
import com.rehome.rehome.dto.response.QueryVnPayResponse;
import com.rehome.rehome.dto.response.VnPayIpnResult;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.PaymentStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.Payment;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.PaymentRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.ItemWithdrawalRequestService;
import com.rehome.rehome.service.OrderService;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VnPayServiceImplTest {

    @Mock VnPayApi vnPayApi;
    @Mock OrderRepository orderRepository;
    @Mock PaymentRepository paymentRepository;
    @Mock OrderService orderService;

    @InjectMocks
    VnPayServiceImpl vnPayService;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ItemWithdrawalRequestService itemWithdrawalRequestService;

    static final String TMN_CODE = "TMN12345";
    static final String HASH_SECRET = "hashSecretTest";
    static final String PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    static final String RETURN_URL = "http://localhost:3000/payment/vnpay-return";
    static final String VERSION = "2.1.0";
    static final String SECURE_HASH_SEPARATOR = "&vnp_SecureHash=";

    String email;
    Account buyer;
    UUID orderId1;
    UUID orderId2;
    Order order1;
    Order order2;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(vnPayService, "tmnCode", TMN_CODE);
        ReflectionTestUtils.setField(vnPayService, "hashSecret", HASH_SECRET);
        ReflectionTestUtils.setField(vnPayService, "payUrl", PAY_URL);
        ReflectionTestUtils.setField(vnPayService, "returnUrl", RETURN_URL);
        ReflectionTestUtils.setField(vnPayService, "version", VERSION);

        email = "buyer@example.com";
        buyer = Account.builder().id(UUID.randomUUID()).email(email).build();

        orderId1 = UUID.randomUUID();
        orderId2 = UUID.randomUUID();
        order1 = Order.builder()
                .id(orderId1).orderNo("OD-2026-000001").buyer(buyer)
                .totalAmount(new BigDecimal("150000.00"))
                .status(OrderStatus.PENDING_PAYMENT)
                .build();
        order2 = Order.builder()
                .id(orderId2).orderNo("OD-2026-000002").buyer(buyer)
                .totalAmount(new BigDecimal("250000.00"))
                .status(OrderStatus.PENDING_PAYMENT)
                .build();

        when(orderRepository.findById(orderId1)).thenReturn(Optional.of(order1));
        when(orderRepository.findById(orderId2)).thenReturn(Optional.of(order2));
    }

    // ==================== helpers: tái hiện thuật toán chữ ký của VNPay ====================

    private static String hmacSha512(String key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        return sb.toString();
    }

    /** key=value sắp theo key, bỏ value rỗng, value url-encode, nối bằng '&'. */
    private static String hashData(Map<String, String> fields) throws Exception {
        List<String> names = new ArrayList<>(fields.keySet());
        Collections.sort(names);
        List<String> pairs = new ArrayList<>();
        for (String name : names) {
            String value = fields.get(name);
            if (value != null && !value.isBlank()) {
                pairs.add(name + "=" + URLEncoder.encode(value, StandardCharsets.US_ASCII.toString()));
            }
        }
        return String.join("&", pairs);
    }

    private Map<String, String> signedParams(String txnRef, long amountTimes100, String responseCode,
                                             String transactionNo) throws Exception {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TmnCode", TMN_CODE);
        params.put("vnp_TxnRef", txnRef);
        params.put("vnp_Amount", String.valueOf(amountTimes100));
        params.put("vnp_ResponseCode", responseCode);
        params.put("vnp_TransactionNo", transactionNo);
        params.put("vnp_OrderInfo", "Thanh toan don hang");
        params.put("vnp_BankCode", "NCB");
        params.put("vnp_SecureHashType", "HmacSHA512");
        // vnp_SecureHashType không nằm trong chuỗi hash, giống service
        Map<String, String> toHash = new HashMap<>(params);
        toHash.remove("vnp_SecureHashType");
        params.put("vnp_SecureHash", hmacSha512(HASH_SECRET, hashData(toHash)));
        return params;
    }

    private Payment pendingPayment(Order order, String txnRef, String amount) {
        return Payment.builder()
                .order(order)
                .method("VNPAY")
                .status(PaymentStatus.PENDING)
                .amount(new BigDecimal(amount))
                .vnpTxnRef(txnRef)
                .vnpCreateDate("20261030101010")
                .build();
    }

    private QueryVnPayResponse queryResponse(String responseCode, String transactionStatus, String transactionNo) {
        QueryVnPayResponse resp = mock(QueryVnPayResponse.class);
        when(resp.getResponseCode()).thenReturn(responseCode);
        when(resp.getTransactionStatus()).thenReturn(transactionStatus);
        when(resp.getTransactionNo()).thenReturn(transactionNo);
        when(resp.getMessage()).thenReturn("msg");
        when(resp.getTxnRef()).thenReturn("txn");
        return resp;
    }

    private String queryOf(CreateVnPayResponse response) {
        String url = response.getPayUrl();
        assertThat(url).startsWith(PAY_URL + "?");
        String afterPrefix = url.substring(PAY_URL.length() + 1);
        int idx = afterPrefix.indexOf(SECURE_HASH_SEPARATOR);
        assertThat(idx).isPositive();
        return afterPrefix.substring(0, idx);
    }

    // ==================== createPaymentForOrders ====================

    @Test
    @DisplayName("createPaymentForOrders: OrderService từ chối đơn -> lỗi được ném ra nguyên vẹn, không lưu payment")
    void create_orderNotPayable_propagates() {
        when(orderService.loadPayableOrders(email, List.of(orderId1)))
                .thenThrow(new ConflictException("Đơn đã được thanh toán.", "ORDER_ALREADY_PAID"));

        assertThatThrownBy(() -> vnPayService.createPaymentForOrders(email, List.of(orderId1), null, "1.2.3.4"))
                .isInstanceOf(ConflictException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPaymentForOrders: đã có payment SUCCESS -> ORDER_ALREADY_PAID, không lưu")
    void create_paymentAlreadySuccess_throwsConflict() {
        when(orderService.loadPayableOrders(email, List.of(orderId1))).thenReturn(List.of(order1));
        Payment paid = Payment.builder().order(order1).status(PaymentStatus.SUCCESS).build();
        when(paymentRepository.findByOrder_Id(orderId1)).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> vnPayService.createPaymentForOrders(email, List.of(orderId1), null, "1.2.3.4"))
                .isInstanceOf(ConflictException.class)
                .extracting(ex -> ((ConflictException) ex).getCode())
                .isEqualTo("ORDER_ALREADY_PAID");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPaymentForOrders: lô nhiều đơn, một đơn đã SUCCESS -> chặn cả lô")
    void create_batchWithOnePaidOrder_blocksWholeBatch() {
        when(orderService.loadPayableOrders(email, List.of(orderId1, orderId2))).thenReturn(List.of(order1, order2));
        when(paymentRepository.findByOrder_Id(orderId1)).thenReturn(Optional.empty());
        when(paymentRepository.findByOrder_Id(orderId2))
                .thenReturn(Optional.of(Payment.builder().order(order2).status(PaymentStatus.SUCCESS).build()));

        assertThatThrownBy(() -> vnPayService.createPaymentForOrders(
                email, List.of(orderId1, orderId2), null, "1.2.3.4"))
                .isInstanceOf(ConflictException.class);

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPaymentForOrders: một đơn -> payUrl có đủ tham số, số tiền x100, chữ ký HMAC-SHA512 đúng")
    void create_singleOrder_buildsSignedUrl() throws Exception {
        when(orderService.loadPayableOrders(email, List.of(orderId1))).thenReturn(List.of(order1));
        when(paymentRepository.findByOrder_Id(orderId1)).thenReturn(Optional.empty());

        CreateVnPayResponse response = vnPayService.createPaymentForOrders(email, List.of(orderId1), null, "1.2.3.4");

        assertThat(response.getAmount()).isEqualByComparingTo("150000");
        assertThat(response.getTxnRef()).startsWith(orderId1 + "-");

        String query = queryOf(response);
        assertThat(query)
                .contains("vnp_Amount=15000000")
                .contains("vnp_Command=pay")
                .contains("vnp_CurrCode=VND")
                .contains("vnp_TmnCode=" + TMN_CODE)
                .contains("vnp_Version=" + VERSION)
                .contains("vnp_TxnRef=" + URLEncoder.encode(response.getTxnRef(), StandardCharsets.US_ASCII))
                .contains("vnp_OrderInfo=" + URLEncoder.encode("Thanh toan don hang: OD-2026-000001", StandardCharsets.US_ASCII))
                .contains("vnp_ReturnUrl=" + URLEncoder.encode(RETURN_URL, StandardCharsets.US_ASCII))
                .contains("vnp_IpAddr=1.2.3.4")
                .contains("vnp_CreateDate=")
                .contains("vnp_ExpireDate=");

        String expectedHash = hmacSha512(HASH_SECRET, query);
        assertThat(response.getPayUrl()).endsWith(SECURE_HASH_SEPARATOR + expectedHash);
    }

    @Test
    @DisplayName("createPaymentForOrders: lưu payment PENDING cho đơn với đúng txnRef, số tiền, method")
    void create_singleOrder_savesPendingPayment() {
        when(orderService.loadPayableOrders(email, List.of(orderId1))).thenReturn(List.of(order1));
        when(paymentRepository.findByOrder_Id(orderId1)).thenReturn(Optional.empty());

        CreateVnPayResponse response = vnPayService.createPaymentForOrders(email, List.of(orderId1), null, "1.2.3.4");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getOrder()).isSameAs(order1);
        assertThat(saved.getMethod()).isEqualTo("VNPAY");
        assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(saved.getAmount()).isEqualByComparingTo("150000");
        assertThat(saved.getVnpTxnRef()).isEqualTo(response.getTxnRef());
        assertThat(saved.getVnpCreateDate()).matches("\\d{14}");
        assertThat(saved.getVnpTransactionNo()).isNull();
        assertThat(saved.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("createPaymentForOrders: đã có payment PENDING/FAILED -> dùng lại dòng cũ, xoá transactionNo và paidAt")
    void create_existingNonSuccessPayment_isReusedAndReset() {
        when(orderService.loadPayableOrders(email, List.of(orderId1))).thenReturn(List.of(order1));
        Payment failed = pendingPayment(order1, "old-ref", "150000");
        failed.setStatus(PaymentStatus.FAILED);
        failed.setVnpTransactionNo("999");
        failed.setPaidAt(Instant.now());
        when(paymentRepository.findByOrder_Id(orderId1)).thenReturn(Optional.of(failed));

        CreateVnPayResponse response = vnPayService.createPaymentForOrders(email, List.of(orderId1), null, "1.2.3.4");

        verify(paymentRepository).save(failed);
        assertThat(failed.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(failed.getVnpTxnRef()).isEqualTo(response.getTxnRef()).isNotEqualTo("old-ref");
        assertThat(failed.getVnpTransactionNo()).isNull();
        assertThat(failed.getPaidAt()).isNull();
    }

    @Test
    @DisplayName("createPaymentForOrders: customReturnUrl có giá trị -> dùng thay returnUrl mặc định")
    void create_customReturnUrl_isUsed() {
        when(orderService.loadPayableOrders(email, List.of(orderId1))).thenReturn(List.of(order1));
        when(paymentRepository.findByOrder_Id(orderId1)).thenReturn(Optional.empty());
        String custom = "myapp://payment/result";

        CreateVnPayResponse response = vnPayService.createPaymentForOrders(email, List.of(orderId1), custom, "1.2.3.4");

        assertThat(queryOf(response))
                .contains("vnp_ReturnUrl=" + URLEncoder.encode(custom, StandardCharsets.US_ASCII))
                .doesNotContain(URLEncoder.encode(RETURN_URL, StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("createPaymentForOrders: customReturnUrl trắng -> dùng returnUrl mặc định")
    void create_blankCustomReturnUrl_fallsBackToDefault() {
        when(orderService.loadPayableOrders(email, List.of(orderId1))).thenReturn(List.of(order1));
        when(paymentRepository.findByOrder_Id(orderId1)).thenReturn(Optional.empty());

        CreateVnPayResponse response = vnPayService.createPaymentForOrders(email, List.of(orderId1), "  ", "1.2.3.4");

        assertThat(queryOf(response))
                .contains("vnp_ReturnUrl=" + URLEncoder.encode(RETURN_URL, StandardCharsets.US_ASCII));
    }

    @Test
    @DisplayName("createPaymentForOrders: clientIp null hoặc trắng -> 127.0.0.1")
    void create_missingClientIp_defaultsToLoopback() {
        when(orderService.loadPayableOrders(email, List.of(orderId1))).thenReturn(List.of(order1));
        when(paymentRepository.findByOrder_Id(orderId1)).thenReturn(Optional.empty());

        assertThat(queryOf(vnPayService.createPaymentForOrders(email, List.of(orderId1), null, null)))
                .contains("vnp_IpAddr=127.0.0.1");
        assertThat(queryOf(vnPayService.createPaymentForOrders(email, List.of(orderId1), null, " ")))
                .contains("vnp_IpAddr=127.0.0.1");
    }

    @Test
    @DisplayName("createPaymentForOrders: lô nhiều đơn -> tổng tiền, orderInfo gọn, chung txnRef theo đơn đầu, mỗi đơn một payment")
    void create_multipleOrders_sharesOneTransaction() {
        when(orderService.loadPayableOrders(email, List.of(orderId1, orderId2))).thenReturn(List.of(order1, order2));
        when(paymentRepository.findByOrder_Id(any(UUID.class))).thenReturn(Optional.empty());

        CreateVnPayResponse response = vnPayService.createPaymentForOrders(
                email, List.of(orderId1, orderId2), null, "1.2.3.4");

        assertThat(response.getAmount()).isEqualByComparingTo("400000");
        assertThat(response.getTxnRef()).startsWith(orderId1 + "-");
        assertThat(queryOf(response))
                .contains("vnp_Amount=40000000")
                .contains("vnp_OrderInfo=" + URLEncoder.encode("Thanh toan 2 don hang ReHome", StandardCharsets.US_ASCII));

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository, times(2)).save(captor.capture());
        List<Payment> saved = captor.getAllValues();
        assertThat(saved).extracting(Payment::getVnpTxnRef).containsOnly(response.getTxnRef());
        assertThat(saved).extracting(p -> p.getOrder().getId()).containsExactly(orderId1, orderId2);
        assertThat(saved.get(0).getAmount()).isEqualByComparingTo("150000");
        assertThat(saved.get(1).getAmount()).isEqualByComparingTo("250000");
    }

    @Test
    @DisplayName("createPaymentForOrders: số tiền lẻ được làm tròn HALF_UP trước khi nhân 100")
    void create_amountIsRoundedHalfUp() {
        order1.setTotalAmount(new BigDecimal("150000.50"));
        when(orderService.loadPayableOrders(email, List.of(orderId1))).thenReturn(List.of(order1));
        when(paymentRepository.findByOrder_Id(orderId1)).thenReturn(Optional.empty());

        CreateVnPayResponse response = vnPayService.createPaymentForOrders(email, List.of(orderId1), null, "1.2.3.4");

        assertThat(queryOf(response)).contains("vnp_Amount=15000100");
    }

    // ==================== handleIpn ====================

    @Test
    @DisplayName("handleIpn: chữ ký sai -> 97, không tra DB")
    void ipn_invalidSignature_returns97() throws Exception {
        Map<String, String> params = signedParams("ref-1", 15000000, "00", "123");
        params.put("vnp_SecureHash", "deadbeef");

        VnPayIpnResult result = vnPayService.handleIpn(params);

        assertThat(result).usingRecursiveComparison().isEqualTo(new VnPayIpnResult("97", "Invalid signature"));
        verifyNoInteractions(paymentRepository);
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("handleIpn: sửa tham số sau khi ký (vd đổi vnp_Amount) -> chữ ký không khớp -> 97")
    void ipn_tamperedParams_returns97() throws Exception {
        Map<String, String> params = signedParams("ref-1", 15000000, "00", "123");
        params.put("vnp_Amount", "100");

        VnPayIpnResult result = vnPayService.handleIpn(params);

        assertThat(result).usingRecursiveComparison().isEqualTo(new VnPayIpnResult("97", "Invalid signature"));
    }

    @Test
    @DisplayName("handleIpn: thiếu vnp_SecureHash -> 97")
    void ipn_missingSignature_returns97() throws Exception {
        Map<String, String> params = signedParams("ref-1", 15000000, "00", "123");
        params.remove("vnp_SecureHash");

        VnPayIpnResult result = vnPayService.handleIpn(params);

        assertThat(result).usingRecursiveComparison().isEqualTo(new VnPayIpnResult("97", "Invalid signature"));
    }

    @Test
    @DisplayName("checkPaymentStatus: PENDING, VNPay báo thành công -> cập nhật SUCCESS và xác nhận đơn")
    void check_pending_vnPayReportsSuccess() {
        Payment payment = pendingPayment(order1, "ref-1", "150000");
        QueryVnPayResponse vnPayResponse = queryResponse("00", "00", "888");
        when(paymentRepository.findByOrder_Id(orderId1)).thenReturn(Optional.of(payment));
        when(paymentRepository.findAllByVnpTxnRef("ref-1")).thenReturn(List.of(payment));
        when(vnPayApi.queryTransaction(any(QueryVnPayRequest.class))).thenReturn(vnPayResponse);

        PaymentStatusResponse response = vnPayService.checkPaymentStatus(email, orderId1);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getVnpTransactionNo()).isEqualTo("888");
        assertThat(response.getPaidAt()).isNotNull();
        verify(orderService).applyGatewayPaymentSuccess(eq(orderId1), eq(OrderPaymentMethod.VNPAY), anyString());
    }

    @Test
    @DisplayName("checkPaymentStatus: PENDING, VNPay báo thất bại (status khác 00/01) -> FAILED, không xác nhận đơn")
    void check_pending_vnPayReportsFailure() {
        Payment payment = pendingPayment(order1, "ref-1", "150000");
        QueryVnPayResponse vnPayResponse = queryResponse("00", "02", null);
        when(paymentRepository.findByOrder_Id(orderId1)).thenReturn(Optional.of(payment));
        when(paymentRepository.findAllByVnpTxnRef("ref-1")).thenReturn(List.of(payment));
        when(vnPayApi.queryTransaction(any(QueryVnPayRequest.class))).thenReturn(vnPayResponse);

        PaymentStatusResponse response = vnPayService.checkPaymentStatus(email, orderId1);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("checkPaymentStatus: VNPay báo giao dịch còn đang xử lý (01) -> giữ PENDING")
    void check_pending_vnPayStillProcessing_staysPending() {
        Payment payment = pendingPayment(order1, "ref-1", "150000");
        QueryVnPayResponse vnPayResponse = queryResponse("00", "01", null);
        when(paymentRepository.findByOrder_Id(orderId1)).thenReturn(Optional.of(payment));
        when(paymentRepository.findAllByVnpTxnRef("ref-1")).thenReturn(List.of(payment));
        when(vnPayApi.queryTransaction(any(QueryVnPayRequest.class))).thenReturn(vnPayResponse);

        PaymentStatusResponse response = vnPayService.checkPaymentStatus(email, orderId1);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("checkPaymentStatus: bản thân truy vấn lỗi (responseCode khác 00) -> giữ PENDING")
    void check_pending_queryItselfFailed_staysPending() {
        Payment payment = pendingPayment(order1, "ref-1", "150000");
        QueryVnPayResponse vnPayResponse = queryResponse("91", "00", "888");
        when(paymentRepository.findByOrder_Id(orderId1)).thenReturn(Optional.of(payment));
        when(paymentRepository.findAllByVnpTxnRef("ref-1")).thenReturn(List.of(payment));
        when(vnPayApi.queryTransaction(any(QueryVnPayRequest.class))).thenReturn(vnPayResponse);

        PaymentStatusResponse response = vnPayService.checkPaymentStatus(email, orderId1);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verify(paymentRepository, never()).save(any());
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("checkPaymentStatus: đơn nằm trong lô trả gộp -> áp kết quả cho cả các đơn anh em")
    void check_pending_batchSiblingsAreUpdatedToo() {
        Payment p1 = pendingPayment(order1, "ref-batch", "150000");
        Payment p2 = pendingPayment(order2, "ref-batch", "250000");
        QueryVnPayResponse vnPayResponse = queryResponse("00", "00", "999");
        when(paymentRepository.findByOrder_Id(orderId1)).thenReturn(Optional.of(p1));
        when(paymentRepository.findAllByVnpTxnRef("ref-batch")).thenReturn(List.of(p1, p2));
        when(vnPayApi.queryTransaction(any(QueryVnPayRequest.class))).thenReturn(vnPayResponse);

        PaymentStatusResponse response = vnPayService.checkPaymentStatus(email, orderId1);

        assertThat(response.getOrderIds()).containsExactly(orderId1);
        assertThat(p1.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(p2.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        verify(orderService).applyGatewayPaymentSuccess(eq(orderId1), eq(OrderPaymentMethod.VNPAY), anyString());
        verify(orderService).applyGatewayPaymentSuccess(eq(orderId2), eq(OrderPaymentMethod.VNPAY), anyString());
    }

    @Test
    @DisplayName("getReturnInfo: còn payment PENDING (IPN chưa tới) -> hỏi VNPay một lần rồi trả trạng thái mới")
    void returnInfo_pending_syncsWithVnPay() throws Exception {
        Map<String, String> params = signedParams("ref-1", 15000000, "00", "123");
        Payment payment = pendingPayment(order1, "ref-1", "150000");
        QueryVnPayResponse vnPayResponse = queryResponse("00", "00", "321");
        when(paymentRepository.findAllByVnpTxnRef("ref-1")).thenReturn(List.of(payment));
        when(vnPayApi.queryTransaction(any(QueryVnPayRequest.class))).thenReturn(vnPayResponse);

        PaymentStatusResponse response = vnPayService.getReturnInfo(params);

        verify(vnPayApi, times(1)).queryTransaction(any(QueryVnPayRequest.class));
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getVnpTransactionNo()).isEqualTo("321");
        verify(orderService).applyGatewayPaymentSuccess(eq(orderId1), eq(OrderPaymentMethod.VNPAY), anyString());
    }

    @Test
    @DisplayName("getReturnInfo: còn PENDING nhưng VNPay báo đang xử lý -> giữ PENDING")
    void returnInfo_pending_vnPayStillProcessing_staysPending() throws Exception {
        Map<String, String> params = signedParams("ref-1", 15000000, "00", "123");
        Payment payment = pendingPayment(order1, "ref-1", "150000");
        QueryVnPayResponse vnPayResponse = queryResponse("00", "01", null);
        when(paymentRepository.findAllByVnpTxnRef("ref-1")).thenReturn(List.of(payment));
        when(vnPayApi.queryTransaction(any(QueryVnPayRequest.class))).thenReturn(vnPayResponse);

        PaymentStatusResponse response = vnPayService.getReturnInfo(params);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        verifyNoInteractions(orderService);
    }
}
