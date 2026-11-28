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
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_recipient_created", columnList = "recipient_account_id, created_at"),
        @Index(name = "idx_notifications_recipient_read", columnList = "recipient_account_id, is_read")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recipient_account_id", nullable = false)
    private Account recipientAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = true)
    private NotificationTargetType targetType;

    @Column(name = "target_id", nullable = true)
    private String targetId;

    @Column(name = "warehouse_id", nullable = true)
    private UUID warehouseId;

    @Column(name = "is_read", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
