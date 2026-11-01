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

/**
 * Hồ sơ tổ chức của chính người đang đăng nhập.
 * Chưa nộp hồ sơ -> mọi field đều null (verificationStatus = null).
 * ALWAYS để field null vẫn xuất hiện trong JSON, kể cả khi project cấu hình NON_NULL toàn cục.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class OrganizationProfileResponse {

    private VerificationStatus verificationStatus;
    private String rejectionReason;
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

    // Dữ liệu để app điền sẵn form nộp lại khi hồ sơ bị từ chối
    private Instant submittedAt;
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
    }

    public static OrganizationProfileResponse from(OrganizationProfile p) {
        return OrganizationProfileResponse.builder()
                .verificationStatus(p.getVerificationStatus())
                .rejectionReason(p.getRejectionReason())
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
                .representative(p.getRepresentative() == null ? null : RepresentativeInfo.builder()
                        .fullName(p.getRepresentative().getFullName())
                        .position(p.getRepresentative().getPosition())
                        .cccdNumber(p.getRepresentative().getCccdNumber())
                        // F-139: đường dẫn endpoint có kiểm quyền, không phải URL storage
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
                                .build())
                        .toList())
                .build();
    }

    /** Tổ chức chưa nộp hồ sơ: trả về object rỗng, verificationStatus = null. */
    public static OrganizationProfileResponse notSubmitted() {
        return OrganizationProfileResponse.builder().build();
    }
}