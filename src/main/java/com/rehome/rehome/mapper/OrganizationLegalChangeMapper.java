package com.rehome.rehome.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.rehome.rehome.entity.auth.OrganizationDocument;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.auth.OrganizationRepresentative;
import com.rehome.rehome.entity.enums.OrganizationType;
import com.rehome.rehome.exception.BadRequestException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Áp nội dung {@code proposedChanges} của một yêu cầu đổi thông tin pháp lý (F-097)
 * vào hồ sơ tổ chức khi Moderator duyệt (F-140).
 *
 * <p>{@code proposedChanges} là JSON tự do do tổ chức gửi lên, nên ở đây là **whitelist**:
 * chỉ các trường pháp lý mà UC-51 nêu (tên, người đại diện, giấy tờ) cộng hai trường định danh
 * pháp lý khác (orgType, taxCode) được áp. Trường lạ thì BÁO LỖI chứ không bỏ qua im lặng —
 * bỏ qua im lặng nghĩa là Moderator bấm duyệt mà thứ tổ chức xin đổi lại không được áp.
 *
 * <p>Các trường thông tin thường (logo, mô tả, số điện thoại, giờ nhận) không đi đường này:
 * tổ chức tự sửa ngay qua API 117 (F-096).
 */
@Component
public class OrganizationLegalChangeMapper {

    private static final Set<String> ALLOWED_FIELDS =
            Set.of("legalName", "orgType", "taxCode", "representative", "documents");

    private static final Set<String> ALLOWED_REPRESENTATIVE_FIELDS =
            Set.of("fullName", "position", "cccdNumber", "cccdFrontUrl", "cccdBackUrl");

    private static final Set<String> ALLOWED_DOCUMENT_FIELDS =
            Set.of("docType", "docNumber", "issueDate", "issuingAuthority", "fileUrl");

    /**
     * @return true nếu nội dung được duyệt có kèm ảnh giấy tờ mới (ảnh CCCD hoặc tệp giấy tờ).
     *         Service dùng tin này để đặt lại mốc giữ ảnh của F-139 — nếu không, ảnh vừa nộp
     *         sẽ bị job dọn xóa ngay vì mốc {@code verifiedAt} là của lần xác minh trước.
     */
    public boolean apply(JsonNode proposedChanges, OrganizationProfile profile) {
        requireKnownFields(proposedChanges, ALLOWED_FIELDS, "proposedChanges");

        boolean applied = false;
        boolean imagesReplaced = false;

        if (proposedChanges.has("legalName")) {
            profile.setLegalName(requireText(proposedChanges, "legalName"));
            applied = true;
        }
        if (proposedChanges.has("orgType")) {
            profile.setOrgType(parseOrgType(requireText(proposedChanges, "orgType")));
            applied = true;
        }
        if (proposedChanges.has("taxCode")) {
            // Tổ chức không có mã số thuế vẫn được chấp nhận (OVERVIEW mục 9) nên cho phép bỏ trống
            profile.setTaxCode(blankToNull(proposedChanges.get("taxCode").asText(null)));
            applied = true;
        }
        if (proposedChanges.has("representative")) {
            imagesReplaced = applyRepresentative(proposedChanges.get("representative"), profile);
            applied = true;
        }
        if (proposedChanges.has("documents")) {
            imagesReplaced = applyDocuments(proposedChanges.get("documents"), profile) || imagesReplaced;
            applied = true;
        }

        if (!applied) {
            throw new BadRequestException("proposedChanges không có trường pháp lý nào để áp dụng.");
        }
        return imagesReplaced;
    }

