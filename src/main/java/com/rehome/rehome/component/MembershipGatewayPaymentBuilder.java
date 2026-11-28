package com.rehome.rehome.component;

import com.rehome.rehome.entity.membership.MembershipPayment;
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

@Component
@RequiredArgsConstructor
@Slf4j
public class MembershipGatewayPaymentBuilder {

    public static final String MEMBERSHIP_REF_PREFIX = "SUB-";

    private static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public static final int PAY_LINK_MINUTES = 15;

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

    public String createVnPayPayment(MembershipPayment payment, String customReturnUrl, String clientIp) {
        String txnRef = buildRef(payment.getSubscription().getId());
        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        String createDate = now.format(VNPAY_TIME);

        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_Version", vnpVersion);
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", vnpTmnCode);
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

    private static long toGatewayAmount(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
