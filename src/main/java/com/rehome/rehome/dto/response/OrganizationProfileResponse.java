package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.util.LegalFileLinks;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class OrganizationProfileResponse {

    private VerificationStatus verificationStatus;
    private String rejectionReason;
    private Instant rejectedAt;
    private String displayName;
    private String logoUrl;

    private String legalName;
    private String orgType;
    private String taxCode;
    private String operationScope;
    private String address;
    private String contactPhone;
    private String contactEmail;
    private String website;
    private String description;

    private String donationPointName;
    private String donationPointAddress;
    private BigDecimal donationPointLatitude;
    private BigDecimal donationPointLongitude;
    private String donationPointReceivingHours;
    private String donationPointContactPhone;

    private Instant verifiedAt;

    private Instant submittedAt;

    private Integer maxConcurrentCampaigns;
    private Long runningCampaigns;

    private RepresentativeInfo representative;
    private List<DocumentInfo> documents;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class RepresentativeInfo {
        private String fullName;
        private String position;
        private String cccdNumber;
        private String cccdFrontUrl;
        private String cccdBackUrl;
    }

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static class DocumentInfo {
        private String docType;
        private String docNumber;
        private LocalDate issueDate;
        private String issuingAuthority;
        private String fileUrl;
        private Boolean accepted;
    }

    public static OrganizationProfileResponse from(OrganizationProfile p) {
        return from(p, null, null);
    }

    public static OrganizationProfileResponse from(OrganizationProfile p, Integer maxConcurrentCampaigns, Long runningCampaigns) {
        return OrganizationProfileResponse.builder()
                .verificationStatus(p.getVerificationStatus())
                .rejectionReason(p.getRejectionReason())
                .rejectedAt(p.getRejectedAt())
                .displayName(p.getDisplayName())
                .logoUrl(p.getLogoUrl())
                .legalName(p.getLegalName())
                .orgType(p.getOrgType())
                .taxCode(p.getTaxCode())
                .operationScope(p.getOperationScope())
                .address(p.getAddress())
                .contactPhone(p.getContactPhone())
                .contactEmail(p.getContactEmail())
                .website(p.getWebsite())
                .description(p.getDescription())
                .donationPointName(p.getDonationPointName())
                .donationPointAddress(p.getDonationPointAddress())
                .donationPointLatitude(p.getDonationPointLatitude())
                .donationPointLongitude(p.getDonationPointLongitude())
                .donationPointReceivingHours(p.getDonationPointReceivingHours())
                .donationPointContactPhone(p.getDonationPointContactPhone())
                .verifiedAt(p.getVerifiedAt())
                .submittedAt(p.getSubmittedAt())
                .maxConcurrentCampaigns(maxConcurrentCampaigns)
                .runningCampaigns(runningCampaigns)
                .representative(p.getRepresentative() == null ? null : RepresentativeInfo.builder()
                        .fullName(p.getRepresentative().getFullName())
                        .position(p.getRepresentative().getPosition())
                        .cccdNumber(p.getRepresentative().getCccdNumber())
                        .cccdFrontUrl(LegalFileLinks.cccdFrontOrNull(
                                p.getAccountId(), p.getRepresentative().getCccdFrontUrl()))
                        .cccdBackUrl(LegalFileLinks.cccdBackOrNull(
                                p.getAccountId(), p.getRepresentative().getCccdBackUrl()))
                        .build())
                .documents(p.getDocuments() == null ? List.of() : p.getDocuments().stream()
                        .map(d -> DocumentInfo.builder()
                                .docType(d.getDocType())
                                .docNumber(d.getDocNumber())
                                .issueDate(d.getIssueDate())
                                .issuingAuthority(d.getIssuingAuthority())
                                .fileUrl(LegalFileLinks.documentOrNull(
                                        p.getAccountId(), d.getId(), d.getFileUrl()))
                                .accepted(d.getAccepted())
                                .build())
                        .toList())
                .build();
    }

    public static OrganizationProfileResponse notSubmitted() {
        return OrganizationProfileResponse.builder().build();
    }
}