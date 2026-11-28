package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.DegradedItemChoice;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "degraded_item_cases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DegradedItemCase {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "return_request_id", nullable = true)
    private ReturnRequest returnRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "choice", nullable = true)
    private DegradedItemChoice choice;

    @Column(name = "compensation_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal compensationAmount;

    @Column(name = "paid_amount", nullable = true, precision = 19, scale = 2)
    private BigDecimal paidAmount;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "moderator_id", nullable = false)
    private Account moderator;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at", nullable = true)
    private Instant decidedAt;

    @Column(name = "notes", nullable = true, columnDefinition = "text")
    private String notes;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "degraded_item_case_photos", joinColumns = @JoinColumn(name = "degraded_item_case_id"))
    @Column(name = "image_url", nullable = false, length = 2048)
    @OrderColumn(name = "position")
    @Builder.Default
    private List<String> photos = new ArrayList<>();

    @Column(name = "picked_up_at", nullable = true)
    private Instant pickedUpAt;

    @Column(name = "decision_deadline", nullable = true)
    private Instant decisionDeadline;

    @Column(name = "last_reminder_sent_at", nullable = true)
    private Instant lastReminderSentAt;

    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("false")
    private boolean itemDonated;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
    @Version
    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private long version;
}
