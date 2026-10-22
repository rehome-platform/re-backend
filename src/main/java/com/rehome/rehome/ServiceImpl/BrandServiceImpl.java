package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.request.CreateBrandRequest;
import com.rehome.rehome.dto.request.UpdateBrandRequest;
import com.rehome.rehome.dto.response.BrandResponse;
import com.rehome.rehome.dto.response.CatalogStatusResponse;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.marketplace.Brand;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.BrandRepository;
import com.rehome.rehome.service.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BrandServiceImpl implements BrandService {

    private final BrandRepository brandRepository;

    @Override
    @Transactional
    public BrandResponse create(CreateBrandRequest request) {
        String name = request.getName().trim().replaceAll("\\s+", " ");

        if (brandRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(ErrorCode.BRAND_NAME_ALREADY_EXISTS);           // 409
        }

        Brand brand = brandRepository.save(Brand.builder()
                .name(name)
                .status(CatalogStatus.ACTIVE)
                .build());

        return new BrandResponse(brand.getId(), brand.getName(), brand.getStatus());
    }

    @Override
    @Transactional
    public BrandResponse update(UUID id, UpdateBrandRequest request) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));              // 404

        String name = request.getName().trim().replaceAll("\\s+", " ");

        if (brandRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new AppException(ErrorCode.BRAND_NAME_ALREADY_EXISTS);                      // 409
        }

        brand.setName(name);
        if (request.getStatus() != null) {
            brand.setStatus(request.getStatus());
        }
        brandRepository.save(brand);

        return new BrandResponse(brand.getId(), brand.getName(), brand.getStatus());
    }

    @Override
    @Transactional
    public CatalogStatusResponse hide(UUID id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND));      // 404

        if (brand.getStatus() != CatalogStatus.HIDDEN) {
            brand.setStatus(CatalogStatus.HIDDEN);
            brandRepository.save(brand);
        }

        return new CatalogStatusResponse(brand.getStatus());
    }
}