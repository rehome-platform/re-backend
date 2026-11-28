package com.rehome.rehome.entity.auth;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "organization_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private OrganizationProfile organization;

    @Column(name = "doc_type", nullable = false, length = 255)
    private String docType;

    @Column(name = "doc_number", nullable = true, length = 255)
    private String docNumber;

    @Column(name = "issue_date", nullable = true)
    private LocalDate issueDate;

    @Column(name = "issuing_authority", nullable = true, length = 255)
    private String issuingAuthority;

    @Column(name = "file_url", nullable = true, length = 2048)
    private String fileUrl;

    @Column(name = "accepted", nullable = true)
    private Boolean accepted;
}
