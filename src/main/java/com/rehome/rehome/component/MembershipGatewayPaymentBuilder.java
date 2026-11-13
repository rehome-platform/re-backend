package com.rehome.rehome.component;

import com.rehome.rehome.client.MomoApi;
import com.rehome.rehome.dto.request.CreateMomoRequest;
import com.rehome.rehome.dto.response.CreateMomoResponse;
import com.rehome.rehome.entity.membership.MembershipPayment;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.util.GatewaySignature;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Mở giao dịch MoMo / VNPay cho một lần mua gói thành viên.
 *
 * <p><b>Vì sao là component riêng chứ không gọi {@code MomoService}/{@code VnPayService}?</b> Hai
 * service đó phải gọi ngược về {@code MembershipService} khi IPN về (route theo tiền tố), nên
 * {@code MembershipServiceImpl} gọi xuôi vào chúng sẽ thành phụ thuộc vòng và Spring từ chối tạo
 * bean. Component này chỉ phụ thuộc vào {@code MomoApi} (Feign client) nên không có vòng nào.
 *
 * <h2>Tiền tố mã giao dịch - đây là chỗ route IPN</h2>
 * Cổng thanh toán chỉ trả về đúng cái mã mình gửi lên, nên mã phải tự nói nó là giao dịch gì:
 * <ul>
 *   <li>Đơn hàng: {@code <orderId>-<millis>} (hành vi cũ, giữ nguyên)</li>
 *   <li>Mua gói: {@code SUB-<subscriptionId không dấu gạch>-<millis>}</li>
 * </ul>
 * ⚠️ {@code orderId} của MoMo giới hạn <b>50 ký tự</b>. UUID có gạch là 36, cộng "-millis" (14) đã
 * vừa đúng 50 - nên bản mua gói <b>phải</b> bỏ gạch khỏi UUID: {@code 4 + 32 + 14 = 50}. Đừng đổi
 * sang UUID có gạch, MoMo sẽ từ chối. VNPay cho {@code vnp_TxnRef} tới 100 ký tự nên thoải mái,
 * nhưng dùng cùng một dạng để {@link #isMembershipRef} chỉ cần kiểm tra một tiền tố.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipGatewayPaymentBuilder {

    /** Tiền tố nhận dạng giao dịch mua gói ở cả hai cổng. */
    public static final String MEMBERSHIP_REF_PREFIX = "SUB-";

    private static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    // Link thanh toán gói sống 15 phút: đủ để mở app cổng và trả tiền, và nếu bỏ giữa đường thì
    // subscription PENDING_PAYMENT không treo quá lâu chặn lần mua sau (MEMBERSHIP_PAYMENT_PENDING).
    private static final int PAY_LINK_MINUTES = 15;

    private final MomoApi momoApi;

    @Value("${momo.partner-code}")
    private String momoPartnerCode;

    @Value("${momo.access-key}")
    private String momoAccessKey;

    @Value("${momo.secret-key}")
    private String momoSecretKey;

    @Value("${momo.return-url}")
    private String momoReturnUrl;

    @Value("${momo.ipn-url}")
    private String momoIpnUrl;

    @Value("${momo.request-type}")
    private String momoRequestType;

    @Value("${vnpay.tmn-code}")
    private String vnpTmnCode;

    @Value("${vnpay.hash-secret}")
    private String vnpHashSecret;

    @Value("${vnpay.pay-url}")
    private String vnpPayUrl;

    @Value("${vnpay.return-url}")
    private String vnpReturnUrl;

    @Value("${vnpay.version}")
    private String vnpVersion;

    public static boolean isMembershipRef(String gatewayRef) {
        return gatewayRef != null && gatewayRef.startsWith(MEMBERSHIP_REF_PREFIX);
    }

    public static String buildRef(UUID subscriptionId) {
        return MEMBERSHIP_REF_PREFIX
                + subscriptionId.toString().replace("-", "")
                + "-" + System.currentTimeMillis();
    }

    /**
     * Gọi MoMo tạo giao dịch và ghi các field đối soát vào {@code payment} (chưa lưu - nơi gọi
     * đang trong transaction của mình).
     *
     * @return payUrl để mobile mở WebView
     */
    public String createMomoPayment(MembershipPayment payment, String customRedirectUrl) {
        String momoOrderId = buildRef(payment.getSubscription().getId());
        String requestId = UUID.randomUUID().toString();
        String redirect = StringUtils.hasText(customRedirectUrl) ? customRedirectUrl : momoReturnUrl;
        String orderInfo = "Mua goi thanh vien ReHome";
        // extraData để trống: mọi thứ cần biết đã nằm trong momoOrderId, và extraData tham gia vào
        // chuỗi chữ ký nên thêm dữ liệu vào đây chỉ làm tăng chỗ sai.
        String extraData = "";
        long amount = toGatewayAmount(payment.getAmount());

        String rawSignature = String.format(
                "accessKey=%s&amount=%s&extraData=%s&ipnUrl=%s&orderId=%s&orderInfo=%s&partnerCode=%s"
                        + "&redirectUrl=%s&requestId=%s&requestType=%s",
                momoAccessKey, amount, extraData, momoIpnUrl, momoOrderId, orderInfo, momoPartnerCode,
                redirect, requestId, momoRequestType);
        String signature = GatewaySignature.hmacSHA256(momoSecretKey, rawSignature);

        CreateMomoResponse response = momoApi.createMomoQR(CreateMomoRequest.builder()
                .partnerCode(momoPartnerCode)
                .requestType(momoRequestType)
                .ipnUrl(momoIpnUrl)
                .redirectUrl(redirect)
                .orderId(momoOrderId)
                .orderInfo(orderInfo)
                .requestId(requestId)
                .extraData(extraData)
                .amount(amount)
                .signature(signature)
                .lang("vi")
                .build());

        if (response == null || response.getResultCode() != 0) {
            String message = response != null ? response.getMessage() : "Không nhận được phản hồi từ MoMo";
            throw new BadRequestException("Tạo giao dịch MoMo thất bại: " + message);
        }

        payment.setMomoOrderId(momoOrderId);
        payment.setMomoRequestId(requestId);
        return response.getPayUrl();
    }

    /**
     * Dựng URL thanh toán VNPay. VNPay không có bước gọi API tạo giao dịch - chỉ ký query string
     * rồi redirect, nên không có nhánh lỗi mạng ở đây.
     */
    public String createVnPayPayment(MembershipPayment payment, String customReturnUrl, String clientIp) {
        String txnRef = buildRef(payment.getSubscription().getId());
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        String createDate = now.format(VNPAY_TIME);

        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_Version", vnpVersion);
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", vnpTmnCode);
        // VNPay nhận số tiền đã nhân 100
        fields.put("vnp_Amount", String.valueOf(toGatewayAmount(payment.getAmount()) * 100));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", txnRef);
        fields.put("vnp_OrderInfo", "Mua goi thanh vien ReHome");
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", StringUtils.hasText(customReturnUrl) ? customReturnUrl : vnpReturnUrl);
        fields.put("vnp_IpAddr", StringUtils.hasText(clientIp) ? clientIp : "127.0.0.1");
        fields.put("vnp_CreateDate", createDate);
        fields.put("vnp_ExpireDate", now.plusMinutes(PAY_LINK_MINUTES).format(VNPAY_TIME));

        String query = GatewaySignature.hashAllFields(fields);
        String secureHash = GatewaySignature.hmacSHA512(vnpHashSecret, query);

        payment.setVnpTxnRef(txnRef);
        payment.setVnpCreateDate(createDate);
        return vnpPayUrl + "?" + query + "&vnp_SecureHash=" + secureHash;
    }

    /** Hai cổng đều nhận số nguyên đồng, không nhận phần thập phân. */
    private static long toGatewayAmount(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
