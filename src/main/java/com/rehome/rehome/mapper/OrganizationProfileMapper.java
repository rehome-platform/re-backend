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

    public OrganizationProfile toEntity(RegisterOrganizationRequest request) {
        OrganizationProfile org = new OrganizationProfile();
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

        // Người đại diện (embedded)
        org.setRepresentative(toRepresentative(request.getRepresentative()));

        // Giấy tờ, gán quan hệ 2 chiều document -> organization
        List<OrganizationDocument> documents = new ArrayList<>();
        for (DocumentRequest d : request.getDocuments()) {
            OrganizationDocument doc = new OrganizationDocument();
            doc.setDocType(d.getDocType());
            doc.setDocNumber(d.getDocNumber());
            doc.setIssueDate(d.getIssueDate());
            doc.setIssuingAuthority(d.getIssuingAuthority());
            doc.setFileUrl(d.getFileUrl());
            doc.setOrganization(org);
            documents.add(doc);
        }
        org.setDocuments(documents);

        return org;
    }

    public RegisterOrganizationResponse toResponse(OrganizationProfile org) {
        RegisterOrganizationResponse res = new RegisterOrganizationResponse();
        res.setAccountId(org.getAccount().getId());
        res.setVerificationStatus(org.getVerificationStatus());
        return res;
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