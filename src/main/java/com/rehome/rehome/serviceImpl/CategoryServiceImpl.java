package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.config.CodeGenerator;
import com.rehome.rehome.dto.request.CreateCategoryRequest;
import com.rehome.rehome.dto.request.UpdateCategoryRequest;
import com.rehome.rehome.dto.response.CatalogStatusResponse;
import com.rehome.rehome.dto.response.CategoryResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.marketplace.Category;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.CategoryRepository;
import com.rehome.rehome.service.CategoryService;
import com.rehome.rehome.util.CatalogStatusParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public CategoryResponse create(CreateCategoryRequest request) {
        String name = normalize(request.getName());

        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new AppException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);           // 409
        }

        String code = CodeGenerator.makeUnique(
                CodeGenerator.fromName(name),
                categoryRepository::existsByCode);

        Category category = categoryRepository.save(Category.builder()
                .name(name)
                .code(code)
                .tryonSlot(request.getTryonSlot())
                .status(CatalogStatus.ACTIVE)
                .build());

        return toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse update(UUID id, UpdateCategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));   // 404

        String name = normalize(request.getName());

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw new AppException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);           // 409
        }

        category.setName(name);
        category.setTryonSlot(request.getTryonSlot());
        if (request.getStatus() != null) {
            category.setStatus(request.getStatus());
        }
        categoryRepository.save(category);

        return toResponse(category);
    }

    private String normalize(String name) {
        return name.trim().replaceAll("\\s+", " ");
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getTryonSlot(), c.getStatus());
    }

    @Override
    @Transactional
    public CatalogStatusResponse hide(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));   // 404

        // Idempotent: đã HIDDEN rồi thì gọi lại vẫn trả 200
        if (category.getStatus() != CatalogStatus.HIDDEN) {
            category.setStatus(CatalogStatus.HIDDEN);
            categoryRepository.save(category);
        }

        return new CatalogStatusResponse(category.getStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> list(String status, Account account) {
        CatalogStatus s = CatalogStatusParser.parse(status, account);
        return categoryRepository.findByStatusOrderByNameAsc(s).stream()
                .map(this::toResponse)
                .toList();
    }
}