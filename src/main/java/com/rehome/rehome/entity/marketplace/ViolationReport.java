package com.rehome.rehome.entity.marketplace;

import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.enums.ViolationReportStatus;
import com.rehome.rehome.entity.enums.ViolationTargetType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "violation_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ViolationReport {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Member gửi báo cáo
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_member_id", nullable = false)
    private Account reporterMember;

    // POST / CAMPAIGN / ORGANIZATION_ACCOUNT — quyết định target_id trỏ bảng nào
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ViolationTargetType targetType;

    // Khóa đa hình: id của CommunityPost, Campaign hoặc Account bị báo — không đặt FK được vì trỏ ba bảng
    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    // Lý do báo cáo
    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    // PENDING / RESOLVED / DISMISSED
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private ViolationReportStatus status = ViolationReportStatus.PENDING;

    // Moderator xử lý báo cáo
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "resolved_by", nullable = true)
    private Account resolvedBy;

    // Hành động đã làm, ví dụ gỡ bài, ẩn chiến dịch
    @Column(name = "resolution_action", nullable = true, length = 255)
    private String resolutionAction;

    // Thời điểm gửi báo cáo
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Thời điểm xử lý xong
    @Column(name = "resolved_at", nullable = true)
    private Instant resolvedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
