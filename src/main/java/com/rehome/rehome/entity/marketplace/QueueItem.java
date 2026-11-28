package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.QueueType;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;

@Entity
@Table(
        name = "queue_items",
        indexes = {@Index(name = "idx_queue_type_claimed", columnList = "type, claimed_by")},
        uniqueConstraints = {@UniqueConstraint(name = "uk_queue_type_ref", columnNames = {"type", "ref_id"})})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueueItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private QueueType type;

    @Column(name = "ref_id", nullable = false)
    private String refId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_by", nullable = true)
    private Account claimedBy;

    @Column(name = "claimed_at", nullable = true)
    private Instant claimedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id", nullable = true)
    private Warehouse warehouse;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }
}