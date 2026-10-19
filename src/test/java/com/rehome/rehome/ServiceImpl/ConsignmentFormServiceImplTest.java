package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.response.ConsignmentFormResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.Brand;
import com.rehome.rehome.entity.marketplace.Category;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentFormPhoto;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.mapper.ConsignmentFormMapper;
import com.rehome.rehome.repository.BrandRepository;
import com.rehome.rehome.repository.CategoryRepository;
import com.rehome.rehome.repository.ConsignmentFormPhotoRepository;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
}