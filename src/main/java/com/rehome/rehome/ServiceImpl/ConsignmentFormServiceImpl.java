package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.response.ConsignmentFormResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.mapper.ConsignmentFormMapper;
import com.rehome.rehome.repository.*;
import com.rehome.rehome.service.ConsignmentFormService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ConsignmentFormServiceImpl implements ConsignmentFormService {
    private final ConsignmentFormRepository consignmentFormRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final WarehouseRepository warehouseRepository;
    private final ConsignmentFormPhotoRepository photoRepository;
    private final ConsignmentFormMapper consignmentFormMapper;

    @Override
    @Transactional
    public ConsignmentFormResponse create(ConsignmentFormRequest request, Account account) {
        ConsignmentForm cf = consignmentFormMapper.toEntity(request);

        cf.setCategory(categoryRepository.getReferenceById(request.getCategoryId()));
        cf.setBrand(brandRepository.getReferenceById(request.getBrandId()));
        cf.setWarehouse(warehouseRepository.getReferenceById(request.getWarehouseId()));
        cf.setMember(account); // đã có sẵn từ token, không cần query

        cf = consignmentFormRepository.save(cf);
        photoRepository.saveAll(consignmentFormMapper.toPhotoEntities(cf, request.getPhotoUrls()));

        return consignmentFormMapper.toResponse(cf);
    }
}
