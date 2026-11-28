package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.ProductCondition;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "consignment_forms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsignmentForm {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Account member;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "warehouse_id", nullable = true)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "brand_id", nullable = false)
    private Brand brand;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "desired_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal desiredPrice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private ConsignmentFormStatus status = ConsignmentFormStatus.PENDING;

    @Column(name = "reject_reason", nullable = true, columnDefinition = "text")
    private String rejectReason;

    @Column(name = "bring_in_deadline", nullable = true)
    private LocalDate bringInDeadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "form_no", unique = true, length = 30)
    private String formNo;

    @Column(name = "size", length = 50)
    private String size;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_condition", length = 30)
    private ProductCondition condition;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    private void onUpdate() {
        updatedAt = Instant.now();
    }
}
