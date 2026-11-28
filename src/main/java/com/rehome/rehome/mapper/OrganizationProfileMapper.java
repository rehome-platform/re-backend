package com.rehome.rehome.mapper;

import com.rehome.rehome.dto.request.organization.DocumentRequest;
import com.rehome.rehome.dto.request.organization.DonationPointRequest;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.request.organization.RepresentativeRequest;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.OrganizationDocument;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.auth.OrganizationRepresentative;
import com.rehome.rehome.util.LegalFileLinks;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class OrganizationProfileMapper {

    public OrganizationProfile toEntity(RegisterOrganizationRequest request) {
        OrganizationProfile org = new OrganizationProfile();
        applyFields(request, org);
        org.setRepresentative(toRepresentative(request.getRepresentative()));
        org.setDocuments(buildDocuments(request.getDocuments(), org));
        return org;
    }

    public void updateEntity(RegisterOrganizationRequest request, OrganizationProfile profile) {
        applyFields(request, profile);

        OrganizationRepresentative existingRepresentative = profile.getRepresentative();
        OrganizationRepresentative newRepresentative = toRepresentative(request.getRepresentative());
        keepEchoedRepresentativeFiles(profile.getAccountId(), existingRepresentative, newRepresentative);
        profile.setRepresentative(newRepresentative);

        replaceDocuments(request.getDocuments(), profile);
    }

    private void keepEchoedRepresentativeFiles(UUID organizationId, OrganizationRepresentative existing,
                                               OrganizationRepresentative updated) {
        String existingFrontUrl = existing == null ? null : existing.getCccdFrontUrl();
        String existingBackUrl = existing == null ? null : existing.getCccdBackUrl();
        if (LegalFileLinks.isCccdFrontLink(organizationId, updated.getCccdFrontUrl())) {
            updated.setCccdFrontUrl(existingFrontUrl);
        }
        if (LegalFileLinks.isCccdBackLink(organizationId, updated.getCccdBackUrl())) {
            updated.setCccdBackUrl(existingBackUrl);
        }
    }

    public RegisterOrganizationResponse toResponse(OrganizationProfile org) {
        RegisterOrganizationResponse res = new RegisterOrganizationResponse();
        res.setAccountId(org.getAccount().getId());
        res.setVerificationStatus(org.getVerificationStatus());
        return res;
    }

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

        DonationPointRequest dp = request.getDonationPoint();
        org.setDonationPointName(dp.getName());
        org.setDonationPointAddress(dp.getAddress());
        org.setDonationPointLatitude(dp.getLatitude());
        org.setDonationPointLongitude(dp.getLongitude());
        org.setDonationPointReceivingHours(dp.getReceivingHours());
        org.setDonationPointContactPhone(blankToNull(dp.getContactPhone()));
    }

    private List<OrganizationDocument> buildDocuments(List<DocumentRequest> requests, OrganizationProfile org) {
        return buildDocuments(requests, org, Map.of());
    }

    private List<OrganizationDocument> buildDocuments(List<DocumentRequest> requests, OrganizationProfile org,
                                                       Map<UUID, String> existingFileUrlById) {
        List<OrganizationDocument> documents = new ArrayList<>();
        for (DocumentRequest d : requests) {
            OrganizationDocument doc = new OrganizationDocument();
            doc.setDocType(d.getDocType());
            doc.setDocNumber(d.getDocNumber());
            doc.setIssueDate(d.getIssueDate());
            doc.setIssuingAuthority(d.getIssuingAuthority());
            doc.setFileUrl(resolveEchoedDocumentFileUrl(org.getAccountId(), d.getFileUrl(), existingFileUrlById));
            doc.setOrganization(org);
            documents.add(doc);
        }
        return documents;
    }

    private void replaceDocuments(List<DocumentRequest> requests, OrganizationProfile org) {
        List<OrganizationDocument> current = org.getDocuments();
        if (current == null) {
            org.setDocuments(buildDocuments(requests, org));
            return;
        }

        Map<UUID, String> existingFileUrlById = current.stream()
                .collect(Collectors.toMap(OrganizationDocument::getId, OrganizationDocument::getFileUrl));

        current.clear();
        current.addAll(buildDocuments(requests, org, existingFileUrlById));
    }

    private String resolveEchoedDocumentFileUrl(UUID organizationId, String requestedFileUrl,
                                                Map<UUID, String> existingFileUrlById) {
        UUID echoedDocumentId = LegalFileLinks.documentIdFromLinkOrNull(organizationId, requestedFileUrl);
        if (echoedDocumentId == null) {
            return requestedFileUrl;
        }
        return existingFileUrlById.getOrDefault(echoedDocumentId, requestedFileUrl);
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