package com.rehome.rehome.entity.chat;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.NotificationTargetType;
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

    // ORDER_UPDATE / WALLET_EVENT / TERM_EXPIRY / VERIFICATION_RESULT / QUEUE_TASK / DONATION_RECORDED
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    // Tiêu đề ngắn hiện ở đầu thông báo
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    // Nội dung chi tiết
    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    // Loại màn đích để app biết mở đâu khi bấm vào thông báo; null = không có deep-link
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = true)
    private NotificationTargetType targetType;

    // Khóa đa hình giống targetType, không đặt FK vì tuỳ loại trỏ bảng khác nhau (giống ViolationReport)
    @Column(name = "target_id", nullable = true)
    private UUID targetId;

    // DONATION_RECORDED: kho đã ghi nhận lượt quyên góp, để app mở đúng màn chờ lấy của kho đó (OC-07).
    // Tách riêng khỏi targetId vì không phải một phần khóa đa hình (campaignId mới là target)
    @Column(name = "warehouse_id", nullable = true)
    private UUID warehouseId;

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
