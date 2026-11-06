package com.rehome.rehome.serviceImpl;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.DeviceToken;
import com.rehome.rehome.repository.DeviceTokenRepository;
import com.rehome.rehome.service.PushNotificationService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Push qua Firebase Cloud Messaging. Chỉ có mặt khi {@code app.push.enabled=true}
 * (xem {@link com.rehome.rehome.config.FirebaseConfig}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "true")
public class FcmPushNotificationServiceImpl implements PushNotificationService {

    private static final int FCM_MULTICAST_LIMIT = 500;

    private final FirebaseMessaging firebaseMessaging;
    private final DeviceTokenRepository deviceTokenRepository;

    @Override
    @Transactional
    public void send(Account recipient, String title, String body, Map<String, String> data) {
        List<DeviceToken> deviceTokens = deviceTokenRepository.findByAccount_Id(recipient.getId());
        if (deviceTokens.isEmpty()) {
            return;
        }

        List<String> tokens = deviceTokens.stream().map(DeviceToken::getToken).toList();
        // FCM giới hạn 500 token/lần gửi multicast; số thiết bị của 1 account thực tế rất nhỏ nên
        // chunk đơn giản bằng sublist, không cần hàng đợi.
        for (int from = 0; from < tokens.size(); from += FCM_MULTICAST_LIMIT) {
            List<String> chunk = tokens.subList(from, Math.min(from + FCM_MULTICAST_LIMIT, tokens.size()));
            sendChunk(chunk, title, body, data);
        }
    }

    private void sendChunk(List<String> tokens, String title, String body, Map<String, String> data) {
        MulticastMessage.Builder messageBuilder = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build());
        if (data != null) {
            messageBuilder.putAllData(data);
        }

        try {
            BatchResponse response = firebaseMessaging.sendEachForMulticast(messageBuilder.build());
            cleanUpInvalidTokens(tokens, response);
        } catch (FirebaseMessagingException e) {
            log.warn("Gửi push FCM thất bại: {}", e.getMessage());
        }
    }

    // Token hết hạn/gỡ app/đăng xuất Firebase (UNREGISTERED) hoặc sai định dạng (INVALID_ARGUMENT)
    // thì xoá khỏi DB ngay - gửi lại lần sau vẫn lỗi, chỉ tốn quota và trễ response.
    private void cleanUpInvalidTokens(List<String> tokens, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        List<String> invalidTokens = new ArrayList<>();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) {
                continue;
            }
            MessagingErrorCode errorCode = sendResponse.getException() == null
                    ? null : sendResponse.getException().getMessagingErrorCode();
            if (errorCode == MessagingErrorCode.UNREGISTERED || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                invalidTokens.add(tokens.get(i));
            }
        }
        if (!invalidTokens.isEmpty()) {
            deviceTokenRepository.deleteByTokenIn(invalidTokens);
        }
    }
}
