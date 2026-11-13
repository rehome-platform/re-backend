package com.rehome.rehome.entity.membership;

import com.rehome.rehome.entity.enums.MembershipTier;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * Cấu hình của một hạng thành viên. Chỉ có đúng 4 dòng (FREE/SILVER/GOLD/DIAMOND), seed bằng
 * {@code MembershipPlanInitializer} giống {@code SystemConfigInitializer}.
 *
 * <p><b>Mọi con số ở đây là DỮ LIỆU, không phải hằng số.</b> Admin sửa được qua
 * {@code PUT /api/admin/membership-plans/{id}} - kể cả giá tháng, giá năm và từng hạn mức. Code
 * tuyệt đối không hard-code giá trị nào; các giá trị trong initializer chỉ là seed ban đầu.
 *
 * <p>Giá gói và hạn mức cố ý KHÔNG nằm trong {@code system_configs}: mỗi hạng có một bộ giá trị
 * riêng nên đây là bảng dữ liệu, không phải tham số toàn cục.
 */
@Entity
@Table(name = "membership_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // UNIQUE: một hạng chỉ có đúng một dòng cấu hình
    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, unique = true, length = 255)
    private MembershipTier tier;

    // Tên hiển thị trên app, ví dụ "Thành viên Vàng"
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", nullable = true, columnDefinition = "text")
    private String description;

    // FREE = 0
    @Column(name = "price_per_month", nullable = false, precision = 19, scale = 2)
    private BigDecimal pricePerMonth;

    // null = không bán gói năm
    @Column(name = "price_per_year", nullable = true, precision = 19, scale = 2)
    private BigDecimal pricePerYear;

    // Hạn mức CHÍNH của thử đồ real-time. null = không giới hạn (mobile hiện "Không giới hạn")
    @Column(name = "live_tryon_per_day", nullable = true)
    private Integer liveTryonPerDay;

    // Trần phụ theo tháng, seed = null (không chặn). Giữ sẵn cột để sau này siết thêm mà không
    // phải migrate DB - xem cảnh báo chi phí ở đặc tả §2.2.
    @Column(name = "live_tryon_per_month", nullable = true)
    private Integer liveTryonPerMonth;

    // Thời lượng tối đa mỗi phiên real-time, giây
    @Column(name = "live_tryon_max_seconds", nullable = false)
    private Integer liveTryonMaxSeconds;

    // Hạn mức CHÍNH của phối đồ AI (ảnh tĩnh). null = không giới hạn
    @Column(name = "ai_tryon_per_day", nullable = true)
    private Integer aiTryonPerDay;

    @Column(name = "ai_tryon_per_month", nullable = true)
    private Integer aiTryonPerMonth;

    // true = TOÀN BỘ hàng ký gửi của Member được ưu tiên hiển thị + nhãn "Nổi bật", tự động.
    // Không có khái niệm slot, Member không phải bấm gì.
    @Column(name = "featured_listing", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean featuredListing = false;

    // Số form ký gửi ĐANG CHỜ MODERATOR DUYỆT tối đa, không phải số sản phẩm lên sàn
    @Column(name = "max_pending_consignment_forms", nullable = false)
    private Integer maxPendingConsignmentForms;

    // FREE = false, 3 hạng còn lại = true
    @Column(name = "purchasable", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private Boolean purchasable = true;

    // false = ngừng bán mới, người đang dùng vẫn giữ tới hết hạn (không có DELETE)
    @Column(name = "active", nullable = false)
    @ColumnDefault("true")
    @Builder.Default
    private Boolean active = true;

    @Column(name = "sort_order", nullable = false)
    @ColumnDefault("0")
    @Builder.Default
    private Integer sortOrder = 0;

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
