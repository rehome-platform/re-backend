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
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OrganizationLegalChangeMapper {

    private static final Set<String> ALLOWED_FIELDS =
            Set.of("legalName", "orgType", "taxCode", "representative", "documents");

    private static final Set<String> ALLOWED_REPRESENTATIVE_FIELDS =
            Set.of("fullName", "position", "cccdNumber", "cccdFrontUrl", "cccdBackUrl");

    private static final Set<String> ALLOWED_DOCUMENT_FIELDS =
            Set.of("docType", "docNumber", "issueDate", "issuingAuthority", "fileUrl");

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

    public void validate(JsonNode proposedChanges) {
        requireKnownFields(proposedChanges, ALLOWED_FIELDS, "proposedChanges");

        boolean hasAny = proposedChanges.has("legalName")
                || proposedChanges.has("orgType")
                || proposedChanges.has("taxCode");

        if (proposedChanges.has("representative")) {
            JsonNode representative = proposedChanges.get("representative");
            if (!representative.isObject()) {
                throw new BadRequestException("representative phải là object.");
            }
            requireKnownFields(representative, ALLOWED_REPRESENTATIVE_FIELDS, "representative");
            hasAny = true;
        }

        if (proposedChanges.has("documents")) {
            JsonNode documents = proposedChanges.get("documents");
            if (!documents.isArray() || documents.isEmpty()) {
                throw new BadRequestException("documents phải là mảng và có ít nhất một giấy tờ.");
            }
            for (JsonNode item : documents) {
                if (!item.isObject()) {
                    throw new BadRequestException("Mỗi phần tử của documents phải là object.");
                }
                requireKnownFields(item, ALLOWED_DOCUMENT_FIELDS, "documents[]");
            }
            hasAny = true;
        }

        if (!hasAny) {
            throw new BadRequestException("proposedChanges không có trường pháp lý nào để áp dụng.");
        }
    }

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

    public LegalChangeSummary summarize(JsonNode proposedChanges, OrganizationProfile profile) {
        List<String> labels = new ArrayList<>();
        List<String> oldValues = new ArrayList<>();
        List<String> newValues = new ArrayList<>();

        if (proposedChanges != null && proposedChanges.isObject()) {
            if (proposedChanges.has("legalName")) {
                addDiff(labels, oldValues, newValues, "Tên pháp lý",
                        profile.getLegalName(), textOrNull(proposedChanges, "legalName"));
            }
            if (proposedChanges.has("orgType")) {
                addDiff(labels, oldValues, newValues, "Loại hình tổ chức",
                        profile.getOrgType(), textOrNull(proposedChanges, "orgType"));
            }
            if (proposedChanges.has("taxCode")) {
                addDiff(labels, oldValues, newValues, "Mã số thuế",
                        profile.getTaxCode(), textOrNull(proposedChanges, "taxCode"));
            }
            if (proposedChanges.has("representative") && proposedChanges.get("representative").isObject()) {
                summarizeRepresentative(proposedChanges.get("representative"), profile.getRepresentative(),
                        labels, oldValues, newValues);
            }
            if (proposedChanges.has("documents") && proposedChanges.get("documents").isArray()) {
                summarizeDocuments(proposedChanges.get("documents"), profile.getDocuments(),
                        labels, oldValues, newValues);
            }
        }

        return new LegalChangeSummary(
                String.join("; ", labels), String.join("; ", oldValues), String.join("; ", newValues));
    }

    private void summarizeRepresentative(JsonNode node, OrganizationRepresentative current,
                                          List<String> labels, List<String> oldValues, List<String> newValues) {
        if (node.has("fullName")) {
            addDiff(labels, oldValues, newValues, "Họ và tên người đại diện",
                    current == null ? null : current.getFullName(), textOrNull(node, "fullName"));
        }
        if (node.has("position")) {
            addDiff(labels, oldValues, newValues, "Chức vụ người đại diện",
                    current == null ? null : current.getPosition(), textOrNull(node, "position"));
        }
        if (node.has("cccdNumber")) {
            addDiff(labels, oldValues, newValues, "Số CCCD người đại diện",
                    current == null ? null : current.getCccdNumber(), textOrNull(node, "cccdNumber"));
        }
        if (node.has("cccdFrontUrl")) {
            addDiff(labels, oldValues, newValues, "Ảnh CCCD mặt trước",
                    current == null ? null : current.getCccdFrontUrl(), textOrNull(node, "cccdFrontUrl"));
        }
        if (node.has("cccdBackUrl")) {
            addDiff(labels, oldValues, newValues, "Ảnh CCCD mặt sau",
                    current == null ? null : current.getCccdBackUrl(), textOrNull(node, "cccdBackUrl"));
        }
    }

    private void summarizeDocuments(JsonNode node, List<OrganizationDocument> current,
                                     List<String> labels, List<String> oldValues, List<String> newValues) {
        String oldDisplay = current == null ? "" : current.stream()
                .map(OrganizationDocument::getDocType)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(", "));

        List<String> newTypes = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isObject() && item.has("docType")) {
                String docType = textOrNull(item, "docType");
                if (StringUtils.hasText(docType)) {
                    newTypes.add(docType);
                }
            }
        }
        addDiff(labels, oldValues, newValues, "Giấy tờ pháp lý", oldDisplay, String.join(", ", newTypes));
    }

    private void addDiff(List<String> labels, List<String> oldValues, List<String> newValues,
                          String label, String oldValue, String newValue) {
        String oldDisplay = oldValue == null ? "" : oldValue;
        String newDisplay = newValue == null ? "" : newValue;
        if (oldDisplay.equals(newDisplay)) {
            return;
        }
        labels.add(label);
        oldValues.add(oldDisplay);
        newValues.add(newDisplay);
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return text == null ? null : text.trim();
    }

    public record LegalChangeSummary(String fieldChanged, String oldValue, String newValue) {}
}
