package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 1 thông báo trong ứng dụng (F-092/F-093/F-094). message lấy từ Notification.body.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private String id;
    private String type;
    private String title;
    private String message;

    @JsonProperty("isRead") // Jackson mặc định bỏ tiền tố "is" của getter boolean -> phải ép tên field tường minh
    private boolean isRead;

    // Deep-link để app mở đúng màn hình; null khi thông báo không có đích cụ thể
    private String targetType;
    private String targetId;

    // DONATION_RECORDED: kho đã ghi nhận lượt quyên góp (OC-07)
    private String warehouseId;

    private Instant createdAt;
}
