package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.TryOnImageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.TryOnModelType;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.tryon.TryOnImage;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.TryOnImageRepository;
import com.rehome.rehome.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TryOnServiceImplTest {

    private static final String IMAGE_URL = "http://localhost:8080/files/try-on/abc.png";

    @Mock private TryOnImageRepository tryOnImageRepository;
    @Mock private ProductPhotoRepository productPhotoRepository;
    @Mock private FileStorageService fileStorageService;

    @InjectMocks
    private TryOnServiceImpl tryOnService;

    private Account member;
    private Product top;
    private Product bottom;
    private Product dress;

    @BeforeEach
    void setUp() {
        member = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();

        top = Product.builder().id("Q7-AK-000001").name("Áo thun trắng")
                .currentPrice(new BigDecimal("150000")).status(ProductStatus.ON_SALE).build();
        bottom = Product.builder().id("Q7-QU-000002").name("Quần jeans")
                .currentPrice(new BigDecimal("250000")).status(ProductStatus.SOLD).build();
        dress = Product.builder().id("Q7-DA-000003").name("Đầm hoa")
                .currentPrice(new BigDecimal("300000")).status(ProductStatus.ON_SALE).build();
    }

    private TryOnImage outfitImage() {
        return TryOnImage.builder()
                .id(UUID.randomUUID())
                .member(member)
                .modelType(TryOnModelType.FEMALE)
                .topProduct(top)
                .bottomProduct(bottom)
                .imageUrl(IMAGE_URL)
                .createdAt(Instant.parse("2026-10-30T10:00:00Z"))
                .build();
    }

    private ProductPhoto photo(Product p, String url) {
        return ProductPhoto.builder().product(p).imageUrl(url).displayOrder(0).build();
    }

    // ==================== F-080: getCollection ====================

    @Test
    @DisplayName("getCollection: map đúng ảnh, người mẫu và thông tin áo + quần")
    void getCollection_success() {
        TryOnImage image = outfitImage();
        when(tryOnImageRepository.findByMember_Id(eq(member.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(image)));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(photo(top, "https://img/top.jpg"), photo(bottom, "https://img/bottom.jpg")));

        PageResponse<TryOnImageResponse> res = tryOnService.getCollection(member, 0, 20);

        assertEquals(1, res.getContent().size());
        TryOnImageResponse r = res.getContent().get(0);
        assertEquals(image.getId(), r.getId());
        assertEquals(TryOnModelType.FEMALE, r.getModelType());
        assertEquals(IMAGE_URL, r.getImageUrl());
        assertEquals(image.getCreatedAt(), r.getCreatedAt());

        assertEquals("Q7-AK-000001", r.getTop().getId());
        assertEquals("Áo thun trắng", r.getTop().getName());
        assertEquals("https://img/top.jpg", r.getTop().getThumbnailUrl());
        assertEquals(new BigDecimal("150000"), r.getTop().getCurrentPrice());
        assertEquals(ProductStatus.ON_SALE, r.getTop().getStatus());

        assertEquals("Q7-QU-000002", r.getBottom().getId());
        assertEquals(ProductStatus.SOLD, r.getBottom().getStatus());
        assertNull(r.getDress());
    }

    @Test
    @DisplayName("getCollection: ảnh thử Đầm -> top/bottom null, dress có giá trị")
    void getCollection_dressImage() {
        TryOnImage image = TryOnImage.builder()
                .id(UUID.randomUUID()).member(member).modelType(TryOnModelType.MALE)
                .dressProduct(dress).imageUrl(IMAGE_URL).build();
        when(tryOnImageRepository.findByMember_Id(eq(member.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(image)));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of(photo(dress, "https://img/dress.jpg")));

        TryOnImageResponse r = tryOnService.getCollection(member, 0, 20).getContent().get(0);

        assertNull(r.getTop());
        assertNull(r.getBottom());
        assertEquals("Q7-DA-000003", r.getDress().getId());
        assertEquals("https://img/dress.jpg", r.getDress().getThumbnailUrl());
    }

    @Test
    @DisplayName("getCollection: món không có ảnh -> thumbnailUrl null, không lỗi")
    void getCollection_productWithoutPhoto() {
        when(tryOnImageRepository.findByMember_Id(eq(member.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(outfitImage())));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection()))
                .thenReturn(List.of());

        TryOnImageResponse r = tryOnService.getCollection(member, 0, 20).getContent().get(0);

        assertNull(r.getTop().getThumbnailUrl());
        assertNull(r.getBottom().getThumbnailUrl());
    }

    @Test
    @DisplayName("getCollection: bộ sưu tập rỗng -> danh sách rỗng, không query ảnh sản phẩm")
    void getCollection_empty() {
        when(tryOnImageRepository.findByMember_Id(eq(member.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<TryOnImageResponse> res = tryOnService.getCollection(member, 0, 20);

        assertTrue(res.getContent().isEmpty());
        verifyNoInteractions(productPhotoRepository);
    }

    @Test
    @DisplayName("getCollection: page âm -> 0, size < 1 -> 20, sắp theo createdAt giảm dần")
    void getCollection_pagingDefaults() {
        when(tryOnImageRepository.findByMember_Id(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        tryOnService.getCollection(member, -3, 0);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(tryOnImageRepository).findByMember_Id(eq(member.getId()), captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(20, pageable.getPageSize());
        assertNotNull(pageable.getSort().getOrderFor("createdAt"));
        assertTrue(pageable.getSort().getOrderFor("createdAt").isDescending());
    }

    @Test
    @DisplayName("getCollection: size vượt trần -> bị chặn ở 50")
    void getCollection_sizeCapped() {
        when(tryOnImageRepository.findByMember_Id(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        tryOnService.getCollection(member, 2, 1000);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(tryOnImageRepository).findByMember_Id(eq(member.getId()), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(50, captor.getValue().getPageSize());
    }

    // ==================== F-081: deleteImage ====================

    @Test
    @DisplayName("deleteImage: chủ ảnh -> xóa dòng DB rồi xóa file")
    void deleteImage_success() {
        TryOnImage image = outfitImage();
        when(tryOnImageRepository.findById(image.getId())).thenReturn(Optional.of(image));
        when(fileStorageService.deleteByUrl(eq(IMAGE_URL), anyString())).thenReturn(true);

        tryOnService.deleteImage(member, image.getId());

        var order = inOrder(tryOnImageRepository, fileStorageService);
        order.verify(tryOnImageRepository).delete(image);
        order.verify(tryOnImageRepository).flush();
        order.verify(fileStorageService).deleteByUrl(eq(IMAGE_URL), eq("image/png"));
    }

    @Test
    @DisplayName("deleteImage: không có ảnh -> TRYON_IMAGE_NOT_FOUND, không xóa gì")
    void deleteImage_notFound() {
        UUID id = UUID.randomUUID();
        when(tryOnImageRepository.findById(id)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> tryOnService.deleteImage(member, id));

        assertEquals(ErrorCode.TRYON_IMAGE_NOT_FOUND, ex.getErrorCode());
        verify(tryOnImageRepository, never()).delete(any());
        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("deleteImage: ảnh của Member khác -> TRYON_IMAGE_FORBIDDEN, không xóa DB, không xóa file")
    void deleteImage_notOwner() {
        TryOnImage image = outfitImage();
        Account other = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
        when(tryOnImageRepository.findById(image.getId())).thenReturn(Optional.of(image));

        AppException ex = assertThrows(AppException.class, () -> tryOnService.deleteImage(other, image.getId()));

        assertEquals(ErrorCode.TRYON_IMAGE_NOT_FOUND, ex.getErrorCode());
        verify(tryOnImageRepository, never()).delete(any());
        verifyNoInteractions(fileStorageService);
    }

    @Test
    @DisplayName("deleteImage: storage báo không thấy file -> vẫn xóa dòng DB thành công")
    void deleteImage_fileAlreadyGone() {
        TryOnImage image = outfitImage();
        when(tryOnImageRepository.findById(image.getId())).thenReturn(Optional.of(image));
        when(fileStorageService.deleteByUrl(eq(IMAGE_URL), anyString())).thenReturn(false);

        assertDoesNotThrow(() -> tryOnService.deleteImage(member, image.getId()));

        verify(tryOnImageRepository).delete(image);
    }

    @Test
    @DisplayName("deleteImage: storage ném lỗi khi xóa file -> không làm hỏng việc xóa dòng DB")
    void deleteImage_storageFailureIsSwallowed() {
        TryOnImage image = outfitImage();
        when(tryOnImageRepository.findById(image.getId())).thenReturn(Optional.of(image));
        when(fileStorageService.deleteByUrl(eq(IMAGE_URL), anyString()))
                .thenThrow(new AppException(ErrorCode.FILE_DELETE_FAILED));

        assertDoesNotThrow(() -> tryOnService.deleteImage(member, image.getId()));

        verify(tryOnImageRepository).delete(image);
    }
}