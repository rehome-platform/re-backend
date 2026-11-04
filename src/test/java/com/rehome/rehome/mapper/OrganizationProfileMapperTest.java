package com.rehome.rehome.mapper;

import com.rehome.rehome.dto.request.organization.DocumentRequest;
import com.rehome.rehome.dto.request.organization.DonationPointRequest;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.request.organization.RepresentativeRequest;
import com.rehome.rehome.entity.auth.OrganizationDocument;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.auth.OrganizationRepresentative;
import com.rehome.rehome.util.LegalFileLinks;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F-003: /organizations/me trả cccdFrontUrl/cccdBackUrl/documents[].fileUrl như link bảo vệ
 * (/api/organizations/{id}/legal-files/...), không phải URL storage. Nếu mobile không đổi ảnh
 * thì gửi lại nguyên văn link đó khi nộp lại hồ sơ - BE phải giữ nguyên tệp thật đang lưu
 * thay vì lưu thẳng chuỗi link đó làm fileUrl mới.
 */
class OrganizationProfileMapperTest {

    private final OrganizationProfileMapper mapper = new OrganizationProfileMapper();

    private DonationPointRequest donationPoint() {
        DonationPointRequest dp = new DonationPointRequest();
        dp.setName("Điểm nhận Q1");
        dp.setAddress("2 Nguyễn Huệ, Q1, TP.HCM");
        dp.setLatitude(new BigDecimal("10.7769000"));
        dp.setLongitude(new BigDecimal("106.7009000"));
        dp.setReceivingHours("8:00-17:00 T2-T6");
        return dp;
    }

    private RepresentativeRequest representative(String cccdFrontUrl, String cccdBackUrl) {
        RepresentativeRequest r = new RepresentativeRequest();
        r.setFullName("Nguyễn Văn B");
        r.setCccdNumber("079123456789");
        r.setCccdFrontUrl(cccdFrontUrl);
        r.setCccdBackUrl(cccdBackUrl);
        return r;
    }

    private DocumentRequest document(String docType, String fileUrl) {
        DocumentRequest d = new DocumentRequest();
        d.setDocType(docType);
        d.setFileUrl(fileUrl);
        return d;
    }

    private RegisterOrganizationRequest resubmitRequest(RepresentativeRequest representative,
                                                         List<DocumentRequest> documents) {
        RegisterOrganizationRequest req = new RegisterOrganizationRequest();
        req.setLegalName("Hội Chia Sẻ");
        req.setDisplayName("Chia Sẻ");
        req.setOrgType("NGO");
        req.setOperationScope("PROVINCE");
        req.setAddress("1 Lê Lợi, Q1, TP.HCM");
        req.setContactPhone("0900000000");
        req.setContactEmail("org@mail.com");
        req.setDescription("Tổ chức thiện nguyện");
        req.setRepresentative(representative);
        req.setDocuments(documents);
        req.setDonationPoint(donationPoint());
        return req;
    }

    @Test
    void updateEntity_echoedCccdLinks_keepsStoredFiles() {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = OrganizationProfile.builder().accountId(orgId).build();
        profile.setRepresentative(new OrganizationRepresentative(
                "Nguyễn Văn B", null, "079123456789",
                "https://cdn/cccd-front-real.jpg", "https://cdn/cccd-back-real.jpg"));
        profile.setDocuments(new java.util.ArrayList<>());

        RegisterOrganizationRequest request = resubmitRequest(
                representative(LegalFileLinks.cccdFront(orgId), LegalFileLinks.cccdBack(orgId)),
                List.of(document("FUND_LICENSE", "https://cdn/new-licence.pdf")));

        mapper.updateEntity(request, profile);

        // Mobile gửi lại đúng link bảo vệ -> giữ nguyên URL storage thật đang lưu
        assertThat(profile.getRepresentative().getCccdFrontUrl()).isEqualTo("https://cdn/cccd-front-real.jpg");
        assertThat(profile.getRepresentative().getCccdBackUrl()).isEqualTo("https://cdn/cccd-back-real.jpg");
    }

