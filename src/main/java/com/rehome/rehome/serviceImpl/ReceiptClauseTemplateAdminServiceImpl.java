package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.component.ReceiptClauseRenderer;
import com.rehome.rehome.dto.request.ReceiptClauseTemplateRequest;
import com.rehome.rehome.dto.response.ReceiptClauseTemplateResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.ReceiptClauseTemplate;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ReceiptClauseTemplateRepository;
import com.rehome.rehome.service.ReceiptClauseTemplateAdminService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReceiptClauseTemplateAdminServiceImpl implements ReceiptClauseTemplateAdminService {

    private static final String TARGET_TYPE = "RECEIPT_CLAUSE_TEMPLATE";

    private final ReceiptClauseTemplateRepository receiptClauseTemplateRepository;
    private final ReceiptClauseRenderer receiptClauseRenderer;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ReceiptClauseTemplateResponse> list() {
        return receiptClauseTemplateRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(ReceiptClauseTemplateResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public ReceiptClauseTemplateResponse create(Account admin, ReceiptClauseTemplateRequest request) {
        String title = requireText(request.getTitle());
        String body = requireText(request.getBodyTemplate());
        receiptClauseRenderer.validatePlaceholders(body);

        int displayOrder = request.getDisplayOrder() != null
                ? request.getDisplayOrder()
                : receiptClauseTemplateRepository.findTopByOrderByDisplayOrderDesc()
                        .map(t -> t.getDisplayOrder() + 1)
                        .orElse(1);

        ReceiptClauseTemplate entity = ReceiptClauseTemplate.builder()
                .displayOrder(displayOrder)
                .title(title)
                .bodyTemplate(body)
                .active(request.getActive() == null || request.getActive())
                .build();
        ReceiptClauseTemplate saved = receiptClauseTemplateRepository.save(entity);

        auditLogger.log(admin, "CREATE_RECEIPT_CLAUSE_TEMPLATE", TARGET_TYPE, saved.getId().toString(),
                snapshot(saved));
        return ReceiptClauseTemplateResponse.from(saved);
    }

    @Override
    @Transactional
    public ReceiptClauseTemplateResponse update(Account admin, UUID id, ReceiptClauseTemplateRequest request) {
        ReceiptClauseTemplate entity = findOrThrow(id);
        String title = requireText(request.getTitle());
        String body = requireText(request.getBodyTemplate());
        receiptClauseRenderer.validatePlaceholders(body);

        ObjectNode before = snapshot(entity);
        entity.setTitle(title);
        entity.setBodyTemplate(body);
        if (request.getDisplayOrder() != null) {
            entity.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getActive() != null) {
            entity.setActive(request.getActive());
        }
        ReceiptClauseTemplate saved = receiptClauseTemplateRepository.save(entity);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.set("before", before);
        detail.set("after", snapshot(saved));
        auditLogger.log(admin, "UPDATE_RECEIPT_CLAUSE_TEMPLATE", TARGET_TYPE, saved.getId().toString(), detail);
        return ReceiptClauseTemplateResponse.from(saved);
    }

    @Override
    @Transactional
    public void delete(Account admin, UUID id) {
        ReceiptClauseTemplate entity = findOrThrow(id);
        ObjectNode detail = snapshot(entity);
        receiptClauseTemplateRepository.delete(entity);
        auditLogger.log(admin, "DELETE_RECEIPT_CLAUSE_TEMPLATE", TARGET_TYPE, id.toString(), detail);
    }

    @Override
    @Transactional
    public ReceiptClauseTemplateResponse toggle(Account admin, UUID id) {
        ReceiptClauseTemplate entity = findOrThrow(id);
        boolean before = Boolean.TRUE.equals(entity.getActive());
        entity.setActive(!before);
        ReceiptClauseTemplate saved = receiptClauseTemplateRepository.save(entity);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("title", saved.getTitle());
        detail.put("beforeActive", before);
        detail.put("afterActive", saved.getActive());
        auditLogger.log(admin, "TOGGLE_RECEIPT_CLAUSE_TEMPLATE", TARGET_TYPE, saved.getId().toString(), detail);
        return ReceiptClauseTemplateResponse.from(saved);
    }

    private ReceiptClauseTemplate findOrThrow(UUID id) {
        return receiptClauseTemplateRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RECEIPT_CLAUSE_TEMPLATE_NOT_FOUND));
    }

    private static String requireText(String s) {
        if (s == null || s.trim().isEmpty()) {
            throw new AppException(ErrorCode.RECEIPT_CLAUSE_TEMPLATE_INVALID);
        }
        return s.trim();
    }

    private ObjectNode snapshot(ReceiptClauseTemplate t) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("displayOrder", t.getDisplayOrder());
        node.put("title", t.getTitle());
        node.put("bodyTemplate", t.getBodyTemplate());
        node.put("active", Boolean.TRUE.equals(t.getActive()));
        return node;
    }
}
