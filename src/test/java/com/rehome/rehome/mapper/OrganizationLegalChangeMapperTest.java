package com.rehome.rehome.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.entity.auth.OrganizationDocument;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.auth.OrganizationRepresentative;
import com.rehome.rehome.entity.enums.OrganizationType;
import com.rehome.rehome.exception.BadRequestException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * F-140: proposedChanges là JSON tự do nên mapper là chỗ duy nhất quyết định trường nào
 * được đổi. Test khóa cả hai phía: áp đúng trường cho phép, và từ chối thứ không cho phép.
 */
class OrganizationLegalChangeMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final OrganizationLegalChangeMapper mapper = new OrganizationLegalChangeMapper();

    private JsonNode json(String raw) {
        try {
            return JSON.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private OrganizationProfile profile() {
        OrganizationProfile profile = OrganizationProfile.builder()
                .accountId(UUID.randomUUID())
                .legalName("Hội Chia Sẻ")
                .orgType(OrganizationType.CHARITY_FUND.name())
                .taxCode("0101234567")
                .representative(new OrganizationRepresentative(
                        "Nguyễn Văn A", "Giám đốc", "079123456789",
                        "https://cdn/front.jpg", "https://cdn/back.jpg"))
                .build();
        profile.setDocuments(new ArrayList<>(List.of(OrganizationDocument.builder()
                .id(UUID.randomUUID())
                .docType("FUND_LICENSE")
                .fileUrl("https://cdn/cu.pdf")
                .build())));
        return profile;
    }

    // ---------------------------------------------------------------- áp dụng

    @Test
    void apply_changesLegalName() {
        OrganizationProfile profile = profile();

        boolean imagesReplaced = mapper.apply(json("{\"legalName\":\"  Quỹ Chia Sẻ  \"}"), profile);

        assertThat(profile.getLegalName()).isEqualTo("Quỹ Chia Sẻ");
        // Không có ảnh mới nên không cần đặt lại mốc giữ ảnh của F-139
        assertThat(imagesReplaced).isFalse();
    }

    @Test
    void apply_changesOrgTypeAndTaxCode() {
        OrganizationProfile profile = profile();

        mapper.apply(json("{\"orgType\":\"NGO\",\"taxCode\":\"0309876543\"}"), profile);

        assertThat(profile.getOrgType()).isEqualTo("NGO");
        assertThat(profile.getTaxCode()).isEqualTo("0309876543");
    }

    @Test
    void apply_clearsTaxCode_whenBlank() {
        OrganizationProfile profile = profile();

        // Tổ chức không có mã số thuế vẫn được chấp nhận (OVERVIEW mục 9)
        mapper.apply(json("{\"taxCode\":\"\"}"), profile);

        assertThat(profile.getTaxCode()).isNull();
    }

    @Test
    void apply_patchesOnlyGivenRepresentativeFields() {
        OrganizationProfile profile = profile();

        boolean imagesReplaced = mapper.apply(
                json("{\"representative\":{\"fullName\":\"Trần Thị B\"}}"), profile);

        assertThat(profile.getRepresentative().getFullName()).isEqualTo("Trần Thị B");
        // Field không gửi thì giữ nguyên
        assertThat(profile.getRepresentative().getPosition()).isEqualTo("Giám đốc");
        assertThat(profile.getRepresentative().getCccdNumber()).isEqualTo("079123456789");
        assertThat(profile.getRepresentative().getCccdFrontUrl()).isEqualTo("https://cdn/front.jpg");
        assertThat(imagesReplaced).isFalse();
    }

    @Test
    void apply_reportsImagesReplaced_whenCccdImageChanges() {
        OrganizationProfile profile = profile();

        boolean imagesReplaced = mapper.apply(
                json("{\"representative\":{\"cccdFrontUrl\":\"https://cdn/moi.jpg\"}}"), profile);

        assertThat(profile.getRepresentative().getCccdFrontUrl()).isEqualTo("https://cdn/moi.jpg");
        assertThat(imagesReplaced).isTrue();
    }

    @Test
    void apply_createsRepresentative_whenProfileHasNone() {
        OrganizationProfile profile = OrganizationProfile.builder().accountId(UUID.randomUUID()).build();

        mapper.apply(json("{\"representative\":{\"fullName\":\"Trần Thị B\"}}"), profile);

        assertThat(profile.getRepresentative()).isNotNull();
        assertThat(profile.getRepresentative().getFullName()).isEqualTo("Trần Thị B");
    }

    @Test
    void apply_replacesWholeDocumentList() {
        OrganizationProfile profile = profile();
        List<OrganizationDocument> original = profile.getDocuments();

        boolean imagesReplaced = mapper.apply(json("""
                {"documents":[
                  {"docType":"FUND_LICENSE","docNumber":"142/QD-BNV","issueDate":"2026-01-15",
                   "issuingAuthority":"Bộ Nội vụ","fileUrl":"https://cdn/moi.pdf"},
                  {"docType":"CHARTER"}
                ]}"""), profile);

        assertThat(profile.getDocuments()).hasSize(2);
        // Phải sửa trên chính list cũ, không thay bằng list khác (orphanRemoval)
        assertThat(profile.getDocuments()).isSameAs(original);

        OrganizationDocument first = profile.getDocuments().get(0);
        assertThat(first.getDocType()).isEqualTo("FUND_LICENSE");
        assertThat(first.getDocNumber()).isEqualTo("142/QD-BNV");
        assertThat(first.getIssueDate()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(first.getIssuingAuthority()).isEqualTo("Bộ Nội vụ");
        assertThat(first.getFileUrl()).isEqualTo("https://cdn/moi.pdf");
        assertThat(first.getOrganization()).isSameAs(profile);

        // Quyết định nội bộ thường không có số, ngày cấp, cơ quan cấp
        OrganizationDocument second = profile.getDocuments().get(1);
        assertThat(second.getDocNumber()).isNull();
        assertThat(second.getIssueDate()).isNull();
        assertThat(second.getIssuingAuthority()).isNull();

        assertThat(imagesReplaced).isTrue();
    }

    @Test
    void apply_setsDocumentList_whenProfileHasNullList() {
        OrganizationProfile profile = OrganizationProfile.builder().accountId(UUID.randomUUID()).build();
        profile.setDocuments(null);

        mapper.apply(json("{\"documents\":[{\"docType\":\"CHARTER\"}]}"), profile);

        assertThat(profile.getDocuments()).hasSize(1);
    }

    @Test
    void apply_documentsWithoutFile_doesNotResetRetentionClock() {
        OrganizationProfile profile = profile();

        boolean imagesReplaced = mapper.apply(json("{\"documents\":[{\"docType\":\"CHARTER\"}]}"), profile);

        assertThat(imagesReplaced).isFalse();
    }

    @Test
    void apply_changesEverythingAtOnce() {
        OrganizationProfile profile = profile();

        boolean imagesReplaced = mapper.apply(json("""
                {"legalName":"Quỹ Chia Sẻ",
                 "representative":{"fullName":"Trần Thị B","cccdBackUrl":"https://cdn/sau-moi.jpg"},
                 "documents":[{"docType":"CHARTER","fileUrl":"https://cdn/dieu-le.pdf"}]}"""), profile);

        assertThat(profile.getLegalName()).isEqualTo("Quỹ Chia Sẻ");
        assertThat(profile.getRepresentative().getFullName()).isEqualTo("Trần Thị B");
        assertThat(profile.getDocuments()).hasSize(1);
        assertThat(imagesReplaced).isTrue();
    }

    // ---------------------------------------------------------------- từ chối

    @Test
    void apply_throwsBadRequest_whenFieldIsNotLegal() {
        OrganizationProfile profile = profile();

        // logoUrl/description là thông tin thường, tổ chức tự sửa qua API 117
        assertThatThrownBy(() -> mapper.apply(
                json("{\"legalName\":\"X\",\"logoUrl\":\"https://cdn/logo.png\"}"), profile))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("logoUrl");
        assertThat(profile.getLegalName()).isEqualTo("Hội Chia Sẻ");
    }

    @Test
    void apply_throwsBadRequest_whenFieldNameIsMisspelled() {
        // Đây là lý do không bỏ qua im lặng: duyệt xong mà hồ sơ không đổi gì
        assertThatThrownBy(() -> mapper.apply(json("{\"name\":\"Quỹ Chia Sẻ\"}"), profile()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("name");
    }

    @Test
    void apply_throwsBadRequest_whenNothingToApply() {
        assertThatThrownBy(() -> mapper.apply(json("{}"), profile()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("không có trường pháp lý nào");
    }

    @Test
    void apply_throwsBadRequest_whenRequiredTextIsBlankOrNull() {
        assertThatThrownBy(() -> mapper.apply(json("{\"legalName\":\"   \"}"), profile()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("legalName");
        assertThatThrownBy(() -> mapper.apply(json("{\"legalName\":null}"), profile()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("legalName");
    }

    @Test
    void apply_throwsBadRequest_whenOrgTypeIsUnknown() {
        assertThatThrownBy(() -> mapper.apply(json("{\"orgType\":\"CONG_TY\"}"), profile()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("orgType không hợp lệ");
    }

    @Test
    void apply_throwsBadRequest_whenRepresentativeIsNotObject() {
        assertThatThrownBy(() -> mapper.apply(json("{\"representative\":\"Trần Thị B\"}"), profile()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("representative");
    }

    @Test
    void apply_throwsBadRequest_whenRepresentativeHasUnknownField() {
        assertThatThrownBy(() -> mapper.apply(
                json("{\"representative\":{\"email\":\"a@b.com\"}}"), profile()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("email");
    }

    @Test
    void apply_throwsBadRequest_whenDocumentsIsEmptyOrNotArray() {
        assertThatThrownBy(() -> mapper.apply(json("{\"documents\":[]}"), profile()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("documents");
        assertThatThrownBy(() -> mapper.apply(json("{\"documents\":{\"docType\":\"X\"}}"), profile()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("documents");
        assertThatThrownBy(() -> mapper.apply(json("{\"documents\":[\"CHARTER\"]}"), profile()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("documents");
    }

    @Test
    void apply_throwsBadRequest_whenDocumentHasUnknownFieldOrNoDocType() {
        assertThatThrownBy(() -> mapper.apply(
                json("{\"documents\":[{\"docType\":\"X\",\"expiry\":\"2027-01-01\"}]}"), profile()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expiry");
        assertThatThrownBy(() -> mapper.apply(
                json("{\"documents\":[{\"docNumber\":\"142\"}]}"), profile()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("docType");
    }

    @Test
    void apply_throwsBadRequest_whenIssueDateIsNotIsoDate() {
        assertThatThrownBy(() -> mapper.apply(
                json("{\"documents\":[{\"docType\":\"X\",\"issueDate\":\"15/01/2026\"}]}"), profile()))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("yyyy-MM-dd");
    }

    @Test
    void apply_acceptsNullAndBlankIssueDate() {
        OrganizationProfile profile = profile();

        mapper.apply(json("{\"documents\":[{\"docType\":\"X\",\"issueDate\":null},"
                + "{\"docType\":\"Y\",\"issueDate\":\"  \"}]}"), profile);

        assertThat(profile.getDocuments()).extracting(OrganizationDocument::getIssueDate)
                .containsOnlyNulls();
    }
}