    @Test
    void updateEntity_newCccdUpload_overwritesStoredFiles() {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = OrganizationProfile.builder().accountId(orgId).build();
        profile.setRepresentative(new OrganizationRepresentative(
                "Nguyễn Văn B", null, "079123456789",
                "https://cdn/cccd-front-old.jpg", "https://cdn/cccd-back-old.jpg"));
        profile.setDocuments(new java.util.ArrayList<>());

        RegisterOrganizationRequest request = resubmitRequest(
                representative("https://cdn/cccd-front-new.jpg", "https://cdn/cccd-back-new.jpg"),
                List.of(document("FUND_LICENSE", "https://cdn/new-licence.pdf")));

        mapper.updateEntity(request, profile);

        // Ảnh mới thật (không phải link bảo vệ) thì phải ghi đè như bình thường
        assertThat(profile.getRepresentative().getCccdFrontUrl()).isEqualTo("https://cdn/cccd-front-new.jpg");
        assertThat(profile.getRepresentative().getCccdBackUrl()).isEqualTo("https://cdn/cccd-back-new.jpg");
    }

    @Test
    void updateEntity_echoedDocumentLink_keepsStoredFileForThatDocument() {
        UUID orgId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        OrganizationProfile profile = OrganizationProfile.builder().accountId(orgId).build();
        profile.setRepresentative(new OrganizationRepresentative(
                "Nguyễn Văn B", null, "079123456789",
                "https://cdn/cccd-front.jpg", "https://cdn/cccd-back.jpg"));
        OrganizationDocument existingDoc = OrganizationDocument.builder()
                .id(documentId).docType("FUND_LICENSE").fileUrl("https://cdn/licence-real.pdf").build();
        existingDoc.setOrganization(profile);
        profile.setDocuments(new java.util.ArrayList<>(List.of(existingDoc)));

        RegisterOrganizationRequest request = resubmitRequest(
                representative(LegalFileLinks.cccdFront(orgId), LegalFileLinks.cccdBack(orgId)),
                List.of(document("FUND_LICENSE", LegalFileLinks.document(orgId, documentId))));

        mapper.updateEntity(request, profile);

        assertThat(profile.getDocuments()).hasSize(1);
        assertThat(profile.getDocuments().get(0).getFileUrl()).isEqualTo("https://cdn/licence-real.pdf");
    }

    @Test
    void updateEntity_newDocumentUpload_storesNewFileUrl() {
        UUID orgId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        OrganizationProfile profile = OrganizationProfile.builder().accountId(orgId).build();
        profile.setRepresentative(new OrganizationRepresentative(
                "Nguyễn Văn B", null, "079123456789",
                "https://cdn/cccd-front.jpg", "https://cdn/cccd-back.jpg"));
        OrganizationDocument existingDoc = OrganizationDocument.builder()
                .id(documentId).docType("FUND_LICENSE").fileUrl("https://cdn/licence-old.pdf").build();
        existingDoc.setOrganization(profile);
        profile.setDocuments(new java.util.ArrayList<>(List.of(existingDoc)));

        RegisterOrganizationRequest request = resubmitRequest(
                representative(LegalFileLinks.cccdFront(orgId), LegalFileLinks.cccdBack(orgId)),
                List.of(document("FUND_LICENSE", "https://cdn/licence-new.pdf")));

        mapper.updateEntity(request, profile);

        assertThat(profile.getDocuments().get(0).getFileUrl()).isEqualTo("https://cdn/licence-new.pdf");
    }

    @Test
    void updateEntity_documentLinkFromAnotherOrganization_isNotTreatedAsEchoed() {
        UUID orgId = UUID.randomUUID();
        UUID otherOrgId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        OrganizationProfile profile = OrganizationProfile.builder().accountId(orgId).build();
        profile.setRepresentative(new OrganizationRepresentative(
                "Nguyễn Văn B", null, "079123456789",
                "https://cdn/cccd-front.jpg", "https://cdn/cccd-back.jpg"));
        OrganizationDocument existingDoc = OrganizationDocument.builder()
                .id(documentId).docType("FUND_LICENSE").fileUrl("https://cdn/licence-real.pdf").build();
        existingDoc.setOrganization(profile);
        profile.setDocuments(new java.util.ArrayList<>(List.of(existingDoc)));

        // Link có đúng format nhưng thuộc tổ chức khác -> không được khớp nhầm
        String foreignLink = LegalFileLinks.document(otherOrgId, documentId);
        RegisterOrganizationRequest request = resubmitRequest(
                representative(LegalFileLinks.cccdFront(orgId), LegalFileLinks.cccdBack(orgId)),
                List.of(document("FUND_LICENSE", foreignLink)));

        mapper.updateEntity(request, profile);

        // Không khớp link của chính mình -> coi như URL thật, lưu nguyên văn (dù vô nghĩa,
        // đây không phải trường hợp mobile tạo ra - chỉ cần không bị gán nhầm tệp của org khác)
        assertThat(profile.getDocuments().get(0).getFileUrl()).isEqualTo(foreignLink);
    }
}
