package com.rehome.rehome.entity.marketplace;


import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ViolationReportStatus;
import com.rehome.rehome.entity.enums.ViolationResolutionAction;
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
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_member_id", nullable = false)
    private Account reporterMember;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ViolationTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private ViolationReportStatus status = ViolationReportStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "resolved_by", nullable = true)
    private Account resolvedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_action", nullable = true, length = 255)
    private ViolationResolutionAction resolutionAction;

    @Column(name = "resolution_reason", nullable = true, columnDefinition = "text")
    private String resolutionReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at", nullable = true)
    private Instant resolvedAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