    /** PATCH từng field: chỉ field có mặt trong JSON mới bị đổi, còn lại giữ nguyên. */
    private boolean applyRepresentative(JsonNode node, OrganizationProfile profile) {
        if (!node.isObject()) {
            throw new BadRequestException("representative phải là object.");
        }
        requireKnownFields(node, ALLOWED_REPRESENTATIVE_FIELDS, "representative");

        OrganizationRepresentative representative = profile.getRepresentative();
        if (representative == null) {
            representative = new OrganizationRepresentative();
            profile.setRepresentative(representative);
        }

        if (node.has("fullName")) {
            representative.setFullName(requireText(node, "fullName"));
        }
        if (node.has("position")) {
            representative.setPosition(requireText(node, "position"));
        }
        if (node.has("cccdNumber")) {
            representative.setCccdNumber(requireText(node, "cccdNumber"));
        }

        boolean imagesReplaced = false;
        if (node.has("cccdFrontUrl")) {
            representative.setCccdFrontUrl(requireText(node, "cccdFrontUrl"));
            imagesReplaced = true;
        }
        if (node.has("cccdBackUrl")) {
            representative.setCccdBackUrl(requireText(node, "cccdBackUrl"));
            imagesReplaced = true;
        }
        return imagesReplaced;
    }

    /**
     * Thay toàn bộ danh sách giấy tờ, không merge: tổ chức xin đổi giấy tờ là nộp lại bộ mới,
     * và không có id để đối chiếu từng giấy.
     *
     * <p>Phải clear() + addAll() trên chính list đang có, không setDocuments(listMới):
     * list này có orphanRemoval nên Hibernate báo lỗi nếu bị thay bằng đối tượng khác.
     */
    private boolean applyDocuments(JsonNode node, OrganizationProfile profile) {
        if (!node.isArray() || node.isEmpty()) {
            throw new BadRequestException("documents phải là mảng và có ít nhất một giấy tờ.");
        }

        List<OrganizationDocument> replacement = new ArrayList<>();
        boolean imagesReplaced = false;
        for (JsonNode item : node) {
            if (!item.isObject()) {
                throw new BadRequestException("Mỗi phần tử của documents phải là object.");
            }
            requireKnownFields(item, ALLOWED_DOCUMENT_FIELDS, "documents[]");

            String fileUrl = item.has("fileUrl") ? blankToNull(item.get("fileUrl").asText(null)) : null;
            imagesReplaced = imagesReplaced || fileUrl != null;

            replacement.add(OrganizationDocument.builder()
                    .organization(profile)
                    .docType(requireText(item, "docType"))
                    .docNumber(item.has("docNumber") ? blankToNull(item.get("docNumber").asText(null)) : null)
                    .issueDate(parseIssueDate(item))
                    .issuingAuthority(item.has("issuingAuthority")
                            ? blankToNull(item.get("issuingAuthority").asText(null)) : null)
                    .fileUrl(fileUrl)
                    .build());
        }

        List<OrganizationDocument> current = profile.getDocuments();
        if (current == null) {
            profile.setDocuments(replacement);
        } else {
            current.clear();
            current.addAll(replacement);
        }
        return imagesReplaced;
    }

    private LocalDate parseIssueDate(JsonNode item) {
        if (!item.has("issueDate") || item.get("issueDate").isNull()) {
            return null;
        }
        String raw = item.get("issueDate").asText("");
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            throw new BadRequestException("issueDate phải theo dạng yyyy-MM-dd.");
        }
    }

    /**
     * Trường lạ là lỗi, không bỏ qua: tổ chức gõ sai tên trường (vd. "name" thay vì "legalName")
     * mà vẫn duyệt được thì hồ sơ không đổi gì nhưng cả hai bên đều tưởng đã đổi.
     */
    private void requireKnownFields(JsonNode node, Set<String> allowed, String where) {
        if (node == null || !node.isObject()) {
            throw new BadRequestException(where + " phải là object.");
        }
        List<String> unknown = new ArrayList<>();
        for (Iterator<String> names = node.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!allowed.contains(name)) {
                unknown.add(name);
            }
        }
        if (!unknown.isEmpty()) {
            throw new BadRequestException(
                    where + " có trường không được phép đổi: " + String.join(", ", unknown));
        }
    }

    private String requireText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        String text = value == null || value.isNull() ? null : value.asText(null);
        if (!StringUtils.hasText(text)) {
            throw new BadRequestException(field + " không được để trống.");
        }
        return text.trim();
    }

    private String parseOrgType(String rawOrgType) {
        try {
            return OrganizationType.valueOf(rawOrgType).name();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("orgType không hợp lệ.");
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
