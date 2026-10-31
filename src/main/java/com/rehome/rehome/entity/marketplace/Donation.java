package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.DonationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "donations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Donation {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Điểm ký gửi nhận đồ
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    // Tên người quyên góp; null khi quyên góp ẩn danh
    @Column(name = "donor_name", nullable = true, length = 255)
    private String donorName;

    // Người quyên góp chọn ẩn danh
    @Column(name = "is_anonymous", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean isAnonymous = false;

    // Ghi chú tự do của Moderator, ví dụ "2 áo khoác, 1 chăn" — không ghi số kg
    @Column(name = "note", nullable = true, columnDefinition = "text")
    private String note;

    // PENDING_PICKUP — chờ tổ chức đến lấy; HANDED_OVER — đã bàn giao
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING_PICKUP'")
    @Builder.Default
    private DonationStatus status = DonationStatus.PENDING_PICKUP;

    // Tổ chức đã đến nhận; null khi còn chờ lấy
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "handed_over_to_organization_id", nullable = true)
    private OrganizationProfile handedOverToOrganization;

    // Thời điểm bàn giao; null khi còn chờ lấy
    @Column(name = "handed_over_at", nullable = true)
    private Instant handedOverAt;

    // Thời điểm ghi nhận
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
