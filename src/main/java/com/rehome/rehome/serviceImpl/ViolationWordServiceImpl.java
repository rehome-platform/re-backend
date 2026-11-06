package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.dto.request.CreateViolationWordRequest;
import com.rehome.rehome.dto.request.UpdateViolationWordRequest;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ViolationWordResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ViolationWordCategory;
import com.rehome.rehome.entity.enums.ViolationWordSeverity;
import com.rehome.rehome.entity.marketplace.ViolationWord;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ViolationWordRepository;
import com.rehome.rehome.repository.spec.ViolationWordSpecifications;
import com.rehome.rehome.service.ViolationWordService;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ViolationWordServiceImpl implements ViolationWordService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String TARGET_TYPE = "VIOLATION_WORD";

    private final ViolationWordRepository violationWordRepository;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ViolationWordResponse> search(String keyword, ViolationWordCategory category,
                                                      ViolationWordSeverity severity, Boolean isActive,
                                                      int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<ViolationWord> result = violationWordRepository.findAll(
                ViolationWordSpecifications.filter(keyword, category, severity, isActive),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return PageResponse.from(result.map(ViolationWordResponse::from));
    }

    @Override
    @Transactional
    public ViolationWordResponse create(Account admin, CreateViolationWordRequest request) {
        String word = normalize(request.getWord());
        if (violationWordRepository.existsByWord(word)) {
            throw new AppException(ErrorCode.VIOLATION_WORD_ALREADY_EXISTS);
        }

        ViolationWord entity = ViolationWord.builder()
                .word(word)
                .category(request.getCategory())
                .severity(request.getSeverity())
                .action(request.getAction())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .note(trimToNull(request.getNote()))
                .build();
        ViolationWord saved = saveOrConflict(entity);

        auditLogger.log(admin, "CREATE_VIOLATION_WORD", TARGET_TYPE, saved.getId().toString(), snapshot(saved));
        return ViolationWordResponse.from(saved);
    }

    @Override
    @Transactional
    public ViolationWordResponse update(Account admin, UUID id, UpdateViolationWordRequest request) {
        ViolationWord entity = findOrThrow(id);
        String word = normalize(request.getWord());
        if (violationWordRepository.existsByWordAndIdNot(word, id)) {
            throw new AppException(ErrorCode.VIOLATION_WORD_ALREADY_EXISTS);
        }

        ObjectNode before = snapshot(entity);
        entity.setWord(word);
        entity.setCategory(request.getCategory());
        entity.setSeverity(request.getSeverity());
        entity.setAction(request.getAction());
        if (request.getIsActive() != null) {
            entity.setIsActive(request.getIsActive());
        }
        entity.setNote(trimToNull(request.getNote()));
        ViolationWord saved = saveOrConflict(entity);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.set("before", before);
        detail.set("after", snapshot(saved));
        auditLogger.log(admin, "UPDATE_VIOLATION_WORD", TARGET_TYPE, saved.getId().toString(), detail);
        return ViolationWordResponse.from(saved);
    }

    @Override
    @Transactional
    public void delete(Account admin, UUID id) {
        ViolationWord entity = findOrThrow(id);
        ObjectNode detail = snapshot(entity);
        violationWordRepository.delete(entity);
        auditLogger.log(admin, "DELETE_VIOLATION_WORD", TARGET_TYPE, id.toString(), detail);
    }

    @Override
    @Transactional
    public ViolationWordResponse toggle(Account admin, UUID id) {
        ViolationWord entity = findOrThrow(id);
        boolean before = Boolean.TRUE.equals(entity.getIsActive());
        entity.setIsActive(!before);
        ViolationWord saved = violationWordRepository.save(entity);

        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("word", saved.getWord());
        detail.put("beforeIsActive", before);
        detail.put("afterIsActive", saved.getIsActive());
        auditLogger.log(admin, "TOGGLE_VIOLATION_WORD", TARGET_TYPE, saved.getId().toString(), detail);
        return ViolationWordResponse.from(saved);
    }

    private ViolationWord findOrThrow(UUID id) {
        return violationWordRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.VIOLATION_WORD_NOT_FOUND));
    }

    // Hai request tạo cùng từ chạy song song có thể cùng qua existsByWord; ràng buộc unique là chốt chặn cuối
    private ViolationWord saveOrConflict(ViolationWord entity) {
        try {
            return violationWordRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException e) {
            throw new AppException(ErrorCode.VIOLATION_WORD_ALREADY_EXISTS);
        }
    }

    // trim + chữ thường + gộp mọi khoảng trắng liên tiếp về 1 dấu cách
    static String normalize(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private ObjectNode snapshot(ViolationWord w) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("word", w.getWord());
        node.put("category", w.getCategory().name());
        node.put("severity", w.getSeverity().name());
        node.put("action", w.getAction().name());
        node.put("isActive", Boolean.TRUE.equals(w.getIsActive()));
        node.put("note", w.getNote());
        return node;
    }
}
