package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.dto.request.CreateViolationWordRequest;
import com.rehome.rehome.dto.request.UpdateViolationWordRequest;
import com.rehome.rehome.dto.response.ViolationWordResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ViolationWordAction;
import com.rehome.rehome.entity.enums.ViolationWordCategory;
import com.rehome.rehome.entity.enums.ViolationWordSeverity;
import com.rehome.rehome.entity.marketplace.ViolationWord;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ViolationWordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViolationWordServiceImplTest {

    @Mock ViolationWordRepository repository;
    @Mock AuditLogger auditLogger;

    private ViolationWordServiceImpl service;
    private Account admin;

    @BeforeEach
    void setUp() {
        service = new ViolationWordServiceImpl(repository, org.mockito.Mockito.mock(org.springframework.context.ApplicationEventPublisher.class), auditLogger, new ObjectMapper());
        admin = Account.builder().id(UUID.randomUUID()).build();
    }

    private CreateViolationWordRequest createRequest(String word) {
        CreateViolationWordRequest r = new CreateViolationWordRequest();
        r.setWord(word);
        r.setCategory(ViolationWordCategory.COUNTERFEIT);
        r.setSeverity(ViolationWordSeverity.BLOCKED);
        r.setAction(ViolationWordAction.AUTO_REJECT);
        return r;
    }

    private UpdateViolationWordRequest updateRequest(String word) {
        UpdateViolationWordRequest r = new UpdateViolationWordRequest();
        r.setWord(word);
        r.setCategory(ViolationWordCategory.SPAM);
        r.setSeverity(ViolationWordSeverity.WARNING);
        r.setAction(ViolationWordAction.AUTO_FLAG);
        return r;
    }

    private ViolationWord existing(UUID id, String word, boolean active) {
        return ViolationWord.builder().id(id).word(word).category(ViolationWordCategory.FRAUD)
                .severity(ViolationWordSeverity.BLOCKED).action(ViolationWordAction.NOTIFY_ADMIN)
                .isActive(active).build();
    }

    private void assertAppError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    @Test
    @DisplayName("create: từ khoá được trim, chữ thường, gộp khoảng trắng; mặc định đang bật; ghi audit log")
    void create_normalizesAndLogsAudit() {
        when(repository.existsByWord("hàng fake")).thenReturn(false);
        when(repository.saveAndFlush(any(ViolationWord.class))).thenAnswer(inv -> {
            ViolationWord w = inv.getArgument(0);
            w.setId(UUID.randomUUID());
            return w;
        });

        ViolationWordResponse result = service.create(admin, createRequest("  Hàng   FAKE "));

        assertThat(result.getWord()).isEqualTo("hàng fake");
        assertThat(result.getIsActive()).isTrue();
        ArgumentCaptor<JsonNode> detail = ArgumentCaptor.forClass(JsonNode.class);
        verify(auditLogger).log(eq(admin), eq("CREATE_VIOLATION_WORD"), eq("VIOLATION_WORD"),
                eq(result.getId().toString()), detail.capture());
        assertThat(detail.getValue().get("word").asText()).isEqualTo("hàng fake");
    }

    @Test
    @DisplayName("create: từ đã tồn tại (sau chuẩn hoá) -> VIOLATION_WORD_ALREADY_EXISTS, không lưu")
    void create_duplicate_throws() {
        when(repository.existsByWord("hàng fake")).thenReturn(true);

        assertAppError(() -> service.create(admin, createRequest("HÀNG FAKE")),
                ErrorCode.VIOLATION_WORD_ALREADY_EXISTS);

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("create: vi phạm ràng buộc unique khi lưu (race) -> VIOLATION_WORD_ALREADY_EXISTS")
    void create_uniqueConstraintRace_throwsConflict() {
        when(repository.existsByWord("spam")).thenReturn(false);
        when(repository.saveAndFlush(any(ViolationWord.class))).thenThrow(new DataIntegrityViolationException("dup"));

        assertAppError(() -> service.create(admin, createRequest("spam")), ErrorCode.VIOLATION_WORD_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("update: không tìm thấy -> VIOLATION_WORD_NOT_FOUND")
    void update_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertAppError(() -> service.update(admin, id, updateRequest("x")), ErrorCode.VIOLATION_WORD_NOT_FOUND);
    }

    @Test
    @DisplayName("update: trùng từ của bản ghi khác -> VIOLATION_WORD_ALREADY_EXISTS")
    void update_duplicateOfOtherRecord_throws() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(existing(id, "cũ", true)));
        when(repository.existsByWordAndIdNot("mới", id)).thenReturn(true);

        assertAppError(() -> service.update(admin, id, updateRequest("Mới")), ErrorCode.VIOLATION_WORD_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("update: isActive không gửi -> giữ nguyên trạng thái; ghi audit before/after")
    void update_keepsActiveWhenNotSent() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(existing(id, "cũ", false)));
        when(repository.existsByWordAndIdNot("mới", id)).thenReturn(false);
        when(repository.saveAndFlush(any(ViolationWord.class))).thenAnswer(inv -> inv.getArgument(0));

        ViolationWordResponse result = service.update(admin, id, updateRequest("Mới"));

        assertThat(result.getWord()).isEqualTo("mới");
        assertThat(result.getCategory()).isEqualTo(ViolationWordCategory.SPAM);
        assertThat(result.getIsActive()).isFalse();
        ArgumentCaptor<JsonNode> detail = ArgumentCaptor.forClass(JsonNode.class);
        verify(auditLogger).log(eq(admin), eq("UPDATE_VIOLATION_WORD"), eq("VIOLATION_WORD"),
                eq(id.toString()), detail.capture());
        assertThat(detail.getValue().get("before").get("word").asText()).isEqualTo("cũ");
        assertThat(detail.getValue().get("after").get("word").asText()).isEqualTo("mới");
    }

    @Test
    @DisplayName("toggle: đảo trạng thái hoạt động và ghi audit log")
    void toggle_flipsActive() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.of(existing(id, "spam", true)));
        when(repository.save(any(ViolationWord.class))).thenAnswer(inv -> inv.getArgument(0));

        ViolationWordResponse result = service.toggle(admin, id);

        assertThat(result.getIsActive()).isFalse();
        verify(auditLogger).log(eq(admin), eq("TOGGLE_VIOLATION_WORD"), eq("VIOLATION_WORD"), eq(id.toString()), any());
    }

    @Test
    @DisplayName("delete: xoá bản ghi và ghi audit log; không tìm thấy -> VIOLATION_WORD_NOT_FOUND")
    void delete_removesAndLogs() {
        UUID id = UUID.randomUUID();
        ViolationWord word = existing(id, "spam", true);
        when(repository.findById(id)).thenReturn(Optional.of(word));

        service.delete(admin, id);

        verify(repository).delete(word);
        verify(auditLogger).log(eq(admin), eq("DELETE_VIOLATION_WORD"), eq("VIOLATION_WORD"), eq(id.toString()), any());

        UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Optional.empty());
        assertAppError(() -> service.delete(admin, missing), ErrorCode.VIOLATION_WORD_NOT_FOUND);
    }

    @Test
    @DisplayName("search: size bị chặn tối đa 100, page âm đưa về 0, sắp theo createdAt giảm dần")
    @SuppressWarnings("unchecked")
    void search_clampsPagingAndSortsNewestFirst() {
        when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.search("fake", null, null, null, -3, 9999);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }
}
