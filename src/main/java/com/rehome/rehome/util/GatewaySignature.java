package com.rehome.rehome.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.util.StringUtils;

/**
 * Chữ ký và query string của MoMo (HMAC-SHA256) và VNPay (HMAC-SHA512).
 *
 * <p>Công thức này trước đây bị copy ở {@code MomoServiceImpl}, {@code VnPayServiceImpl} và
 * {@code ItemWithdrawalRequestServiceImpl} (ba bản giống nhau, copy để tránh phụ thuộc vòng giữa
 * các service). Luồng mua gói thành viên là nơi thứ tư cần nó, nên tách ra đây thay vì copy tiếp.
 * Hai service cũ cố ý chưa đổi sang dùng util này - đổi chúng là refactor riêng, không gộp vào
 * tính năng gói VIP để diff còn đọc được.
 */
public final class GatewaySignature {

    private GatewaySignature() {}

    /**
     * Sắp xếp field theo tên, bỏ field rỗng, nối {@code key=value} (value url-encode) bằng
     * {@code &}. Đây vừa là chuỗi để tính {@code vnp_SecureHash}, vừa là query string gửi lên
     * VNPay - tính một lần dùng cả hai nên chữ ký luôn khớp chuỗi thật được gửi đi.
     */
    public static String hashAllFields(Map<String, String> fields) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);

        List<String> pairs = new ArrayList<>();
        for (String fieldName : fieldNames) {
            String fieldValue = fields.get(fieldName);
            if (StringUtils.hasText(fieldValue)) {
                pairs.add(fieldName + "=" + urlEncode(fieldValue));
            }
        }
        return String.join("&", pairs);
    }

    public static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.US_ASCII.toString());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hmacSHA256(String key, String data) {
        return hmac("HmacSHA256", key, data);
    }

    public static String hmacSHA512(String key, String data) {
        return hmac("HmacSHA512", key, data);
    }

    private static String hmac(String algorithm, String key, String data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm));
            return toHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            // Thuật toán và key đều cố định nên lỗi ở đây là lỗi cấu hình, không phải lỗi dữ liệu
            // người dùng - để IllegalStateException nổi lên thành 500 thay vì âm thầm trả chữ ký rỗng.
            throw new IllegalStateException("Không thể tạo chữ ký " + algorithm, e);
        }
    }

    private static String toHex(byte[] hash) {
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
