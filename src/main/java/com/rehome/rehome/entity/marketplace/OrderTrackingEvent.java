package com.rehome.rehome.entity.marketplace;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;

// Lịch sử trạng thái vận chuyển GHN, ghi mỗi lần webhook bắn về - F-128
@Entity
@Table(name = "order_tracking_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderTrackingEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Trạng thái thô từ GHN, ví dụ "delivering", "delivered"
    @Column(name = "ghn_status", nullable = false, length = 255)
    private String ghnStatus;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    // Thời điểm GHN ghi nhận sự kiện - field Time trong webhook
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
