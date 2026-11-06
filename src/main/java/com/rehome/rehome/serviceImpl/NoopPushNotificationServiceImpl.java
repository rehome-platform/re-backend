package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.chat.Notification;
import com.rehome.rehome.service.PushNotificationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Bean mặc định khi {@code app.push.enabled} không phải "true" (dev/CI không có service account
 * Firebase thật) - xem {@link com.rehome.rehome.config.FirebaseConfig}.
 */
@Service
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "false", matchIfMissing = true)
public class NoopPushNotificationServiceImpl implements PushNotificationService {

    @Override
    public void send(Account recipient, Notification notification) {
        // Không làm gì - WebSocket (nếu đang kết nối) là đường báo real-time duy nhất lúc này.
    }
}
