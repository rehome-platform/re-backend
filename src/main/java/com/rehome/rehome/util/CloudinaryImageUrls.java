package com.rehome.rehome.util;

import com.rehome.rehome.exception.BadRequestException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.springframework.util.StringUtils;

/**
 * Kiểm tra imageUrls client gửi lên chỉ được chứa ảnh do chính Cloudinary của hệ thống
 * phân phối, không nhận link ngoài — tránh client tự gắn URL ảnh bất kỳ (SSRF/nội dung
 * không kiểm soát) vào tin nhắn/đánh giá. Dùng chung cho chat (Message.imageUrls) và
 * đánh giá đơn hàng (Order.ratingImageUrls).
 */
public final class CloudinaryImageUrls {

    private static final String CLOUDINARY_DELIVERY_HOST = "res.cloudinary.com";

    private CloudinaryImageUrls() {
    }

    public static boolean isOwnCloudinaryUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        try {
            URI uri = new URI(url);
            return "https".equals(uri.getScheme()) && CLOUDINARY_DELIVERY_HOST.equals(uri.getHost());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** Throws BadRequestException(message) nếu có url không phải Cloudinary của hệ thống. */
    public static void requireOwnUrls(List<String> imageUrls, String message) {
        for (String url : imageUrls) {
            if (!isOwnCloudinaryUrl(url)) {
                throw new BadRequestException(message);
            }
        }
    }
}
