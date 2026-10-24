package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.VnPayApi;
import com.rehome.rehome.dto.request.QueryVnPayRequest;
import com.rehome.rehome.dto.response.CreateVnPayResponse;
import com.rehome.rehome.dto.response.PaymentStatusResponse;
import com.rehome.rehome.dto.response.QueryVnPayResponse;
import com.rehome.rehome.dto.response.VnPayIpnResult;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.PaymentStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.Payment;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.PaymentRepository;
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
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @Override
    @Transactional
    public CreateVnPayResponse createPaymentForOrder(String email, UUID orderId, String customReturnUrl, String clientIp) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng"));

        if (!order.getBuyer().getEmail().equalsIgnoreCase(email)) {
            throw new AccessDeniedException("Đơn hàng này không thuộc về bạn");
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new ConflictException("Đơn hàng không ở trạng thái chờ thanh toán");
        }

        Payment payment = paymentRepository.findByOrder_Id(orderId).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new ConflictException("Đơn hàng đã được thanh toán");
        }

        String effectiveReturnUrl = StringUtils.hasText(customReturnUrl) ? customReturnUrl : returnUrl;
        String txnRef = orderId + "-" + System.currentTimeMillis();
        String orderInfo = "Thanh toan don hang: " + order.getOrderNo();
        long amount = order.getTotalAmount().setScale(0, RoundingMode.HALF_UP).longValueExact() * 100;

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String createDate = now.format(formatter);
        String expireDate = now.plusMinutes(15).format(formatter);

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

        // Query string và chuỗi hash dùng chung 1 công thức (key=value sắp xếp theo key, value url-encode),
        // nên chữ ký tính trên đúng chuỗi sẽ được gửi đi, không cần build hai lần.
        String query = hashAllFields(fields);

        String secureHash;
        try {
            secureHash = hmacSHA512(hashSecret, query);
        } catch (Exception e) {
            log.error(">>>> Co loi khi hash VNPay: " + e);
            throw new BadRequestException("Không thể tạo chữ ký thanh toán");
        }

        String paymentUrl = payUrl + "?" + query + "&vnp_SecureHash=" + secureHash;

        if (payment == null) {
            payment = Payment.builder()
                    .order(order)
                    .amount(order.getTotalAmount())
                    .build();
        }
        payment.setMethod("VNPAY");
        payment.setVnpTxnRef(txnRef);
        payment.setVnpCreateDate(createDate);
        payment.setVnpTransactionNo(null);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setPaidAt(null);
        paymentRepository.save(payment);

        return CreateVnPayResponse.builder()
                .txnRef(txnRef)
                .amount(order.getTotalAmount())
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

        Payment payment = paymentRepository.findByVnpTxnRef(params.get("vnp_TxnRef")).orElse(null);
        if (payment == null) {
            log.warn(">>>> IPN cho vnp_TxnRef={} khong khop giao dich nao, bo qua", params.get("vnp_TxnRef"));
            return new VnPayIpnResult("01", "Order not found");
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info(">>>> IPN cho vnp_TxnRef={} da duoc xu ly truoc do, bo qua", params.get("vnp_TxnRef"));
            return new VnPayIpnResult("02", "Order already confirmed");
        }

        long expectedAmount = payment.getAmount().setScale(0, RoundingMode.HALF_UP).longValueExact() * 100;
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

        applyResult(payment, params.get("vnp_ResponseCode"), params.get("vnp_TransactionNo"));

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
            syncWithVnPay(payment);
        }

        return toStatusResponse(payment);
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

        Payment payment = paymentRepository.findByVnpTxnRef(params.get("vnp_TxnRef"))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy giao dịch tương ứng"));

        // Trạng thái hiển thị luôn lấy từ DB (do IPN cập nhật), không tin tham số trên URL redirect
        // vì đây đi qua browser, có thể bị sửa. Nếu IPN chưa kịp tới thì hỏi lại VNPay 1 lần.
        if (payment.getStatus() == PaymentStatus.PENDING) {
            syncWithVnPay(payment);
        }

        return toStatusResponse(payment);
    }

    private void syncWithVnPay(Payment payment) {

        String requestId = UUID.randomUUID().toString();
        String createDate = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String orderInfo = "Truy van giao dich " + payment.getVnpTxnRef();

        // querydr dùng công thức hash khác (nối chuỗi theo thứ tự cố định bằng "|"), không dùng hashAllFields
        String hashData = String.join("|",
                requestId, version, "querydr", tmnCode, payment.getVnpTxnRef(),
                payment.getVnpCreateDate(), createDate, "127.0.0.1", orderInfo);

        String signature;
        try {
            signature = hmacSHA512(hashSecret, hashData);
        } catch (Exception e) {
            log.error(">>>> Co loi khi tao chu ky querydr VNPay: " + e);
            return;
        }

        QueryVnPayRequest request = QueryVnPayRequest.builder()
                .requestId(requestId)
                .version(version)
                .command("querydr")
                .tmnCode(tmnCode)
                .txnRef(payment.getVnpTxnRef())
                .orderInfo(orderInfo)
                .transactionDate(payment.getVnpCreateDate())
                .createDate(createDate)
                .ipAddr("127.0.0.1")
                .secureHash(signature)
                .build();

        QueryVnPayResponse response;
        try {
            response = vnPayApi.queryTransaction(request);
        } catch (Exception e) {
            log.error(">>>> Khong the truy van trang thai giao dich VNPay: " + e);
            return;
        }

        if (response == null) {
            return;
        }

        log.info(">>>> Query response tu VNPay: txnRef={}, responseCode={}, transactionStatus={}, transactionNo={}, message={}",
                response.getTxnRef(), response.getResponseCode(), response.getTransactionStatus(),
                response.getTransactionNo(), response.getMessage());

        // vnp_ResponseCode là mã của chính request truy vấn (00 = truy vấn thành công), khác với
        // vnp_TransactionStatus là trạng thái thật của giao dịch
        if (!"00".equals(response.getResponseCode())) {
            log.warn(">>>> Truy van VNPay loi cho txnRef={}: {}", payment.getVnpTxnRef(), response.getMessage());
            return;
        }

        // "01" = giao dịch đang chờ xử lý bên VNPay, giữ nguyên PENDING chờ IPN hoặc lần poll kế tiếp
        if ("01".equals(response.getTransactionStatus())) {
            return;
        }

        applyResult(payment, response.getTransactionStatus(), response.getTransactionNo());
    }

    private void applyResult(Payment payment, String resultCode, String transactionNo) {
        if ("00".equals(resultCode)) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setVnpTransactionNo(transactionNo);
            payment.setPaidAt(Instant.now());

            Order order = payment.getOrder();
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }

        paymentRepository.save(payment);
    }

    private static PaymentStatusResponse toStatusResponse(Payment payment) {
        return PaymentStatusResponse.builder()
                .orderId(payment.getOrder().getId())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .vnpTransactionNo(payment.getVnpTransactionNo())
                .paidAt(payment.getPaidAt())
                .build();
    }

    // Sắp xếp field theo tên, bỏ field rỗng, nối "key=value" (value url-encode) bằng "&".
    // Đây chính là chuỗi dùng để tính vnp_SecureHash, và cũng là query string gửi lên VNPay.
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
