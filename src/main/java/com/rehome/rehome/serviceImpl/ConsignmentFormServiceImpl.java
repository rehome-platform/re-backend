package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.config.ConsignmentProperties;
import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.request.RejectConsignmentFormRequest;
import com.rehome.rehome.dto.request.ReturnOnsiteRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ConsignmentFormMapper;
import com.rehome.rehome.repository.*;
import com.rehome.rehome.service.ConsignmentFormService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConsignmentFormServiceImpl implements ConsignmentFormService {
    private final ConsignmentFormRepository consignmentFormRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final WarehouseRepository warehouseRepository;
    private final ConsignmentFormPhotoRepository photoRepository;
    private final ConsignmentFormMapper consignmentFormMapper;
    private final ConsignmentReceiptRepository consignmentReceiptRepository;
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ConsignmentProperties consignmentProperties;

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    @Override
    @Transactional
    public ConsignmentFormResponse create(ConsignmentFormRequest request, Account account) {

        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_CREATE_FORM);
        }

        ConsignmentForm cf = consignmentFormMapper.toEntity(request);

        cf.setCategory(categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND)));
        cf.setBrand(brandRepository.findById(request.getBrandId())
                .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND)));
        cf.setWarehouse(warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND)));
        cf.setMember(account);

        cf = consignmentFormRepository.save(cf);
        photoRepository.saveAll(consignmentFormMapper.toPhotoEntities(cf, request.getPhotoUrls()));

        return consignmentFormMapper.toResponse(cf);
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public PageResponse<ConsignmentFormListItemResponse> getMyForms(
            Account account, ConsignmentFormStatus status, int page, int size) {

        if(account.getRole() != Role.MEMBER){
            throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }

        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ConsignmentForm> result = (status == null)
                ? consignmentFormRepository.findByMemberId(account.getId(), pageable)
                : consignmentFormRepository.findByMemberIdAndStatus(account.getId(), status, pageable);

        return PageResponse.of(result.map(consignmentFormMapper::toListItem));
    }

    @Override
    public ConsignmentFormDetailResponse getMyForm(Account account, UUID id) {
        ConsignmentForm form = consignmentFormRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));

        if (!form.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);
        }

        ConsignmentFormDetailResponse response = consignmentFormMapper.toDetail(form);

        consignmentReceiptRepository.findByConsignmentForm_Id(id)
                .ifPresent(cr -> response.setReceiptId(cr.getId()));

        return response;
    }
    @Override
    @Transactional
    public ConsignmentFormResponse approve(UUID id) {
        ConsignmentForm form = consignmentFormRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));   // 404

        if (form.getStatus() != ConsignmentFormStatus.PENDING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);                    // 409
        }

        form.setStatus(ConsignmentFormStatus.APPROVED);
        form.setBringInDeadline(
                LocalDate.now(VN_ZONE).plusDays(consignmentProperties.getBringInDeadlineDays()));
        form.setRejectReason(null);

        return consignmentFormMapper.toResponse(consignmentFormRepository.save(form));
    }

    @Override
    @Transactional
    public void startInspection(UUID id) {
        ConsignmentForm form = consignmentFormRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));   // 404

        if (form.getStatus() != ConsignmentFormStatus.APPROVED) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_APPROVED);                   // 409
        }

        form.setStatus(ConsignmentFormStatus.INSPECTING);
        consignmentFormRepository.save(form);
    }

    @Override
    @Transactional
    public ConsignmentFormStatusResponse reject(UUID id, RejectConsignmentFormRequest request) {
        ConsignmentForm form = consignmentFormRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));   // 404

        if (form.getStatus() != ConsignmentFormStatus.PENDING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);                    // 409
        }

        form.setStatus(ConsignmentFormStatus.REJECTED);
        form.setRejectReason(request.getReason().trim());
        form.setBringInDeadline(null);

        consignmentFormRepository.save(form);
        return new ConsignmentFormStatusResponse(form.getId(), form.getStatus());
    }

    @Override
    @Transactional
    public ConsignmentFormStatusResponse returnOnsite(UUID id, ReturnOnsiteRequest request) {
        ConsignmentForm form = consignmentFormRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));   // 404

        if (form.getStatus() != ConsignmentFormStatus.INSPECTING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_INSPECTING);   // 409
        }

        form.setStatus(ConsignmentFormStatus.REJECTED);
        form.setRejectReason(request.getReason().trim());
        form.setBringInDeadline(null);

        consignmentFormRepository.save(form);
        return new ConsignmentFormStatusResponse(form.getId(), form.getStatus());
    }
}
