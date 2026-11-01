package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.OrganizationType;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.util.LegalFileLinks;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Hồ sơ xác minh tổ chức cho Moderator (F-137).
 * Checklist đổi theo orgType và theo việc tổ chức có mã số thuế hay không —
 * nguồn quy tắc: docs/OVERVIEW.md mục 9 "Xác minh tổ chức".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrganizationVerificationResponse {

    private String orgType;
    private String legalName;
    private String taxCode;
    private VerificationStatus verificationStatus;
    private RepresentativeInfo representative;
    private List<DocumentInfo> documents;
    private List<ChecklistItem> checklist;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RepresentativeInfo {
        private String fullName;
        private String position;
        private String cccdNumber;
        // Ảnh CCCD chỉ Moderator xem được (OVERVIEW mục 9), dùng để đối chiếu tên với giấy phép.
        // F-139: KHÔNG phải URL storage mà là đường dẫn endpoint có kiểm quyền
        // (/api/organizations/{id}/legal-files/cccd-front) — FE phải gọi kèm header
        // Authorization. null nếu ảnh đã bị job xóa sau khi hết thời hạn giữ.
        private String cccdFrontUrl;
        private String cccdBackUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DocumentInfo {
        private String docType;
        private String docNumber;
        private LocalDate issueDate;
        private String issuingAuthority;
        // F-139: đường dẫn endpoint có kiểm quyền, không phải URL storage; null nếu đã hết hạn giữ
        private String fileUrl;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ChecklistItem {
        private String item;
        private boolean required;
    }

    public static OrganizationVerificationResponse from(OrganizationProfile profile) {
        boolean hasTaxCode = StringUtils.hasText(profile.getTaxCode());
        UUID organizationId = profile.getAccountId();

        return OrganizationVerificationResponse.builder()
                .orgType(profile.getOrgType())
                .legalName(profile.getLegalName())
                .taxCode(profile.getTaxCode())
                .verificationStatus(profile.getVerificationStatus())
                .representative(profile.getRepresentative() == null ? null : RepresentativeInfo.builder()
                        .fullName(profile.getRepresentative().getFullName())
                        .position(profile.getRepresentative().getPosition())
                        .cccdNumber(profile.getRepresentative().getCccdNumber())
                        .cccdFrontUrl(LegalFileLinks.cccdFrontOrNull(
                                organizationId, profile.getRepresentative().getCccdFrontUrl()))
                        .cccdBackUrl(LegalFileLinks.cccdBackOrNull(
                                organizationId, profile.getRepresentative().getCccdBackUrl()))
                        .build())
                .documents(profile.getDocuments() == null ? List.of() : profile.getDocuments().stream()
                        .map(doc -> DocumentInfo.builder()
                                .docType(doc.getDocType())
                                .docNumber(doc.getDocNumber())
                                .issueDate(doc.getIssueDate())
                                .issuingAuthority(doc.getIssuingAuthority())
                                .fileUrl(LegalFileLinks.documentOrNull(
                                        organizationId, doc.getId(), doc.getFileUrl()))
                                .build())
                        .toList())
                .checklist(buildChecklist(profile.getOrgType(), hasTaxCode))
                .build();
    }

    /**
     * Giấy tờ bắt buộc theo loại tổ chức (OVERVIEW mục 9, bảng "Các loại tổ chức được chấp nhận
     * và giấy tờ tương ứng") rồi cộng các bước kiểm chứng chung.
     */
    private static List<ChecklistItem> buildChecklist(String rawOrgType, boolean hasTaxCode) {
        List<ChecklistItem> checklist = new ArrayList<>(documentItemsFor(rawOrgType));

        // Bước 3: kiểm ảnh giấy tờ
        checklist.add(required("Ảnh giấy tờ còn hiệu lực, rõ nét, có ngày cấp và dấu, tên khớp thông tin đã khai"));
        // Bước 4 + yêu cầu nộp ảnh CCCD của người đại diện
        checklist.add(required("Tên trên CCCD của người đại diện khớp tên trên giấy phép"));
        // Bước 1: bắt buộc khi tổ chức có mã số thuế; không có thì không tra được
        checklist.add(new ChecklistItem(
                "Đối chiếu tên, địa chỉ, tình trạng hoạt động theo mã số thuế trên tracuunnt.gdt.gov.vn",
                hasTaxCode));
        // Bước 5: khi không có mã số thuế thì trọng số dồn sang gọi điện xác nhận
        checklist.add(new ChecklistItem(
                "Gọi điện hoặc gửi email tới số và địa chỉ công khai của tổ chức để xác nhận đúng họ nộp hồ sơ",
                !hasTaxCode));
        // Bước 7: tham khảo, không bắt buộc
        checklist.add(new ChecklistItem(
                "Tìm tên tổ chức trên báo chí và mạng xã hội xem có dấu hiệu lừa đảo", false));

        return checklist;
    }

    /**
     * Số giấy tờ tối thiểu loại tổ chức này phải nộp — dùng lại bảng giấy tờ ở mục 9 để
     * F-138 chặn duyệt khi hồ sơ còn thiếu, thay vì nhân đôi bảng đó ở service.
     */
    public static int requiredDocumentCount(String rawOrgType) {
        return documentItemsFor(rawOrgType).size();
    }

    private static List<ChecklistItem> documentItemsFor(String rawOrgType) {
        OrganizationType orgType = parseOrgType(rawOrgType);
        if (orgType == null) {
            return List.of(required("Giấy tờ thành lập do cơ quan có thẩm quyền cấp"));
        }
        return switch (orgType) {
            case CHARITY_FUND -> List.of(
                    required("Giấy phép thành lập và công nhận điều lệ quỹ"));
            case NGO -> List.of(
                    required("Quyết định cho phép thành lập hội kèm điều lệ được phê duyệt"));
            case SCHOOL_CLUB -> List.of(
                    required("Giấy tờ pháp nhân của tổ chức chủ quản"),
                    required("Quyết định thành lập câu lạc bộ"),
                    required("Văn bản cử người phụ trách"));
            case VOLUNTEER_GROUP -> List.of(
                    required("Có pháp nhân đứng tên chịu trách nhiệm (nhóm tự phát không được chấp nhận)"),
                    required("Giấy tờ thành lập do cơ quan có thẩm quyền cấp"));
            case SOCIAL_PROTECTION_CENTER -> List.of(
                    required("Giấy chứng nhận đăng ký thành lập"),
                    required("Giấy phép hoạt động"));
            case RELIGIOUS, OTHER -> List.of(
                    required("Giấy tờ thành lập do cơ quan có thẩm quyền cấp"));
        };
    }

    // orgType lưu dạng String nên mã lạ vẫn phải ra được checklist chung
    private static OrganizationType parseOrgType(String rawOrgType) {
        if (!StringUtils.hasText(rawOrgType)) {
            return null;
        }
        try {
            return OrganizationType.valueOf(rawOrgType.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static ChecklistItem required(String item) {
        return new ChecklistItem(item, true);
    }
}
