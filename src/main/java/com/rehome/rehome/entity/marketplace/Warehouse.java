package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.enums.CatalogStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "warehouses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Warehouse {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "phone", nullable = true, length = 255)
    private String phone;

    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "opening_hours", nullable = false, length = 255)
    private String openingHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'ACTIVE'")
    @Builder.Default
    private CatalogStatus status = CatalogStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }

    private String code;

    @Column(name = "ward_name", nullable = true, length = 255)
    private String wardName;

    @Column(name = "district_name", nullable = true, length = 255)
    private String districtName;

    @Column(name = "province_name", nullable = true, length = 255)
    private String provinceName;

    @Column(name = "province_id", nullable = true)
    private Integer provinceId;

    @Column(name = "ward_code", nullable = true, length = 50)
    private String wardCode;

    @Column(name = "is_new_address", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean isNewAddress = false;
}