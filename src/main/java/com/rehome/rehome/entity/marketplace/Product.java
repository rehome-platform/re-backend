package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.util.VietnameseText;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.springframework.data.domain.Persistable;

@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product implements Persistable<String> {
    @Id
    @Column(name = "id", nullable = false, length = 255)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "consignment_receipt_id", nullable = false, unique = true)
    private ConsignmentReceipt consignmentReceipt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "name_normalized", nullable = true, length = 255)
    private String nameNormalized;

    @Column(name = "size", nullable = true, length = 255)
    private String size;

    @Column(name = "condition_note", nullable = false, columnDefinition = "text")
    private String conditionNote;

    @Column(name = "current_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'ON_SALE'")
    @Builder.Default
    private ProductStatus status = ProductStatus.ON_SALE;

    @Column(name = "current_term", nullable = false)
    @ColumnDefault("1")
    @Builder.Default
    private Short currentTerm = (short) 1;

    @Column(name = "term_started_at", nullable = false)
    private Instant termStartedAt;

    @Column(name = "last_price_reduced_on", nullable = true)
    private LocalDate lastPriceReducedOn;

    @Column(name = "end_of_term_deadline", nullable = true)
    private Instant endOfTermDeadline;

    @Column(name = "unclaimed_decision_deadline", nullable = true)
    private Instant unclaimedDecisionDeadline;

    @Column(name = "featured_until", nullable = true)
    private Instant featuredUntil;

    @Column(name = "featured_rank")
    private Integer featuredRank;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_price_changed_at", nullable = true)
    private Instant lastPriceChangedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        nameNormalized = VietnameseText.normalize(name);
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
        nameNormalized = VietnameseText.normalize(name);
    }

    @Transient
    @Builder.Default
    private boolean isNew = true;

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.isNew = false;
    }
    @Version
    @Column(nullable = false)
    @org.hibernate.annotations.ColumnDefault("0")
    private long version;
}
