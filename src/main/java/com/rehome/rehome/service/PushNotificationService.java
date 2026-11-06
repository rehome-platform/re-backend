package com.rehome.rehome.service;

import com.rehome.rehome.entity.auth.Account;
import java.util.Map;

public interface PushNotificationService {

    // Gửi push tới mọi thiết bị đã đăng ký của recipient (F-092/F-093/F-094, và tin nhắn chat).
    // Không throw khi gửi thất bại (thiết bị offline/token hết hạn...) - việc chính (lưu DB, báo
    // qua WebSocket) đã xong trước khi gọi đây, push chỉ là thêm, lỗi push không được làm hỏng
    // luồng chính.
    void send(Account recipient, String title, String body, Map<String, String> data);
}
