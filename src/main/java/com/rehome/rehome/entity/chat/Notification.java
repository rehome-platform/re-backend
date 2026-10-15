package com.rehome.rehome.entity.chat;

import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.enums.NotificationType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Người nhận thông báo
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_account_id", nullable = false)
    private Account recipientAccount;

    // ORDER_UPDATE / WALLET_EVENT / TERM_EXPIRY / VERIFICATION_RESULT / QUEUE_TASK
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    // Tiêu đề ngắn hiện ở đầu thông báo
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    // Nội dung chi tiết
    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    // Đã đọc chưa, dùng đếm badge
    @Column(name = "is_read", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean isRead = false;

    // Thời điểm gửi thông báo
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
