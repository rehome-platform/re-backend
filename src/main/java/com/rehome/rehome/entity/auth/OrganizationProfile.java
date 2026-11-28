package com.rehome.rehome.entity.auth;

import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.util.VietnameseText;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "organization_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationProfile {
    @Id
    @Column(name = "account_id", nullable = false)
    UUID accountId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "legal_name", nullable = false, length = 255)
    private String legalName;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "display_name_normalized", nullable = true, length = 255)
    private String displayNameNormalized;

    @Column(name = "legal_name_normalized", nullable = true, length = 255)
    private String legalNameNormalized;

    @Column(name = "org_type", nullable = false, length = 255)
    private String orgType;

    @Column(name = "tax_code", nullable = true, length = 255)
    private String taxCode;

    @Column(name = "operation_scope", nullable = false, length = 255)
    private String operationScope;

    @Column(name = "address", nullable = false, length = 255)
    private String address;

    @Column(name = "contact_phone", nullable = false, length = 255)
    private String contactPhone;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "website", nullable = true, length = 2048)
    private String website;

    @Column(name = "description", nullable = false, columnDefinition = "text")
    private String description;

    @Column(name = "logo_url", nullable = true, length = 2048)
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false)
    @ColumnDefault("'PENDING'")
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    @Column(name = "rejection_reason", nullable = true, columnDefinition = "text")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "verified_by", nullable = true)
    private Account verifiedBy;

    @Column(name = "verified_at", nullable = true)
    private Instant verifiedAt;

    @Column(name = "rejected_at", nullable = true)
    private Instant rejectedAt;

    @Column(name = "submitted_at", nullable = true)
    private Instant submittedAt;

    @Column(name = "donation_point_name", nullable = false, length = 255)
    private String donationPointName;

    @Column(name = "donation_point_address", nullable = false, length = 255)
    private String donationPointAddress;

    @Column(name = "donation_point_latitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal donationPointLatitude;

    @Column(name = "donation_point_longitude", nullable = false, precision = 10, scale = 7)
    private BigDecimal donationPointLongitude;

    @Column(name = "donation_point_receiving_hours", nullable = false, length = 255)
    private String donationPointReceivingHours;

    @Column(name = "donation_point_contact_phone", nullable = true, length = 255)
    private String donationPointContactPhone;

    @Embedded
    private OrganizationRepresentative representative;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrganizationDocument> documents = new ArrayList<>();

    @PrePersist
    @PreUpdate
    private void normalizeSearchFields() {
        displayNameNormalized = VietnameseText.normalize(displayName);
        legalNameNormalized = VietnameseText.normalize(legalName);
    }
}
