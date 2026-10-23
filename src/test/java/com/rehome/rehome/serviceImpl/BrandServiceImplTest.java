package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateBrandRequest;
import com.rehome.rehome.dto.request.UpdateBrandRequest;
import com.rehome.rehome.dto.response.BrandResponse;
import com.rehome.rehome.dto.response.CatalogStatusResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.Brand;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.BrandRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
class BrandServiceImplTest {

    @Mock private BrandRepository brandRepository;
    @InjectMocks private BrandServiceImpl brandService;

    private CreateBrandRequest createReq(String name) {
        CreateBrandRequest r = new CreateBrandRequest();
        r.setName(name);
        return r;
    }

    private UpdateBrandRequest updateReq(String name, CatalogStatus status) {
        UpdateBrandRequest r = new UpdateBrandRequest();
        r.setName(name);
        r.setStatus(status);
        return r;
    }

    private Brand brand(UUID id, String name, CatalogStatus status) {
        return Brand.builder().id(id).name(name).status(status).build();
    }

    // ==================== API 46: create ====================

    @Test
    @DisplayName("create: thành công -> chuẩn hoá tên và ACTIVE")
    void create_success() {
        when(brandRepository.existsByNameIgnoreCase("Zara Paris")).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenAnswer(i -> i.getArgument(0));

        BrandResponse res = brandService.create(createReq("  Zara   Paris "));

        assertEquals("Zara Paris", res.getName());
        assertEquals(CatalogStatus.ACTIVE, res.getStatus());
        verify(brandRepository).existsByNameIgnoreCase("Zara Paris");
    }

    @Test
    @DisplayName("create: tên trùng -> BRAND_NAME_ALREADY_EXISTS, không save")
    void create_duplicate() {
        when(brandRepository.existsByNameIgnoreCase("Zara")).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> brandService.create(createReq("Zara")));

