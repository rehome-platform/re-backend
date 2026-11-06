package com.rehome.rehome.entity.auth;

import com.rehome.rehome.entity.enums.DevicePlatform;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

/**
 * FCM registration token của 1 thiết bị đã đăng nhập - dùng để push notification (F-092/F-093/F-094)
 * khi app đã tắt/background, lúc WebSocket không còn kết nối.
 *
 * <p>{@code token} unique toàn hệ thống, không theo account: Firebase cấp token theo thiết bị+app
 * install, không theo người dùng, nên nếu thiết bị đó đăng nhập account khác thì cùng 1 token phải
 * được gán lại cho account mới (xem {@code DeviceTokenServiceImpl.register}), nếu không sẽ báo push
 * nhầm người vẫn đang dùng thiết bị đó.
 */
@Entity
@Table(name = "device_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "token", nullable = false, unique = true, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
