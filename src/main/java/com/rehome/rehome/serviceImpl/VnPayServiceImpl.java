package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.VnPayApi;
import com.rehome.rehome.component.MembershipGatewayPaymentBuilder;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.request.QueryVnPayRequest;
import com.rehome.rehome.dto.response.CreateVnPayResponse;
import com.rehome.rehome.dto.response.PaymentStatusResponse;
import com.rehome.rehome.dto.response.QueryVnPayResponse;
import com.rehome.rehome.dto.response.VnPayIpnResult;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.PaymentStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.Payment;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.PaymentRepository;
import com.rehome.rehome.service.ItemWithdrawalRequestService;
import com.rehome.rehome.service.MembershipService;
import com.rehome.rehome.dto.response.MembershipPaymentStatusResponse;
import com.rehome.rehome.entity.membership.MembershipPayment;
import com.rehome.rehome.repository.MembershipPaymentRepository;
import com.rehome.rehome.service.OrderService;
import com.rehome.rehome.service.ReturnShipBackService;
import com.rehome.rehome.service.VnPayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class VnPayServiceImpl implements VnPayService {

    @Value("${vnpay.tmn-code}")
    private String tmnCode;

    @Value("${vnpay.hash-secret}")
    private String hashSecret;

    @Value("${vnpay.pay-url}")
    private String payUrl;

    @Value("${vnpay.return-url}")
    private String returnUrl;

    @Value("${vnpay.version}")
    private String version;

    private final VnPayApi vnPayApi;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OrderService orderService;
    private final ItemWithdrawalRequestService itemWithdrawalRequestService;
    private final MembershipService membershipService;
    private final MembershipPaymentRepository membershipPaymentRepository;
    private final ReturnShipBackService returnShipBackService;

    @Override
    @Transactional
    public CreateVnPayResponse createPaymentForOrders(String email, List<UUID> orderIds, String customReturnUrl,
                                                       String clientIp) {

        List<Order> orders = orderService.loadPayableOrders(email, orderIds);

        for (Order order : orders) {
            Payment existing = paymentRepository.findByOrder_Id(order.getId()).orElse(null);
            if (existing != null && existing.getStatus() == PaymentStatus.SUCCESS) {
                throw new ConflictException(
                        "Đơn " + order.getOrderNo() + " đã được thanh toán.", "ORDER_ALREADY_PAID");
            }
        }

        String effectiveReturnUrl = StringUtils.hasText(customReturnUrl) ? customReturnUrl : returnUrl;
        String txnRef = orders.get(0).getId() + "-" + System.currentTimeMillis();
        String orderInfo = orders.size() == 1
                ? "Thanh toan don hang: " + orders.get(0).getOrderNo()
                : "Thanh toan " + orders.size() + " don hang ReHome";
        BigDecimal totalAmount = orders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long amount = totalAmount.setScale(0, RoundingMode.HALF_UP).longValueExact() * 100;

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String createDate = now.format(formatter);


        ZonedDateTime expireAt = orders.stream()
                .map(Order::getPaymentDeadline)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .map(d -> d.atZone(now.getZone()))
                .orElse(now.plusMinutes(SystemConfigKeys.DEFAULT_ORDER_PAYMENT_TIMEOUT_MINUTES));
        String expireDate = expireAt.format(formatter);

        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_Version", version);
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", tmnCode);
        fields.put("vnp_Amount", String.valueOf(amount));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", txnRef);
        fields.put("vnp_OrderInfo", orderInfo);
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", effectiveReturnUrl);
        fields.put("vnp_IpAddr", StringUtils.hasText(clientIp) ? clientIp : "127.0.0.1");
        fields.put("vnp_CreateDate", createDate);
        fields.put("vnp_ExpireDate", expireDate);

        String query = hashAllFields(fields);

        String secureHash;
        try {
            secureHash = hmacSHA512(hashSecret, query);
        } catch (Exception e) {
            log.error(">>>> Co loi khi hash VNPay: " + e);
            throw new BadRequestException("Không thể tạo chữ ký thanh toán");
        }

        String paymentUrl = payUrl + "?" + query + "&vnp_SecureHash=" + secureHash;

        for (Order order : orders) {
            Payment payment = paymentRepository.findByOrder_Id(order.getId())
                    .orElseGet(() -> Payment.builder().order(order).build());

            payment.setAmount(order.getTotalAmount());
            payment.setMethod("VNPAY");
            payment.setVnpTxnRef(txnRef);
            payment.setVnpCreateDate(createDate);
            payment.setVnpTransactionNo(null);
            payment.setStatus(PaymentStatus.PENDING);
            payment.setPaidAt(null);
            paymentRepository.save(payment);
        }

        return CreateVnPayResponse.builder()
                .txnRef(txnRef)
                .amount(totalAmount)
                .payUrl(paymentUrl)
                .build();
    }

    @Override
    @Transactional
    public VnPayIpnResult handleIpn(Map<String, String> params) {

        String receivedHash = params.get("vnp_SecureHash");
        Map<String, String> fields = new HashMap<>(params);
        fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");

        String expectedHash;
        try {
            expectedHash = hmacSHA512(hashSecret, hashAllFields(fields));
        } catch (Exception e) {
            log.error(">>>> Co loi khi verify chu ky IPN VNPay: " + e);
            return new VnPayIpnResult("97", "Invalid signature");
        }

        if (!expectedHash.equalsIgnoreCase(receivedHash)) {
            log.error(">>>> IPN signature mismatch cho vnp_TxnRef={}", params.get("vnp_TxnRef"));
            return new VnPayIpnResult("97", "Invalid signature");
        }

        String txnRef = params.get("vnp_TxnRef");

        if (MembershipGatewayPaymentBuilder.isMembershipRef(txnRef)) {
            if (membershipService.applyVnPayResult(txnRef, params.get("vnp_ResponseCode"),
                    params.get("vnp_TransactionNo"))) {
                return new VnPayIpnResult("00", "Confirm Success");
            }
            log.warn(">>>> IPN mua goi cho vnp_TxnRef={} khong khop giao dich nao, bo qua", txnRef);
            return new VnPayIpnResult("01", "Order not found");
        }

        if (ReturnShipBackService.isShipBackRef(txnRef)) {
            return returnShipBackService.handleVnPayIpn(
                    txnRef, params.get("vnp_Amount"), params.get("vnp_ResponseCode"), params.get("vnp_TransactionNo"));
        }

        List<Payment> payments = paymentRepository.findAllByVnpTxnRef(txnRef);
        if (payments.isEmpty()) {
            boolean handled = itemWithdrawalRequestService.applyVnPayFeePaymentResult(
                    params.get("vnp_TxnRef"), params.get("vnp_ResponseCode"), params.get("vnp_TransactionNo"));
            if (handled) {
                return new VnPayIpnResult("00", "Confirm Success");
            }
            log.warn(">>>> IPN cho vnp_TxnRef={} khong khop giao dich nao, bo qua", params.get("vnp_TxnRef"));
            return new VnPayIpnResult("01", "Order not found");
        }

        if (payments.stream().noneMatch(p -> p.getStatus() == PaymentStatus.PENDING)) {
            log.info(">>>> IPN cho vnp_TxnRef={} da duoc xu ly truoc do, bo qua", params.get("vnp_TxnRef"));
            return new VnPayIpnResult("02", "Order already confirmed");
        }

        long expectedAmount = totalAmount(payments).setScale(0, RoundingMode.HALF_UP).longValueExact() * 100;
        long receivedAmount;
        try {
            receivedAmount = Long.parseLong(params.get("vnp_Amount"));
        } catch (NumberFormatException e) {
            receivedAmount = -1;
        }
        if (receivedAmount != expectedAmount) {
            log.error(">>>> IPN sai so tien cho vnp_TxnRef={}: expected={}, received={}",
                    params.get("vnp_TxnRef"), expectedAmount, receivedAmount);
            return new VnPayIpnResult("04", "Invalid amount");
        }

        applyResult(payments, params.get("vnp_ResponseCode"), params.get("vnp_TransactionNo"));

        return new VnPayIpnResult("00", "Confirm Success");
    }

    @Override
    @Transactional
    public PaymentStatusResponse checkPaymentStatus(String email, UUID orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));

        if (!order.getBuyer().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("Đơn hàng này không thuộc về bạn");
        }

        Payment payment = paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Chưa có giao dịch VNPay nào cho đơn hàng này"));

        if (payment.getStatus() == PaymentStatus.PENDING) {
            syncWithVnPay(paymentRepository.findAllByVnpTxnRef(payment.getVnpTxnRef()));
        }

        return PaymentStatusResponse.builder()
                .orderIds(List.of(orderId))
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .vnpTransactionNo(payment.getVnpTransactionNo())
                .paidAt(payment.getPaidAt())
                .build();
    }

    @Override
    @Transactional
    public PaymentStatusResponse getReturnInfo(Map<String, String> params) {

        String receivedHash = params.get("vnp_SecureHash");
        Map<String, String> fields = new HashMap<>(params);
        fields.remove("vnp_SecureHash");
        fields.remove("vnp_SecureHashType");

        String expectedHash;
        try {
            expectedHash = hmacSHA512(hashSecret, hashAllFields(fields));
        } catch (Exception e) {
            log.error(">>>> Co loi khi verify chu ky return URL VNPay: " + e);
            throw new BadRequestException("Không thể xác thực kết quả thanh toán");
        }

        if (!expectedHash.equalsIgnoreCase(receivedHash)) {
            log.error(">>>> Chu ky tren return URL khong hop le cho vnp_TxnRef={}", params.get("vnp_TxnRef"));
            throw new BadRequestException("Chữ ký không hợp lệ");
        }

        List<Payment> payments = paymentRepository.findAllByVnpTxnRef(params.get("vnp_TxnRef"));
        if (payments.isEmpty()) {
            throw new IllegalArgumentException("Không tìm thấy giao dịch tương ứng");
        }

        if (payments.stream().anyMatch(p -> p.getStatus() == PaymentStatus.PENDING)) {
            syncWithVnPay(payments);
        }

        Payment first = payments.get(0);
        return PaymentStatusResponse.builder()
                .orderIds(payments.stream().map(p -> p.getOrder().getId()).toList())
                .status(first.getStatus())
                .amount(totalAmount(payments))
                .vnpTransactionNo(first.getVnpTransactionNo())
                .paidAt(first.getPaidAt())
                .build();
    }

    @Override
    public MembershipPaymentStatusResponse checkMembershipPayment(UUID memberId, UUID paymentId) {
        MembershipPayment payment = ownedMembershipPayment(memberId, paymentId);
        if (payment.getStatus() == PaymentStatus.PENDING && payment.getVnpTxnRef() != null) {
            QueryVnPayResponse result = queryVnPay(payment.getVnpTxnRef(), payment.getVnpCreateDate());
            if (result != null && "00".equals(result.getResponseCode())
                    && payment.getVnpTxnRef().equals(result.getTxnRef())
                    && tmnCode.equals(result.getTmnCode()) && validQuerySignature(result)
                    && result.getAmount() != null && payment.getAmount().movePointRight(2)
                            .compareTo(java.math.BigDecimal.valueOf(result.getAmount())) == 0
                    && ("00".equals(result.getTransactionStatus()) || "02".equals(result.getTransactionStatus()))) {
                membershipService.applyVnPayResult(payment.getVnpTxnRef(),
                        result.getTransactionStatus(), result.getTransactionNo());
                payment = ownedMembershipPayment(memberId, paymentId);
            }
        }
        return new MembershipPaymentStatusResponse(payment.getId(), payment.getStatus(),
                payment.getSubscription().getId(), payment.getSubscription().getStatus());
    }

    private MembershipPayment ownedMembershipPayment(UUID memberId, UUID paymentId) {
        return membershipPaymentRepository.findByIdAndMember_Id(paymentId, memberId)
                .orElseThrow(() -> new com.rehome.rehome.exception.AppException(
                        com.rehome.rehome.exception.ErrorCode.MEMBERSHIP_PAYMENT_NOT_FOUND));
    }

    private boolean validQuerySignature(QueryVnPayResponse response) {
        if (response.getSecureHash() == null) {
            return false;
        }
        String fields = java.util.stream.Stream.of(response.getResponseId(), response.getCommand(),
                response.getResponseCode(), response.getMessage(), response.getTmnCode(), response.getTxnRef(),
                response.getAmount(), response.getBankCode(), response.getPayDate(), response.getTransactionNo(),
                response.getTransactionType(), response.getTransactionStatus(), response.getOrderInfo(),
                response.getPromotionCode(), response.getPromotionAmount())
                .map(value -> value == null ? "" : value.toString())
                .collect(java.util.stream.Collectors.joining("|"));
        return java.security.MessageDigest.isEqual(
                com.rehome.rehome.util.GatewaySignature.hmacSHA512(hashSecret, fields).toLowerCase(java.util.Locale.ROOT)
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                response.getSecureHash().toLowerCase(java.util.Locale.ROOT)
                        .getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    private QueryVnPayResponse queryVnPay(String txnRef, String transactionDate) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String createDate = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String orderInfo = "Truy van giao dich " + txnRef;

        String hashData = String.join("|",
                requestId, version, "querydr", tmnCode, txnRef,
                transactionDate, createDate, "127.0.0.1", orderInfo);

        String signature;
        try {
            signature = hmacSHA512(hashSecret, hashData);
        } catch (Exception e) {
            log.error(">>>> Co loi khi tao chu ky querydr VNPay: " + e);
            return null;
        }

        QueryVnPayRequest request = QueryVnPayRequest.builder()
                .requestId(requestId)
                .version(version)
                .command("querydr")
                .tmnCode(tmnCode)
                .txnRef(txnRef)
                .orderInfo(orderInfo)
                .transactionDate(transactionDate)
                .createDate(createDate)
                .ipAddr("127.0.0.1")
                .secureHash(signature)
                .build();

        QueryVnPayResponse response;
        try {
            response = vnPayApi.queryTransaction(request);
        } catch (Exception e) {
            log.error(">>>> Khong the truy van trang thai giao dich VNPay: " + e);
            return null;
        }

        return response;
    }

    private void syncWithVnPay(List<Payment> payments) {
        Payment payment = payments.get(0);

        QueryVnPayResponse response = queryVnPay(payment.getVnpTxnRef(), payment.getVnpCreateDate());

        if (response == null) {
            return;
        }

        log.info(">>>> Query response tu VNPay: txnRef={}, responseCode={}, transactionStatus={}, transactionNo={}, message={}",
                response.getTxnRef(), response.getResponseCode(), response.getTransactionStatus(),
                response.getTransactionNo(), response.getMessage());

        if (!"00".equals(response.getResponseCode())) {
            log.warn(">>>> Truy van VNPay loi cho txnRef={}: {}", payment.getVnpTxnRef(), response.getMessage());
            return;
        }

        if ("01".equals(response.getTransactionStatus())) {
            return;
        }

        applyResult(payments, response.getTransactionStatus(), response.getTransactionNo());
    }

    private void applyResult(List<Payment> payments, String resultCode, String transactionNo) {
        for (Payment payment : payments) {
            if (!"00".equals(resultCode)) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
                continue;
            }

            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setVnpTransactionNo(transactionNo);
            payment.setPaidAt(Instant.now());
            paymentRepository.save(payment);

            orderService.applyGatewayPaymentSuccess(
                    payment.getOrder().getId(),
                    OrderPaymentMethod.VNPAY,
                    "Hoàn tiền VNPay về sau khi đơn " + payment.getOrder().getOrderNo()
                            + " không còn chờ thanh toán");
        }
    }

    private static BigDecimal totalAmount(List<Payment> payments) {
        return payments.stream()
                .map(Payment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String hashAllFields(Map<String, String> fields) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        java.util.Collections.sort(fieldNames);

        List<String> pairs = new ArrayList<>();
        for (String fieldName : fieldNames) {
            String fieldValue = fields.get(fieldName);
            if (StringUtils.hasText(fieldValue)) {
                pairs.add(fieldName + "=" + urlEncode(fieldValue));
            }
        }
        return String.join("&", pairs);
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.US_ASCII.toString());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String hmacSHA512(String key, String data) throws Exception {

        Mac hmac512 = Mac.getInstance("HmacSHA512");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        hmac512.init(secretKeySpec);

        byte[] hash = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();

        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }

        return hexString.toString();
    }
}
