package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.response.ConsignmentFormDetailResponse;
import com.rehome.rehome.dto.response.ConsignmentFormListItemResponse;
import com.rehome.rehome.dto.response.ConsignmentFormResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.marketplace.Brand;
import com.rehome.rehome.entity.marketplace.Category;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentFormPhoto;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ConsignmentFormMapper;
import com.rehome.rehome.repository.BrandRepository;
import com.rehome.rehome.repository.CategoryRepository;
import com.rehome.rehome.repository.ConsignmentFormPhotoRepository;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsignmentFormServiceImplTest {

    @Mock private ConsignmentFormRepository consignmentFormRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ConsignmentFormPhotoRepository photoRepository;
    @Mock private ConsignmentFormMapper consignmentFormMapper;

    @InjectMocks private ConsignmentFormServiceImpl service;

    private final UUID categoryId = UUID.randomUUID();
    private final UUID brandId = UUID.randomUUID();
    private final UUID warehouseId = UUID.randomUUID();

    private ConsignmentFormRequest request() {
        ConsignmentFormRequest r = new ConsignmentFormRequest();
        r.setCategoryId(categoryId);
        r.setBrandId(brandId);
        r.setWarehouseId(warehouseId);
        r.setPhotoUrls(List.of("https://img/1.jpg", "https://img/2.jpg"));
        return r;
    }

    @Test
    void create_success_setsRelationsSavesFormAndPhotos() {
        Account account = Account.builder().id(UUID.randomUUID()).build();
        ConsignmentFormRequest req = request();

        ConsignmentForm mapped = new ConsignmentForm();
        ConsignmentForm saved = new ConsignmentForm();           // save() trả về instance khác
        Category category = new Category();
        Brand brand = new Brand();
        Warehouse warehouse = new Warehouse();
        List<ConsignmentFormPhoto> photos = List.of(new ConsignmentFormPhoto(), new ConsignmentFormPhoto());
        ConsignmentFormResponse response = new ConsignmentFormResponse();

        when(consignmentFormMapper.toEntity(req)).thenReturn(mapped);
        when(categoryRepository.getReferenceById(categoryId)).thenReturn(category);
        when(brandRepository.getReferenceById(brandId)).thenReturn(brand);
        when(warehouseRepository.getReferenceById(warehouseId)).thenReturn(warehouse);
        when(consignmentFormRepository.save(any(ConsignmentForm.class))).thenReturn(saved);
        when(consignmentFormMapper.toPhotoEntities(saved, req.getPhotoUrls())).thenReturn(photos);
        when(consignmentFormMapper.toResponse(saved)).thenReturn(response);

        ConsignmentFormResponse result = service.create(req, account);

        assertThat(result).isSameAs(response);

        // entity đưa vào save() phải đã được gắn đủ quan hệ
        ArgumentCaptor<ConsignmentForm> captor = ArgumentCaptor.forClass(ConsignmentForm.class);
        verify(consignmentFormRepository).save(captor.capture());
        ConsignmentForm toSave = captor.getValue();
        assertThat(toSave).isSameAs(mapped);
        assertThat(toSave.getCategory()).isSameAs(category);
        assertThat(toSave.getBrand()).isSameAs(brand);
        assertThat(toSave.getWarehouse()).isSameAs(warehouse);
        assertThat(toSave.getMember()).isSameAs(account);

        // phải lưu form trước rồi mới lưu ảnh, và ảnh được map từ form đã save
        InOrder order = inOrder(consignmentFormRepository, photoRepository);
        order.verify(consignmentFormRepository).save(mapped);
        order.verify(photoRepository).saveAll(photos);
    }

    @Test
    void create_categoryNotFound_propagatesAndSavesNothing() {
        Account account = Account.builder().id(UUID.randomUUID()).build();
        ConsignmentFormRequest req = request();

        when(consignmentFormMapper.toEntity(req)).thenReturn(new ConsignmentForm());
        when(categoryRepository.getReferenceById(categoryId)).thenThrow(new EntityNotFoundException("category"));

        assertThatThrownBy(() -> service.create(req, account))
                .isInstanceOf(EntityNotFoundException.class);

        verify(consignmentFormRepository, never()).save(any());
        verifyNoInteractions(photoRepository);
    }

    private Account member() {
        return Account.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void getMyForms_withStatus_usesStatusQueryAndMapsItems() {
        Account account = member();
        ConsignmentForm form = new ConsignmentForm();
        ConsignmentFormListItemResponse item = new ConsignmentFormListItemResponse(
                UUID.randomUUID(), ConsignmentFormStatus.PENDING, "Áo sơ mi Zara", new BigDecimal("250000"));

        when(consignmentFormRepository.findByMemberIdAndStatus(eq(account.getId()),
                eq(ConsignmentFormStatus.PENDING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(form), PageRequest.of(0, 20), 1));
        when(consignmentFormMapper.toListItem(form)).thenReturn(item);

        PageResponse<ConsignmentFormListItemResponse> result =
                service.getMyForms(account, ConsignmentFormStatus.PENDING, 0, 20);

        assertThat(result.getContent()).containsExactly(item);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        verify(consignmentFormRepository, never()).findByMemberId(any(), any());
    }

    @Test
    void getMyForms_withoutStatus_usesMemberQuery_sortedNewestFirst() {
        Account account = member();
        when(consignmentFormRepository.findByMemberId(eq(account.getId()), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getMyForms(account, null, 0, 20);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(consignmentFormRepository).findByMemberId(eq(account.getId()), captor.capture());

        Sort.Order order = captor.getValue().getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
        verify(consignmentFormRepository, never()).findByMemberIdAndStatus(any(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"-5, 0, 20", "0, 0, 20", "0, 1000, 50", "3, 10, 10"})
    void getMyForms_normalizesPageAndSize(int page, int size, int expectedSize) {
        Account account = member();
        when(consignmentFormRepository.findByMemberId(eq(account.getId()), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.getMyForms(account, null, page, size);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(consignmentFormRepository).findByMemberId(eq(account.getId()), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(expectedSize);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(Math.max(page, 0));
    }

    private ConsignmentForm formOf(Account owner, ConsignmentFormStatus status) {
        return ConsignmentForm.builder()
                .id(UUID.randomUUID())
                .member(owner)
                .status(status)
                .productName("Áo sơ mi Zara trắng")
                .rejectReason("Ảnh không rõ tình trạng sản phẩm")
                .build();
    }

    private void assertAppError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(expected));
    }

    @Test
    void getMyForm_rejected_returnsDetailWithoutReceipt() {
        Account owner = member();
        ConsignmentForm form = formOf(owner, ConsignmentFormStatus.REJECTED);
        ConsignmentFormDetailResponse dto = new ConsignmentFormDetailResponse(
                form.getId(), ConsignmentFormStatus.REJECTED, form.getProductName(),
                form.getRejectReason(), null);

        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));
        when(consignmentFormMapper.toDetail(form)).thenReturn(dto);

        assertThat(service.getMyForm(owner, form.getId())).isSameAs(dto);
    }

    @Test
    void getMyForm_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(consignmentFormRepository.findById(id)).thenReturn(Optional.empty());

        assertAppError(() -> service.getMyForm(member(), id), ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);

        verify(consignmentFormRepository).findById(id);
        verifyNoInteractions(consignmentFormMapper);
    }

    @Test
    void getMyForm_ownedByAnotherMember_throws403() {
        Account owner = member();
        Account other = member(); // phải có id khác owner
        ConsignmentForm form = formOf(owner, ConsignmentFormStatus.PENDING);
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));

        assertAppError(() -> service.getMyForm(other, form.getId()), ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);

        verifyNoInteractions(consignmentFormMapper);
    }

    @Test
    void getMyForm_owner_returnsMappedDetail() {
        Account owner = member();
        ConsignmentForm form = formOf(owner, ConsignmentFormStatus.APPROVED);
        ConsignmentFormDetailResponse dto = new ConsignmentFormDetailResponse(
                form.getId(), ConsignmentFormStatus.APPROVED, form.getProductName(),
                null, null);

        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));
        when(consignmentFormMapper.toDetail(form)).thenReturn(dto);

        ConsignmentFormDetailResponse result = service.getMyForm(owner, form.getId());

        assertThat(result).isSameAs(dto);
        verify(consignmentFormMapper).toDetail(form);
    }
}