package com.rehome.rehome.mapper;

import com.rehome.rehome.dto.request.organization.DocumentRequest;
import com.rehome.rehome.dto.request.organization.DonationPointRequest;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.request.organization.RepresentativeRequest;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.OrganizationDocument;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.auth.OrganizationRepresentative;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class OrganizationProfileMapper {

    /**
     * API 3: tạo hồ sơ mới từ request.
     */
    public OrganizationProfile toEntity(RegisterOrganizationRequest request) {
        OrganizationProfile org = new OrganizationProfile();
        applyFields(request, org);
        org.setRepresentative(toRepresentative(request.getRepresentative()));
        org.setDocuments(buildDocuments(request.getDocuments(), org));
        return org;
    }

    /**
     * API 4: ghi đè dữ liệu request vào hồ sơ đã có (nộp lại sau khi bị từ chối).
     * Không đụng vào account và verificationStatus, service tự xử lý hai field này.
     */
    public void updateEntity(RegisterOrganizationRequest request, OrganizationProfile profile) {
        applyFields(request, profile);
        profile.setRepresentative(toRepresentative(request.getRepresentative()));
        replaceDocuments(request.getDocuments(), profile);
    }

    public RegisterOrganizationResponse toResponse(OrganizationProfile org) {
        RegisterOrganizationResponse res = new RegisterOrganizationResponse();
        res.setAccountId(org.getAccount().getId());
        res.setVerificationStatus(org.getVerificationStatus());
        return res;
    }

    // ------------------------------------------------------------------
    // Phần dùng chung cho toEntity và updateEntity
    // ------------------------------------------------------------------

    /** Gán các field đơn (kể cả điểm nhận đồ đã trải phẳng) từ request vào hồ sơ. */
    private void applyFields(RegisterOrganizationRequest request, OrganizationProfile org) {
        org.setLegalName(request.getLegalName().trim());
        org.setDisplayName(request.getDisplayName().trim());
        org.setOrgType(request.getOrgType().trim());
        org.setTaxCode(blankToNull(request.getTaxCode()));
        org.setOperationScope(request.getOperationScope());
        org.setAddress(request.getAddress());
        org.setContactPhone(request.getContactPhone());
        org.setContactEmail(request.getContactEmail().trim().toLowerCase());
        org.setWebsite(blankToNull(request.getWebsite()));
        org.setDescription(request.getDescription());
        org.setLogoUrl(blankToNull(request.getLogoUrl()));

        // Điểm nhận đồ: trải phẳng vào các cột donationPoint*
        DonationPointRequest dp = request.getDonationPoint();
        org.setDonationPointName(dp.getName());
        org.setDonationPointAddress(dp.getAddress());
        org.setDonationPointLatitude(dp.getLatitude());
        org.setDonationPointLongitude(dp.getLongitude());
        org.setDonationPointReceivingHours(dp.getReceivingHours());
        org.setDonationPointContactPhone(blankToNull(dp.getContactPhone()));
    }

    /** Tạo danh sách giấy tờ mới, gán quan hệ 2 chiều document -> organization. */
    private List<OrganizationDocument> buildDocuments(List<DocumentRequest> requests, OrganizationProfile org) {
        List<OrganizationDocument> documents = new ArrayList<>();
        for (DocumentRequest d : requests) {
            OrganizationDocument doc = new OrganizationDocument();
            doc.setDocType(d.getDocType());
            doc.setDocNumber(d.getDocNumber());
            doc.setIssueDate(d.getIssueDate());
            doc.setIssuingAuthority(d.getIssuingAuthority());
            doc.setFileUrl(d.getFileUrl());
            doc.setOrganization(org);
            documents.add(doc);
        }
        return documents;
    }

    /**
     * Thay toàn bộ giấy tờ cũ bằng giấy tờ mới.
     * Phải clear() + addAll() trên CHÍNH list đang có, không gọi setDocuments(newList),
     * vì Hibernate báo lỗi nếu list có orphanRemoval bị thay bằng đối tượng khác.
     */
    private void replaceDocuments(List<DocumentRequest> requests, OrganizationProfile org) {
        List<OrganizationDocument> current = org.getDocuments();
        if (current == null) {
            org.setDocuments(buildDocuments(requests, org));
            return;
        }
        current.clear();
        current.addAll(buildDocuments(requests, org));
    }

    private OrganizationRepresentative toRepresentative(RepresentativeRequest r) {
        OrganizationRepresentative rep = new OrganizationRepresentative();
        rep.setFullName(r.getFullName());
        rep.setPosition(r.getPosition());
        rep.setCccdNumber(r.getCccdNumber());
        rep.setCccdFrontUrl(r.getCccdFrontUrl());
        rep.setCccdBackUrl(r.getCccdBackUrl());
        return rep;
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}