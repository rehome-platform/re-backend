package com.rehome.rehome.service;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.chat.Notification;

public interface PushNotificationService {

    // Gửi push tới mọi thiết bị đã đăng ký của recipient (F-092/F-093/F-094). Không throw khi
    // gửi thất bại (thiết bị offline/token hết hạn...) - notification đã lưu DB và đã báo qua
    // WebSocket rồi, push chỉ là thêm, lỗi push không được làm hỏng luồng chính.
    void send(Account recipient, Notification notification);
}
