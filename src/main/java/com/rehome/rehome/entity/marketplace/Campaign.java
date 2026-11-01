package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.CampaignStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
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

    // Tên chiến dịch (OC-02). Cột để nullable vì ddl-auto=update không backfill được
    // NOT NULL cho các chiến dịch đã tồn tại trước field này; bắt buộc ở tầng validate request.
    @Column(name = "title", nullable = true, length = 255)
    private String title;

    // Ảnh bìa chiến dịch, upload qua /uploads (UploadFolder.CAMPAIGN_COVERS); không bắt buộc
    @Column(name = "cover_url", nullable = true, length = 2048)
    private String coverUrl;

    // Mô tả chiến dịch cho người quyên góp đọc
    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    // Thời điểm bắt đầu nhận đồ
    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    // Thời điểm hết nhận; đến hạn hệ thống tự đóng chiến dịch
    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    // Cách nhận đồ (OC-02): phải chọn ít nhất 1 trong 2 - nhận tại địa chỉ riêng (địa chỉ
    // lấy từ OrganizationProfile.donationPoint*, không nhập tay nữa) và/hoặc chọn kho bên dưới.
    // Có thể chọn cả hai, nhiều kho, kho nào cũng được.
    @Column(name = "receive_at_own_address", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean receiveAtOwnAddress = false;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "campaign_warehouses",
            joinColumns = @JoinColumn(name = "campaign_id"),
            inverseJoinColumns = @JoinColumn(name = "warehouse_id"))
    @Builder.Default
    private Set<Warehouse> warehouses = new HashSet<>();

    // Tên địa điểm nhận đồ - không còn nhập tay (xem receiveAtOwnAddress/warehouses ở trên).
    // Cột giữ nullable để các chiến dịch tạo trước khi đổi sang warehouses vẫn đọc được.
    @Column(name = "location_name", nullable = true, length = 255)
    private String locationName;

    // Vĩ độ, để ghim lên bản đồ - không còn nhập tay, xem comment ở locationName
    @Column(name = "latitude", nullable = true, precision = 10, scale = 7)
    private BigDecimal latitude;

    // Kinh độ, để ghim lên bản đồ - không còn nhập tay, xem comment ở locationName
    @Column(name = "longitude", nullable = true, precision = 10, scale = 7)
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
