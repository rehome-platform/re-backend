package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateCategoryRequest;
import com.rehome.rehome.dto.request.UpdateCategoryRequest;
import com.rehome.rehome.dto.response.CatalogStatusResponse;
import com.rehome.rehome.dto.response.CategoryResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.TryOnSlot;
import com.rehome.rehome.entity.marketplace.Category;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.CategoryRepository;
import com.rehome.rehome.serviceImpl.CategoryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock private CategoryRepository categoryRepository;
    @InjectMocks private CategoryServiceImpl categoryService;

    private CreateCategoryRequest createReq(String name, TryOnSlot slot) {
        CreateCategoryRequest r = new CreateCategoryRequest();
        r.setName(name);
        r.setTryonSlot(slot);
        return r;
    }

    private UpdateCategoryRequest updateReq(String name, TryOnSlot slot, CatalogStatus status) {
        UpdateCategoryRequest r = new UpdateCategoryRequest();
        r.setName(name);
        r.setTryonSlot(slot);
        r.setStatus(status);
        return r;
    }

    private Category category(UUID id, String name, TryOnSlot slot, CatalogStatus status) {
        return Category.builder().id(id).name(name).tryonSlot(slot).status(status).code("AS").build();
    }

    // ==================== API 45: create ====================

    @Test
    @DisplayName("create: thành công -> chuẩn hoá tên, ACTIVE, có code")
    void create_success() {
        when(categoryRepository.existsByNameIgnoreCase("Áo sơ mi")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(i -> i.getArgument(0));

        CategoryResponse res = categoryService.create(createReq("  Áo   sơ  mi ", TryOnSlot.TOP));

        assertEquals("Áo sơ mi", res.getName());
        assertEquals(TryOnSlot.TOP, res.getTryonSlot());
        assertEquals(CatalogStatus.ACTIVE, res.getStatus());

        ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(captor.capture());
        assertNotNull(captor.getValue().getCode());
        assertFalse(captor.getValue().getCode().isBlank());
    }

    @Test
    @DisplayName("create: tên trùng (không phân biệt hoa thường) -> CATEGORY_NAME_ALREADY_EXISTS")
    void create_duplicateName() {
        when(categoryRepository.existsByNameIgnoreCase("Áo sơ mi")).thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> categoryService.create(createReq("Áo sơ mi", TryOnSlot.TOP)));

        assertEquals(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS, ex.getErrorCode());
        verify(categoryRepository, never()).save(any());
    }

    // ==================== API 47: update ====================

    @Test
    @DisplayName("update: thành công -> đổi tên, tryonSlot; status null giữ nguyên")
    void update_keepStatusWhenNull() {
        UUID id = UUID.randomUUID();
        Category c = category(id, "Áo cũ", TryOnSlot.TOP, CatalogStatus.ACTIVE);
        when(categoryRepository.findById(id)).thenReturn(Optional.of(c));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Áo sơ mi nam", id)).thenReturn(false);

        CategoryResponse res = categoryService.update(id, updateReq(" Áo sơ mi  nam ", TryOnSlot.DRESS, null));

        assertEquals("Áo sơ mi nam", res.getName());
        assertEquals(TryOnSlot.DRESS, res.getTryonSlot());
        assertEquals(CatalogStatus.ACTIVE, res.getStatus());
        verify(categoryRepository).save(c);
    }

    @Test
    @DisplayName("update: có status -> cập nhật status")
    void update_changeStatus() {
        UUID id = UUID.randomUUID();
        Category c = category(id, "Áo", TryOnSlot.TOP, CatalogStatus.HIDDEN);
        when(categoryRepository.findById(id)).thenReturn(Optional.of(c));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Áo", id)).thenReturn(false);

        CategoryResponse res = categoryService.update(id, updateReq("Áo", TryOnSlot.TOP, CatalogStatus.ACTIVE));

        assertEquals(CatalogStatus.ACTIVE, res.getStatus());
    }

    @Test
    @DisplayName("update: không giữ nguyên code cũ khi sửa tên")
    void update_codeUnchanged() {
        UUID id = UUID.randomUUID();
        Category c = category(id, "Áo cũ", TryOnSlot.TOP, CatalogStatus.ACTIVE);
        when(categoryRepository.findById(id)).thenReturn(Optional.of(c));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot(any(), any())).thenReturn(false);

        categoryService.update(id, updateReq("Áo mới", TryOnSlot.TOP, null));

        assertEquals("AS", c.getCode());
    }

    @Test
    @DisplayName("update: không tìm thấy -> CATEGORY_NOT_FOUND")
    void update_notFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> categoryService.update(id, updateReq("Áo", TryOnSlot.TOP, null)));

        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, ex.getErrorCode());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: trùng tên với danh mục khác -> CATEGORY_NAME_ALREADY_EXISTS")
    void update_duplicateName() {
        UUID id = UUID.randomUUID();
        Category c = category(id, "Áo", TryOnSlot.TOP, CatalogStatus.ACTIVE);
        when(categoryRepository.findById(id)).thenReturn(Optional.of(c));
        when(categoryRepository.existsByNameIgnoreCaseAndIdNot("Quần", id)).thenReturn(true);

        AppException ex = assertThrows(AppException.class,
                () -> categoryService.update(id, updateReq("Quần", TryOnSlot.BOTTOM, null)));

        assertEquals(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS, ex.getErrorCode());
        assertEquals("Áo", c.getName());
        verify(categoryRepository, never()).save(any());
    }

    // ==================== API 48: hide ====================

    @Test
    @DisplayName("hide: ACTIVE -> HIDDEN và lưu")
    void hide_success() {
        UUID id = UUID.randomUUID();
        Category c = category(id, "Áo", TryOnSlot.TOP, CatalogStatus.ACTIVE);
        when(categoryRepository.findById(id)).thenReturn(Optional.of(c));

        CatalogStatusResponse res = categoryService.hide(id);

        assertEquals(CatalogStatus.HIDDEN, res.getStatus());
        verify(categoryRepository).save(c);
    }

    @Test
    @DisplayName("hide: đã HIDDEN -> idempotent, trả 200 và không save lại")
    void hide_idempotent() {
        UUID id = UUID.randomUUID();
        Category c = category(id, "Áo", TryOnSlot.TOP, CatalogStatus.HIDDEN);
        when(categoryRepository.findById(id)).thenReturn(Optional.of(c));

        CatalogStatusResponse res = categoryService.hide(id);

        assertEquals(CatalogStatus.HIDDEN, res.getStatus());
        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("hide: không tìm thấy -> CATEGORY_NOT_FOUND")
    void hide_notFound() {
        UUID id = UUID.randomUUID();
        when(categoryRepository.findById(id)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> categoryService.hide(id));

        assertEquals(ErrorCode.CATEGORY_NOT_FOUND, ex.getErrorCode());
    }

    // ==================== API 44: list ====================

    @Test
    @DisplayName("list: Guest (account null) -> luôn chỉ ACTIVE kể cả khi xin HIDDEN")
    void list_guestAlwaysActive() {
        Category c = category(UUID.randomUUID(), "Áo", TryOnSlot.TOP, CatalogStatus.ACTIVE);
        when(categoryRepository.findByStatusOrderByNameAsc(CatalogStatus.ACTIVE)).thenReturn(List.of(c));

        List<CategoryResponse> res = categoryService.list("HIDDEN", null);

        assertEquals(1, res.size());
        assertEquals("Áo", res.get(0).getName());
        verify(categoryRepository, never()).findByStatusOrderByNameAsc(CatalogStatus.HIDDEN);
    }

    @Test
    @DisplayName("list: Moderator xin HIDDEN -> trả danh mục HIDDEN")
    void list_moderatorHidden() {
        Account mod = Account.builder().role(Role.MODERATOR).build();
        Category c = category(UUID.randomUUID(), "Áo cũ", TryOnSlot.TOP, CatalogStatus.HIDDEN);
        when(categoryRepository.findByStatusOrderByNameAsc(CatalogStatus.HIDDEN)).thenReturn(List.of(c));

        List<CategoryResponse> res = categoryService.list("hidden", mod);

        assertEquals(1, res.size());
        assertEquals(CatalogStatus.HIDDEN, res.get(0).getStatus());
    }

    @Test
    @DisplayName("list: không truyền status -> mặc định ACTIVE")
    void list_defaultActive() {
        when(categoryRepository.findByStatusOrderByNameAsc(CatalogStatus.ACTIVE)).thenReturn(List.of());

        assertTrue(categoryService.list(null, null).isEmpty());
        verify(categoryRepository).findByStatusOrderByNameAsc(CatalogStatus.ACTIVE);
    }

    @Test
    @DisplayName("list: status không hợp lệ -> INVALID_STATUS, không query DB")
    void list_invalidStatus() {
        AppException ex = assertThrows(AppException.class, () -> categoryService.list("ABC", null));

        assertEquals(ErrorCode.INVALID_STATUS, ex.getErrorCode());
        verifyNoInteractions(categoryRepository);
    }
}