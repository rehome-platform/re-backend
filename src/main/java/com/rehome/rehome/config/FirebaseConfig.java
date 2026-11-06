package com.rehome.rehome.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Push notification (F-092/F-093/F-094) qua Firebase Cloud Messaging khi app đã tắt/background -
 * lúc đó WebSocket (xem {@link WebSocketConfig}) không còn kết nối để nhận.
 *
 * <p>Chỉ khởi tạo khi {@code app.push.enabled=true}, để dev/CI không cần service account thật vẫn
 * chạy được (khớp cách {@link CloudinaryConfig} làm cho storage) - lúc đó
 * {@code NoopPushNotificationServiceImpl} nhận việc, không gọi Firebase.
 *
 * <p>Service account key KHÔNG commit vào repo (secret). Lấy chuỗi base64 của file JSON tải từ
 * Firebase Console > Project Settings > Service accounts > Generate new private key, set vào env
 * var {@code FIREBASE_CREDENTIALS_BASE64}.
 */
@Configuration
@ConditionalOnProperty(name = "app.push.enabled", havingValue = "true")
public class FirebaseConfig {

    @Value("${firebase.credentials-base64:}")
    private String credentialsBase64;

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @PostConstruct
    public void init() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }
        try (InputStream credentialsStream = openCredentials()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(credentialsStream))
                    .build();
            FirebaseApp.initializeApp(options);
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        return FirebaseMessaging.getInstance(FirebaseApp.getInstance());
    }

    private InputStream openCredentials() throws IOException {
        if (StringUtils.hasText(credentialsBase64)) {
            return new ByteArrayInputStream(Base64.getDecoder().decode(credentialsBase64.trim()));
        }
        if (StringUtils.hasText(credentialsPath)) {
            return Files.newInputStream(Path.of(credentialsPath));
        }
        throw new IllegalStateException(
                "app.push.enabled=true nhưng thiếu firebase.credentials-base64 hoặc firebase.credentials-path.");
    }
}
