package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

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
                .build();
    }

    /** Tổ chức chưa nộp hồ sơ: trả về object rỗng, verificationStatus = null. */
    public static OrganizationProfileResponse notSubmitted() {
        return OrganizationProfileResponse.builder().build();
    }
}