        assertEquals(ErrorCode.BRAND_NAME_ALREADY_EXISTS, ex.getErrorCode());
        verify(brandRepository, never()).save(any());
    }

    // ==================== API 47: update ====================

    @Test
    @DisplayName("update: đổi tên, status null giữ nguyên")
    void update_keepStatusWhenNull() {
        UUID id = UUID.randomUUID();
        Brand b = brand(id, "Zara", CatalogStatus.ACTIVE);
        when(brandRepository.findById(id)).thenReturn(Optional.of(b));
        when(brandRepository.existsByNameIgnoreCaseAndIdNot("Zara Home", id)).thenReturn(false);

        BrandResponse res = brandService.update(id, updateReq("  Zara  Home ", null));

        assertEquals("Zara Home", res.getName());
        assertEquals(CatalogStatus.ACTIVE, res.getStatus());
        verify(brandRepository).save(b);
    }

    @Test
    @DisplayName("update: có status -> cập nhật status")
    void update_changeStatus() {
        UUID id = UUID.randomUUID();
        Brand b = brand(id, "Zara", CatalogStatus.HIDDEN);
        when(brandRepository.findById(id)).thenReturn(Optional.of(b));
        when(brandRepository.existsByNameIgnoreCaseAndIdNot("Zara", id)).thenReturn(false);

        BrandResponse res = brandService.update(id, updateReq("Zara", CatalogStatus.ACTIVE));

        assertEquals(CatalogStatus.ACTIVE, res.getStatus());
    }

    @Test
    @DisplayName("update: giữ nguyên tên của chính nó vẫn hợp lệ (loại trừ id hiện tại)")
    void update_sameNameOfItself() {
        UUID id = UUID.randomUUID();
        Brand b = brand(id, "Zara", CatalogStatus.ACTIVE);
        when(brandRepository.findById(id)).thenReturn(Optional.of(b));
        when(brandRepository.existsByNameIgnoreCaseAndIdNot("Zara", id)).thenReturn(false);

        assertDoesNotThrow(() -> brandService.update(id, updateReq("Zara", null)));
    }

    @Test
    @DisplayName("update: không tìm thấy -> BRAND_NOT_FOUND")
    void update_notFound() {
        UUID id = UUID.randomUUID();
        when(brandRepository.findById(id)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> brandService.update(id, updateReq("Zara", null)));

        assertEquals(ErrorCode.BRAND_NOT_FOUND, ex.getErrorCode());
        verify(brandRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: trùng tên thương hiệu khác -> BRAND_NAME_ALREADY_EXISTS")
    void update_duplicate() {
        UUID id = UUID.randomUUID();
        Brand b = brand(id, "Zara", CatalogStatus.ACTIVE);
        when(brandRepository.findById(id)).thenReturn(Optional.of(b));
        when(brandRepository.existsByNameIgnoreCaseAndIdNot("H&M", id)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> brandService.update(id, updateReq("H&M", null)));

        assertEquals(ErrorCode.BRAND_NAME_ALREADY_EXISTS, ex.getErrorCode());
        assertEquals("Zara", b.getName());
        verify(brandRepository, never()).save(any());
    }

    // ==================== API 48: hide ====================

    @Test
    @DisplayName("hide: ACTIVE -> HIDDEN và lưu")
    void hide_success() {
        UUID id = UUID.randomUUID();
        Brand b = brand(id, "Zara", CatalogStatus.ACTIVE);
        when(brandRepository.findById(id)).thenReturn(Optional.of(b));

        CatalogStatusResponse res = brandService.hide(id);

        assertEquals(CatalogStatus.HIDDEN, res.getStatus());
        verify(brandRepository).save(b);
    }

    @Test
    @DisplayName("hide: đã HIDDEN -> idempotent, không save lại")
    void hide_idempotent() {
        UUID id = UUID.randomUUID();
        Brand b = brand(id, "Zara", CatalogStatus.HIDDEN);
        when(brandRepository.findById(id)).thenReturn(Optional.of(b));

        CatalogStatusResponse res = brandService.hide(id);

        assertEquals(CatalogStatus.HIDDEN, res.getStatus());
        verify(brandRepository, never()).save(any());
    }

    @Test
    @DisplayName("hide: không tìm thấy -> BRAND_NOT_FOUND")
    void hide_notFound() {
        UUID id = UUID.randomUUID();
        when(brandRepository.findById(id)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> brandService.hide(id));

        assertEquals(ErrorCode.BRAND_NOT_FOUND, ex.getErrorCode());
    }

    // ==================== API 44: list ====================

    @Test
    @DisplayName("list: Guest xin HIDDEN -> vẫn chỉ nhận ACTIVE")
    void list_guestAlwaysActive() {
        Brand b = brand(UUID.randomUUID(), "Zara", CatalogStatus.ACTIVE);
        when(brandRepository.findByStatusOrderByNameAsc(CatalogStatus.ACTIVE)).thenReturn(List.of(b));

        List<BrandResponse> res = brandService.list("HIDDEN", null);

        assertEquals(1, res.size());
        assertEquals("Zara", res.get(0).getName());
        verify(brandRepository, never()).findByStatusOrderByNameAsc(CatalogStatus.HIDDEN);
    }

    @Test
    @DisplayName("list: Moderator xin HIDDEN -> trả thương hiệu HIDDEN")
    void list_moderatorHidden() {
        Account mod = Account.builder().role(Role.MODERATOR).build();
        Brand b = brand(UUID.randomUUID(), "Old Brand", CatalogStatus.HIDDEN);
        when(brandRepository.findByStatusOrderByNameAsc(CatalogStatus.HIDDEN)).thenReturn(List.of(b));

        List<BrandResponse> res = brandService.list("HIDDEN", mod);

        assertEquals(1, res.size());
        assertEquals(CatalogStatus.HIDDEN, res.get(0).getStatus());
    }

    @Test
    @DisplayName("list: status không hợp lệ -> INVALID_STATUS")
    void list_invalidStatus() {
        AppException ex = assertThrows(AppException.class, () -> brandService.list("ABC", null));

        assertEquals(ErrorCode.INVALID_STATUS, ex.getErrorCode());
        verifyNoInteractions(brandRepository);
    }
}