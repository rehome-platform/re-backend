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
    // Khóa chính của chi nhánh kho
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Tên kho, ví dụ Kho Quận 7
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // Địa chỉ kho, in trên biên nhận ký gửi
    @Column(name = "address", nullable = false, length = 255)
    private String address;

    // Số điện thoại kho - dùng làm to_phone khi tạo vận đơn trả hàng về kho (F-131)
    @Column(name = "phone", nullable = true, length = 255)
    private String phone;

    // Vĩ độ, dùng gợi ý kho gần nhất — F-015
    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    // Kinh độ, dùng gợi ý kho gần nhất — F-015
    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    // Giờ mở cửa, Member xem trước khi mang đồ đến
    @Column(name = "opening_hours", nullable = false, length = 255)
    private String openingHours;

    // ACTIVE / HIDDEN — HIDDEN thì không chọn được kho này nữa
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'ACTIVE'")
    @Builder.Default
    private CatalogStatus status = CatalogStatus.ACTIVE;

    // Thời điểm tạo kho
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }

    private String code;

    // Phường/xã kho - dùng làm from_ward_name khi tạo vận đơn GHN
    @Column(name = "ward_name", nullable = true, length = 255)
    private String wardName;

    // Quận/huyện kho - dùng làm from_district_name khi tạo vận đơn GHN
    @Column(name = "district_name", nullable = true, length = 255)
    private String districtName;

    // Tỉnh/thành kho - dùng làm from_province_name khi tạo vận đơn GHN
    @Column(name = "province_name", nullable = true, length = 255)
    private String provinceName;

    // true = theo đơn vị hành chính mới (sau 01/07/2025, không còn quận/huyện) khi gửi cho GHN
    @Column(name = "is_new_address", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean isNewAddress = false;
}
