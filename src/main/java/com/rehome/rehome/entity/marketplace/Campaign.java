package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.CampaignStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "campaigns")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Campaign {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Tổ chức tạo chiến dịch
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationProfile organization;

    // Mô tả chiến dịch cho người quyên góp đọc
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    // Thời điểm bắt đầu nhận đồ
    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    // Thời điểm hết nhận; đến hạn hệ thống tự đóng chiến dịch
    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    // Tên địa điểm nhận đồ
    @Column(name = "location_name", nullable = false, length = 255)
    private String locationName;

    // Vĩ độ, để ghim lên bản đồ
    @Column(name = "latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal latitude;

    // Kinh độ, để ghim lên bản đồ
    @Column(name = "longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal longitude;

    // Loại đồ tổ chức ưu tiên nhận
    @Column(name = "preferred_item_types", nullable = true, length = 255)
    private String preferredItemTypes;

    // OPEN / CLOSED
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'OPEN'")
    @Builder.Default
    private CampaignStatus status = CampaignStatus.OPEN;

    // Thời điểm tạo chiến dịch
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
