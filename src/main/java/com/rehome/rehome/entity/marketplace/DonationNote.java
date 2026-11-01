package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.auth.OrganizationProfile;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

// F-167: tổ chức nhận quyên góp trực tiếp tại địa chỉ riêng, không qua điểm ký gửi nên
// không có warehouse và cũng không có trạng thái chờ lấy / bàn giao như Donation (F-163)
@Entity
@Table(name = "donation_notes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DonationNote {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Tổ chức tự ghi note này
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationProfile organization;

    // Chiến dịch nhận đồ; null khi tổ chức nhận ngoài chiến dịch nào
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "campaign_id", nullable = true)
    private Campaign campaign;

    // Tên người quyên góp; null khi quyên góp ẩn danh
    @Column(name = "donor_name", nullable = true, length = 255)
    private String donorName;

    // Người quyên góp chọn ẩn danh
    @Column(name = "is_anonymous", nullable = false)
    @ColumnDefault("false")
    @Builder.Default
    private Boolean isAnonymous = false;

    // Ghi chú tự do, ví dụ "1 túi quần áo trẻ em" — không ghi số kg
    @Column(name = "note", nullable = true, columnDefinition = "text")
    private String note;

    // Thời điểm ghi note
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
