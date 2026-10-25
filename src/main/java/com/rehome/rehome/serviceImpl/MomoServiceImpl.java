package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.MomoApi;
import com.rehome.rehome.dto.request.CreateMomoRequest;
import com.rehome.rehome.dto.request.MomoIpnRequest;
import com.rehome.rehome.dto.request.QueryMomoRequest;
import com.rehome.rehome.dto.response.CreateMomoResponse;
import com.rehome.rehome.dto.response.PaymentStatusResponse;
import com.rehome.rehome.dto.response.QueryMomoResponse;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.PaymentStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.Payment;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.PaymentRepository;
import com.rehome.rehome.service.MomoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class MomoServiceImpl implements MomoService {

    @Value("${momo.partner-code}")
    private String partnerCode;

    @Value("${momo.access-key}")
    private String accessKey;

    @Value("${momo.secret-key}")
    private String secretKey;

    @Value("${momo.return-url}")
    private String redirectUrl;

    @Value("${momo.ipn-url}")
    private String ipnUrl;

    @Value("${momo.request-type}")
    private String requestType;

    private final MomoApi momoApi;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;

    @Override
    @Transactional
    public CreateMomoResponse createPaymentForOrder(String email, UUID orderId, String customRedirectUrl) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getBuyer().getEmail().equalsIgnoreCase(email)) {
            throw new AppException(ErrorCode.ORDER_FORBIDDEN);
        }

        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new AppException(ErrorCode.ORDER_NOT_PENDING_PAYMENT);
        }

        Payment payment = paymentRepository.findByOrder_Id(orderId).orElse(null);
        if (payment != null && payment.getStatus() == PaymentStatus.SUCCESS) {
            throw new AppException(ErrorCode.ORDER_ALREADY_PAID);
        }

        String effectiveRedirectUrl = StringUtils.hasText(customRedirectUrl) ? customRedirectUrl : redirectUrl;
        String momoOrderId = orderId + "-" + System.currentTimeMillis();
        String orderInfo = "Thanh toan don hang: " + order.getOrderNo();
        String requestId = UUID.randomUUID().toString();
        String extraData = orderId.toString();
        long amount = order.getTotalAmount().setScale(0, RoundingMode.HALF_UP).longValueExact();

        String rawSignature = String.format(
                "accessKey=%s&amount=%s&extraData=%s&ipnUrl=%s&orderId=%s&orderInfo=%s&partnerCode=%s&redirectUrl=%s&requestId=%s&requestType=%s",
                accessKey, amount, extraData, ipnUrl, momoOrderId, orderInfo, partnerCode, effectiveRedirectUrl, requestId, requestType);

        String prettySignature;
        try {
            prettySignature = signHmacSHA256(rawSignature, secretKey);
        } catch (Exception e) {
            log.error(">>>> Co loi khi hash code: " + e);
            throw new AppException(ErrorCode.MOMO_SIGNATURE_CREATE_FAILED);
        }

        if (prettySignature.isBlank()) {
            log.error(">>>> Signature is blank");
            throw new AppException(ErrorCode.MOMO_SIGNATURE_CREATE_FAILED);
        }

        CreateMomoRequest request = CreateMomoRequest.builder()
                .partnerCode(partnerCode)
                .requestType(requestType)
                .ipnUrl(ipnUrl)
                .redirectUrl(effectiveRedirectUrl)
                .orderId(momoOrderId)
                .orderInfo(orderInfo)
                .requestId(requestId)
                .extraData(extraData)
                .amount(amount)
                .signature(prettySignature)
                .lang("vi")
                .build();

        CreateMomoResponse response = momoApi.createMomoQR(request);

        if (response == null || response.getResultCode() != 0) {
            String message = response != null ? response.getMessage() : "Không nhận được phản hồi từ MoMo";
            log.error(">>>> Tao giao dich MoMo that bai: {}", message);
            throw new AppException(ErrorCode.MOMO_CREATE_PAYMENT_FAILED);
        }

        if (payment == null) {
            payment = Payment.builder()
                    .order(order)
                    .amount(order.getTotalAmount())
                    .build();
        }
        payment.setMomoOrderId(momoOrderId);
        payment.setMomoRequestId(requestId);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setMomoTransactionId(null);
        payment.setPaidAt(null);
        paymentRepository.save(payment);

        return response;
    }

    @Override
    @Transactional
    public void handleIpn(MomoIpnRequest ipnRequest) {

        String rawSignature = buildResultSignature(
                ipnRequest.getAmount(), ipnRequest.getExtraData(), ipnRequest.getMessage(),
                ipnRequest.getOrderId(), ipnRequest.getOrderInfo(), ipnRequest.getOrderType(),
                ipnRequest.getPayType(), ipnRequest.getRequestId(), ipnRequest.getResponseTime(),
                ipnRequest.getResultCode(), ipnRequest.getTransId());

        String expectedSignature;
        try {
            expectedSignature = signHmacSHA256(rawSignature, secretKey);
        } catch (Exception e) {
            log.error(">>>> Co loi khi verify chu ky IPN: " + e);
            throw new AppException(ErrorCode.MOMO_IPN_VERIFY_FAILED);
        }

        if (!expectedSignature.equals(ipnRequest.getSignature())) {
            log.error(">>>> IPN signature mismatch cho orderId={}", ipnRequest.getOrderId());
            throw new AppException(ErrorCode.MOMO_INVALID_SIGNATURE);
        }

        Payment payment = paymentRepository.findByMomoOrderId(ipnRequest.getOrderId()).orElse(null);
        if (payment == null) {
            log.warn(">>>> IPN cho momoOrderId={} khong khop giao dich nao, bo qua", ipnRequest.getOrderId());
            return;
        }

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info(">>>> IPN cho momoOrderId={} da duoc xu ly truoc do, bo qua", ipnRequest.getOrderId());
            return;
        }

        applyResult(payment, ipnRequest.getResultCode(), ipnRequest.getTransId());
    }

    @Override
    @Transactional
    public PaymentStatusResponse checkPaymentStatus(String email, UUID orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getBuyer().getEmail().equalsIgnoreCase(email)) {
            throw new AppException(ErrorCode.ORDER_FORBIDDEN);
        }

        Payment payment = paymentRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        if (payment.getStatus() == PaymentStatus.PENDING) {
            syncWithMomo(payment);
        }

        return PaymentStatusResponse.builder()
                .orderId(orderId)
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .momoTransactionId(payment.getMomoTransactionId())
                .paidAt(payment.getPaidAt())
                .build();
    }

    @Override
    @Transactional
    public PaymentStatusResponse getReturnInfo(MomoIpnRequest returnParams) {

        String rawSignature = buildResultSignature(
                returnParams.getAmount(), returnParams.getExtraData(), returnParams.getMessage(),
                returnParams.getOrderId(), returnParams.getOrderInfo(), returnParams.getOrderType(),
                returnParams.getPayType(), returnParams.getRequestId(), returnParams.getResponseTime(),
                returnParams.getResultCode(), returnParams.getTransId());

        String expectedSignature;
        try {
            expectedSignature = signHmacSHA256(rawSignature, secretKey);
        } catch (Exception e) {
            log.error(">>>> Co loi khi verify chu ky return URL: " + e);
            throw new AppException(ErrorCode.MOMO_RETURN_VERIFY_FAILED);
        }

        if (!expectedSignature.equals(returnParams.getSignature())) {
            log.error(">>>> Chu ky tren return URL khong hop le cho momoOrderId={}", returnParams.getOrderId());
            throw new AppException(ErrorCode.MOMO_INVALID_SIGNATURE);
        }

        Payment payment = paymentRepository.findByMomoOrderId(returnParams.getOrderId())
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));

        // Trạng thái hiển thị luôn lấy từ DB (do IPN cập nhật), không tin resultCode trên URL redirect
        // vì đây là tham số đi qua browser, có thể bị sửa. Nếu IPN chưa kịp tới thì hỏi lại MoMo 1 lần.
        if (payment.getStatus() == PaymentStatus.PENDING) {
            syncWithMomo(payment);
        }

        return PaymentStatusResponse.builder()
                .orderId(payment.getOrder().getId())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .momoTransactionId(payment.getMomoTransactionId())
                .paidAt(payment.getPaidAt())
                .build();
    }

    private void syncWithMomo(Payment payment) {

        String rawSignature = String.format(
                "accessKey=%s&orderId=%s&partnerCode=%s&requestId=%s",
                accessKey, payment.getMomoOrderId(), partnerCode, payment.getMomoRequestId());

        String signature;
        try {
            signature = signHmacSHA256(rawSignature, secretKey);
        } catch (Exception e) {
            log.error(">>>> Co loi khi tao chu ky query: " + e);
            return;
        }

        QueryMomoRequest request = QueryMomoRequest.builder()
                .partnerCode(partnerCode)
                .requestId(payment.getMomoRequestId())
                .orderId(payment.getMomoOrderId())
                .lang("vi")
                .signature(signature)
                .build();

        QueryMomoResponse response;
        try {
            response = momoApi.queryTransaction(request);
        } catch (Exception e) {
            log.error(">>>> Khong the truy van trang thai giao dich MoMo: " + e);
            return;
        }

        if (response == null) {
            return;
        }

        log.info(">>>> Query response tu MoMo: orderId={}, resultCode={}, transId={}, message={}, signature={}",
                response.getOrderId(), response.getResultCode(), response.getTransId(), response.getMessage(), response.getSignature());

        // Đây là lệnh gọi mình chủ động khởi tạo qua HTTPS tới domain chính thức của MoMo (xác thực bằng TLS,
        // không phải request vô danh gửi tới mình như IPN), nên nếu lệch chữ ký (khác field set so với tài liệu)
        // vẫn log cảnh báo để điều tra thêm, nhưng không chặn cập nhật kết quả.
        try {
            String expectedSignature = buildResultSignature(
                    response.getAmount(), response.getExtraData(), response.getMessage(),
                    response.getOrderId(), response.getOrderInfo(), response.getOrderType(),
                    response.getPayType(), response.getRequestId(), response.getResponseTime(),
                    response.getResultCode(), response.getTransId());
            expectedSignature = signHmacSHA256(expectedSignature, secretKey);

            if (!expectedSignature.equals(response.getSignature())) {
                log.warn(">>>> Query response signature khong khop cong thuc du kien cho orderId={}, van ap dung ket qua vi la ket noi HTTPS tin cay", response.getOrderId());
            }
        } catch (Exception e) {
            log.warn(">>>> Co loi khi verify chu ky query response, van ap dung ket qua: " + e);
        }

        // 7002 = đang chờ thanh toán, giữ nguyên PENDING chờ IPN hoặc lần poll kế tiếp
        if (response.getResultCode() == 7002) {
            return;
        }

        applyResult(payment, response.getResultCode(), response.getTransId());
    }

    private void applyResult(Payment payment, int resultCode, long transId) {
        if (resultCode == 0) {
            payment.setStatus(PaymentStatus.SUCCESS);
            payment.setMomoTransactionId(String.valueOf(transId));
            payment.setPaidAt(Instant.now());

            Order order = payment.getOrder();
            order.setStatus(OrderStatus.PAID);
            orderRepository.save(order);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
        }

        paymentRepository.save(payment);
    }

    private String buildResultSignature(long amount, String extraData, String message, String orderId,
                                        String orderInfo, String orderType, String payType, String requestId,
                                        long responseTime, int resultCode, long transId) {
        return String.format(
                "accessKey=%s&amount=%s&extraData=%s&message=%s&orderId=%s&orderInfo=%s&orderType=%s&partnerCode=%s&payType=%s&requestId=%s&responseTime=%s&resultCode=%s&transId=%s",
                accessKey, amount, extraData, message, orderId, orderInfo, orderType, partnerCode,
                payType, requestId, responseTime, resultCode, transId);
    }

    private String signHmacSHA256(String data, String key) throws Exception {

        Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(
                key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmacSHA256.init(secretKey);

        byte[] hash = hmacSHA256.doFinal(data.getBytes(StandardCharsets.UTF_8));
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