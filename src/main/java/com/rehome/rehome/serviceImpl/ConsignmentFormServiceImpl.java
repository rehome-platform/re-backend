package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.config.ConsignmentProperties;
import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.request.RejectConsignmentFormRequest;
import com.rehome.rehome.dto.request.SubmitConsignmentFormsRequest;
import com.rehome.rehome.dto.request.ReturnOnsiteRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentFormPhoto;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ConsignmentFormMapper;
import com.rehome.rehome.repository.*;
import com.rehome.rehome.service.ConsignmentFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final ConsignmentProperties consignmentProperties;
    private final AccountRepository accountRepository;
    private final QueueService queueService;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    // ---------------------------------------------------------------- Member

    @Override
    @Transactional
    public ConsignmentFormResponse create(ConsignmentFormRequest request, Account account) {

        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_CREATE_FORM);
        }

        // Khoá dòng Member rồi đếm, để hai request song song không cùng lọt qua giới hạn
        lockMember(account);
        if (pendingCount(account) >= consignmentProperties.getMaxPendingForms()) {
            throw new AppException(ErrorCode.TOO_MANY_PENDING_FORMS);                             // 409
        }

        ConsignmentForm cf = consignmentFormMapper.toEntity(request);

        cf.setCategory(categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND)));
        cf.setBrand(brandRepository.findById(request.getBrandId())
                .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND)));
        cf.setWarehouse(warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND)));
        cf.setMember(account);
        cf.setFormNo(String.format("KG-%06d", consignmentFormRepository.nextFormNo()));

        cf = consignmentFormRepository.save(cf);
        photoRepository.saveAll(consignmentFormMapper.toPhotoEntities(cf, request.getPhotoUrls()));

        queueService.enqueue(QueueType.FORM_REVIEW, cf.getId().toString(), cf.getWarehouse());

        return consignmentFormMapper.toResponse(cf);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ConsignmentFormListItemResponse> getMyForms(
            Account account, ConsignmentFormStatus status, int page, int size) {

        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }

        int safePage = Math.max(page, 0);
        int safeSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ConsignmentForm> result = (status == null)
                ? consignmentFormRepository.findByMemberIdAndStatusNot(
                        account.getId(), ConsignmentFormStatus.DRAFT, pageable)
                : consignmentFormRepository.findByMemberIdAndStatus(account.getId(), status, pageable);

        // Một query lấy ảnh đầu của cả trang, tránh N+1
        Map<UUID, String> thumbnails = loadThumbnails(result.getContent());

        return PageResponse.of(result.map(
                f -> consignmentFormMapper.toListItem(f, thumbnails.get(f.getId()))));
    }

    @Override
    @Transactional(readOnly = true)
    public ConsignmentFormDetailResponse getMyForm(Account account, UUID id) {
        ConsignmentForm form = consignmentFormRepository.findWithDetailsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));

        if (!form.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);
        }

        return buildDetail(form);
    }

    // PUT /consignment-forms/{id}: chỉ khi PENDING, không đổi kho
    @Override
    @Transactional
    public ConsignmentFormDetailResponse update(UUID id, ConsignmentFormRequest request, Account account) {

        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }

        // Khóa dòng để không đè lên lúc Moderator đang duyệt/từ chối
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));      // 404

        if (!form.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);                         // 403
        }
        if (form.getStatus() != ConsignmentFormStatus.PENDING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);                       // 409
        }

        consignmentFormMapper.applyUpdate(form, request);

        // Không đổi kho: bỏ qua warehouseId trong body
        if (request.getCategoryId() != null
                && !request.getCategoryId().equals(form.getCategory().getId())) {
            form.setCategory(categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND)));
        }
        if (request.getBrandId() != null
                && !request.getBrandId().equals(form.getBrand().getId())) {
            form.setBrand(brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND)));
        }

        // photoUrls null hoặc rỗng = giữ ảnh cũ; có giá trị = thay toàn bộ
        if (request.getPhotoUrls() != null && !request.getPhotoUrls().isEmpty()) {
            photoRepository.deleteByForm_Id(id);
            photoRepository.saveAll(consignmentFormMapper.toPhotoEntities(form, request.getPhotoUrls()));
        }

        consignmentFormRepository.save(form);
        return buildDetail(form);
    }

    // ------------------------------------------------------- Nháp ("Tủ đồ")

    // POST /consignment-forms/drafts: chưa cần kho, không tính vào giới hạn chờ duyệt
    @Override
    @Transactional
    public ConsignmentFormDetailResponse createDraft(ConsignmentFormRequest request, Account account) {
        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_CREATE_FORM);
        }
        validateDraftContent(request);

        ConsignmentForm cf = consignmentFormMapper.toEntity(request);
        cf.setStatus(ConsignmentFormStatus.DRAFT);
        cf.setCategory(categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND)));
        cf.setBrand(brandRepository.findById(request.getBrandId())
                .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND)));
        cf.setMember(account);
        // warehouse và formNo để trống tới lúc gửi

        cf = consignmentFormRepository.save(cf);
        photoRepository.saveAll(consignmentFormMapper.toPhotoEntities(cf, request.getPhotoUrls()));
        return buildDetail(cf);
    }

    // PUT /consignment-forms/drafts/{id}: bỏ qua warehouseId trong body
    @Override
    @Transactional
    public ConsignmentFormDetailResponse updateDraft(UUID id, ConsignmentFormRequest request, Account account) {
        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }
        ConsignmentForm form = loadOwnDraftForUpdate(id, account);
        validateDraftContent(request);

        consignmentFormMapper.applyUpdate(form, request);

        if (!request.getCategoryId().equals(form.getCategory().getId())) {
            form.setCategory(categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND)));
        }
        if (!request.getBrandId().equals(form.getBrand().getId())) {
            form.setBrand(brandRepository.findById(request.getBrandId())
                    .orElseThrow(() -> new AppException(ErrorCode.BRAND_NOT_FOUND)));
        }

        // photoUrls null hoặc rỗng = giữ ảnh cũ; có giá trị = thay toàn bộ
        if (request.getPhotoUrls() != null && !request.getPhotoUrls().isEmpty()) {
            photoRepository.deleteByForm_Id(id);
            photoRepository.saveAll(consignmentFormMapper.toPhotoEntities(form, request.getPhotoUrls()));
        }

        consignmentFormRepository.save(form);
        return buildDetail(form);
    }

    // DELETE /consignment-forms/drafts/{id}: xoá hẳn nháp và ảnh
    @Override
    @Transactional
    public void deleteDraft(UUID id, Account account) {
        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }
        ConsignmentForm form = loadOwnDraftForUpdate(id, account);
        photoRepository.deleteByForm_Id(id);
        consignmentFormRepository.delete(form);
    }

    // POST /consignment-forms/submit: DRAFT -> PENDING, trả kết quả từng món
    @Override
    @Transactional
    public SubmitConsignmentFormsResponse submitDrafts(SubmitConsignmentFormsRequest request, Account account) {
        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_CREATE_FORM);
        }

        Warehouse warehouse = warehouseRepository.findById(request.getWarehouseId())
                .orElseThrow(() -> new AppException(ErrorCode.WAREHOUSE_NOT_FOUND));

        lockMember(account);
        long pending = pendingCount(account);
        int max = consignmentProperties.getMaxPendingForms();

        List<SubmitConsignmentFormsResponse.Result> results = new ArrayList<>();
        int success = 0;

        // LinkedHashSet: bỏ id trùng nhưng giữ thứ tự Member đã chọn
        for (UUID formId : new LinkedHashSet<>(request.getFormIds())) {
            ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(formId).orElse(null);

            ErrorCode error = null;
            if (form == null) {
                error = ErrorCode.CONSIGNMENT_FORM_NOT_FOUND;
            } else if (!form.getMember().getId().equals(account.getId())) {
                error = ErrorCode.CONSIGNMENT_FORM_FORBIDDEN;
            } else if (form.getStatus() != ConsignmentFormStatus.DRAFT) {
                error = ErrorCode.CONSIGNMENT_FORM_NOT_DRAFT;
            } else if (pending >= max) {
                error = ErrorCode.TOO_MANY_PENDING_FORMS;
            }

            if (error != null) {
                results.add(SubmitConsignmentFormsResponse.Result.builder()
                        .formId(formId)
                        .success(false)
                        .status(form != null && form.getMember().getId().equals(account.getId())
                                ? form.getStatus() : null)
                        .errorCode(error.name())
                        .message(error.getDefaultMessage())
                        .build());
                continue;
            }

            form.setWarehouse(warehouse);
            form.setStatus(ConsignmentFormStatus.PENDING);
            form.setFormNo(String.format("KG-%06d", consignmentFormRepository.nextFormNo()));
            form.setBringInDeadline(null);
            form.setRejectReason(null);
            consignmentFormRepository.save(form);
            queueService.enqueue(QueueType.FORM_REVIEW, form.getId().toString(), warehouse);

            pending++;
            success++;
            results.add(SubmitConsignmentFormsResponse.Result.builder()
                    .formId(formId)
                    .success(true)
                    .status(form.getStatus())
                    .formNo(form.getFormNo())
                    .build());
        }

        return SubmitConsignmentFormsResponse.builder()
                .warehouseId(warehouse.getId())
                .successCount(success)
                .failureCount(results.size() - success)
                .results(results)
                .build();
    }

    // ------------------------------------------------------------- Moderator

    @Override
    @Transactional
    public ConsignmentFormResponse approve(UUID id) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));   // 404

        if (form.getStatus() != ConsignmentFormStatus.PENDING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);                    // 409
        }

        form.setStatus(ConsignmentFormStatus.APPROVED);
        queueService.dequeue(QueueType.FORM_REVIEW, form.getId().toString());
        form.setBringInDeadline(
                LocalDate.now(VN_ZONE).plusDays(consignmentProperties.getBringInDeadlineDays()));
        form.setRejectReason(null);

        return consignmentFormMapper.toResponse(consignmentFormRepository.save(form));
    }

    @Override
    @Transactional
    public void startInspection(UUID id) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));   // 404

        if (form.getStatus() != ConsignmentFormStatus.APPROVED) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_APPROVED);                   // 409
        }

        form.setStatus(ConsignmentFormStatus.INSPECTING);
        consignmentFormRepository.save(form);
        queueService.enqueue(QueueType.INSPECTION, form.getId().toString(), form.getWarehouse());
    }

    @Override
    @Transactional
    public ConsignmentFormStatusResponse reject(UUID id, RejectConsignmentFormRequest request) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));   // 404

        if (form.getStatus() != ConsignmentFormStatus.PENDING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);                    // 409
        }

        form.setStatus(ConsignmentFormStatus.REJECTED);
        queueService.dequeue(QueueType.FORM_REVIEW, form.getId().toString());
        form.setRejectReason(request.getReason().trim());
        form.setBringInDeadline(null);

        consignmentFormRepository.save(form);
        return new ConsignmentFormStatusResponse(form.getId(), form.getStatus());
    }

    @Override
    @Transactional
    public ConsignmentFormStatusResponse returnOnsite(UUID id, ReturnOnsiteRequest request) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));   // 404

        if (form.getStatus() != ConsignmentFormStatus.INSPECTING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_INSPECTING);                 // 409
        }

        form.setStatus(ConsignmentFormStatus.REJECTED);
        queueService.dequeue(QueueType.INSPECTION, form.getId().toString());
        form.setRejectReason(request.getReason().trim());
        form.setBringInDeadline(null);

        consignmentFormRepository.save(form);
        return new ConsignmentFormStatusResponse(form.getId(), form.getStatus());
    }

    // --------------------------------------------------------------- helpers

    private void lockMember(Account account) {
        accountRepository.findByIdForUpdate(account.getId())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private long pendingCount(Account account) {
        return consignmentFormRepository.countByMember_IdAndStatus(account.getId(), ConsignmentFormStatus.PENDING);
    }

    private ConsignmentForm loadOwnDraftForUpdate(UUID id, Account account) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));      // 404
        if (!form.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);                         // 403
        }
        if (form.getStatus() != ConsignmentFormStatus.DRAFT) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_DRAFT);                         // 409
        }
        return form;
    }

    // Nháp chỉ được thiếu kho; các cột còn lại của bảng đều NOT NULL nên vẫn phải đủ
    private void validateDraftContent(ConsignmentFormRequest request) {
        if (request.getCategoryId() == null) {
            throw new BadRequestException("categoryId không được để trống");
        }
        if (request.getBrandId() == null) {
            throw new BadRequestException("brandId không được để trống");
        }
        if (request.getProductName() == null || request.getProductName().isBlank()) {
            throw new BadRequestException("productName không được để trống");
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new BadRequestException("description không được để trống");
        }
        if (request.getDesiredPrice() == null || request.getDesiredPrice().signum() <= 0) {
            throw new BadRequestException("desiredPrice phải lớn hơn 0");
        }
    }

    // Ảnh đầu tiên (theo displayOrder) của mỗi form, lấy bằng một query cho cả trang
    private Map<UUID, String> loadThumbnails(List<ConsignmentForm> forms) {
        if (forms.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = forms.stream().map(ConsignmentForm::getId).toList();
        return photoRepository.findByForm_IdInOrderByDisplayOrderAscIdAsc(ids).stream()
                .collect(Collectors.toMap(
                        p -> p.getForm().getId(),
                        ConsignmentFormPhoto::getImageUrl,
                        (first, second) -> first));
    }

    // Phải gọi trong transaction vì đọc các quan hệ lazy của form
    private ConsignmentFormDetailResponse buildDetail(ConsignmentForm form) {
        List<String> photoUrls = consignmentFormMapper.toPhotoUrls(
                photoRepository.findByForm_IdOrderByDisplayOrderAscIdAsc(form.getId()));

        ConsignmentFormDetailResponse response = consignmentFormMapper.toDetail(form, photoUrls);

        consignmentReceiptRepository.findByConsignmentForm_Id(form.getId()).ifPresent(cr -> {
            response.setReceiptId(cr.getId());
            response.setReceiptConfirmed(cr.getConfirmedAt() != null);
        });

        return response;
    }
}