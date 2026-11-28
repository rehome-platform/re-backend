package com.rehome.rehome.entity.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.rehome.rehome.entity.enums.LegalChangeRequestStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "organization_legal_change_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationLegalChangeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationProfile organization;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "proposed_changes", nullable = false, columnDefinition = "jsonb")
    private JsonNode proposedChanges;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private LegalChangeRequestStatus status = LegalChangeRequestStatus.PENDING;

    @Column(name = "reject_reason", nullable = true, columnDefinition = "text")
    private String rejectReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "reviewed_by", nullable = true)
    private Account reviewedBy;

    @Column(name = "reviewed_at", nullable = true)
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
