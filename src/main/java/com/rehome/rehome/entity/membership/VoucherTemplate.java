package com.rehome.rehome.entity.membership;

import com.rehome.rehome.entity.enums.VoucherDiscountKind;
import com.rehome.rehome.entity.enums.VoucherType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "voucher_templates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoucherTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 255)
    private VoucherType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_kind", nullable = false, length = 255)
    private VoucherDiscountKind discountKind;

    @Column(name = "discount_value", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountValue;

    @Column(name = "max_discount_amount", nullable = true, precision = 19, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "min_order_amount", nullable = false, precision = 19, scale = 2)
    @ColumnDefault("0")
    @Builder.Default
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @Column(name = "valid_days", nullable = false)
    private Integer validDays;

    @Column(name = "active", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private Boolean active = true;

    @OneToMany(mappedBy = "voucherTemplate", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<VoucherTemplateTierRule> tierRules = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

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
