package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.util.NotificationFormatting;


import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.entity.enums.WarehouseAuditGroup;
import com.rehome.rehome.constant.SystemConfigKeys;
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
import com.rehome.rehome.component.MembershipQuotaResolver;
import com.rehome.rehome.service.SystemConfigReader;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentFormPhoto;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Warehouse;
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
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConsignmentFormServiceImpl implements ConsignmentFormService {
    private final com.rehome.rehome.component.BusinessNotifications businessNotifications;

    private final AuditLogger auditLogger;
    private final ConsignmentFormRepository consignmentFormRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final WarehouseRepository warehouseRepository;
    private final ConsignmentFormPhotoRepository photoRepository;
    private final ConsignmentFormMapper consignmentFormMapper;
    private final ConsignmentReceiptRepository consignmentReceiptRepository;
    private final SystemConfigReader systemConfigReader;
    private final MembershipQuotaResolver membershipQuotaResolver;
    private final AccountRepository accountRepository;
    private final QueueService queueService;
    private final com.rehome.rehome.service.ContentModerationService moderation;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 50;

    @Override
    @Transactional
    public ConsignmentFormResponse create(ConsignmentFormRequest request, Account account) {
        moderation.check(account.getId(), "CONSIGNMENT_FORM", request.getProductName(), request.getDescription());

        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_CREATE_FORM);
        }

        validateCreateContent(request);

        lockMember(account);
        if (pendingCount(account) >= maxPendingForms(account)) {
            throw new AppException(ErrorCode.TOO_MANY_PENDING_FORMS);
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
    public ConsignmentFormPageResponse getMyForms(
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

        Map<UUID, String> thumbnails = loadThumbnails(result.getContent());
        Map<UUID, ConsignmentReceipt> latestReceipts = loadLatestReceipts(result.getContent());

        Map<UUID, String> productIds = result.isEmpty() ? Map.of()
                : productRepository.findProductIdsByFormIds(result.getContent().stream()
                        .map(ConsignmentForm::getId).toList()).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                ProductRepository.FormProductId::getFormId, ProductRepository.FormProductId::getProductId));

        PageResponse<ConsignmentFormListItemResponse> p = PageResponse.of(result.map(
                f -> consignmentFormMapper.toListItem(f, thumbnails.get(f.getId()), latestReceipts.get(f.getId()),
                        productIds.get(f.getId()))));

        return ConsignmentFormPageResponse.builder()
                .content(p.getContent())
                .page(p.getPage())
                .size(p.getSize())
                .totalElements(p.getTotalElements())
                .totalPages(p.getTotalPages())
                .pendingCount(pendingCount(account))
                .maxPending(maxPendingForms(account))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ConsignmentFormDetailResponse getMyForm(Account account, UUID id) {
        ConsignmentForm form = consignmentFormRepository.findWithDetailsById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));

        authorizeRead(account, form);

        return buildDetail(form);
    }

    @Override
    @Transactional
    public ConsignmentFormDetailResponse update(UUID id, ConsignmentFormRequest request, Account account) {
        moderation.check(account.getId(), "CONSIGNMENT_FORM", request.getProductName(), request.getDescription());

        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }

        validateDraftContent(request);

        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));

        if (!form.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);
        }
        if (form.getStatus() != ConsignmentFormStatus.PENDING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);
        }

        consignmentFormMapper.applyUpdate(form, request);

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

        if (request.getPhotoUrls() != null && !request.getPhotoUrls().isEmpty()) {
            photoRepository.deleteByForm_Id(id);
            photoRepository.saveAll(consignmentFormMapper.toPhotoEntities(form, request.getPhotoUrls()));
        }

        consignmentFormRepository.save(form);
        return buildDetail(form);
    }

    @Override
    @Transactional
    public ConsignmentFormDetailResponse createDraft(ConsignmentFormRequest request, Account account) {
        moderation.check(account.getId(), "CONSIGNMENT_FORM", request.getProductName(), request.getDescription());
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

        cf = consignmentFormRepository.save(cf);
        photoRepository.saveAll(consignmentFormMapper.toPhotoEntities(cf, request.getPhotoUrls()));
        return buildDetail(cf);
    }

    @Override
    @Transactional
    public ConsignmentFormDetailResponse updateDraft(UUID id, ConsignmentFormRequest request, Account account) {
        moderation.check(account.getId(), "CONSIGNMENT_FORM", request.getProductName(), request.getDescription());
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

        if (request.getPhotoUrls() != null && !request.getPhotoUrls().isEmpty()) {
            photoRepository.deleteByForm_Id(id);
            photoRepository.saveAll(consignmentFormMapper.toPhotoEntities(form, request.getPhotoUrls()));
        }

        consignmentFormRepository.save(form);
        return buildDetail(form);
    }

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
        int max = maxPendingForms(account);

        List<SubmitConsignmentFormsResponse.Result> results = new ArrayList<>();
        int success = 0;

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

            moderation.check(account.getId(), "CONSIGNMENT_FORM", form.getProductName(), form.getDescription());
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

    @Override
    @Transactional
    public ConsignmentFormResponse approve(UUID id, Account moderator) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));
        assertModeratorOwnsWarehouse(moderator, form);

        if (form.getStatus() != ConsignmentFormStatus.PENDING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);
        }

        form.setStatus(ConsignmentFormStatus.APPROVED);
        queueService.dequeue(QueueType.FORM_REVIEW, form.getId().toString());
        form.setBringInDeadline(
                LocalDate.now(VN_ZONE).plusDays(bringInDeadlineDays()));
        form.setRejectReason(null);

        ConsignmentForm saved = consignmentFormRepository.save(form);
        logForm(moderator, form, "APPROVE_FORM", "APPROVED");
        businessNotifications.form(form, "Yêu cầu ký gửi đã được duyệt", "vui lòng mang món đến địa điểm ký gửi trước hạn " + NotificationFormatting.deadline(form.getBringInDeadline()) + ".");
        return consignmentFormMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void startInspection(UUID id, Account moderator) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));
        assertModeratorOwnsWarehouse(moderator, form);

        if (form.getStatus() != ConsignmentFormStatus.APPROVED) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_APPROVED);
        }

        form.setStatus(ConsignmentFormStatus.INSPECTING);
        consignmentFormRepository.save(form);
        queueService.enqueue(QueueType.INSPECTION, form.getId().toString(), form.getWarehouse());
        logForm(moderator, form, "START_INSPECTION", "STARTED");
    }

    @Override
    @Transactional
    public ConsignmentFormStatusResponse reject(UUID id, RejectConsignmentFormRequest request, Account moderator) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));
        assertModeratorOwnsWarehouse(moderator, form);

        if (form.getStatus() != ConsignmentFormStatus.PENDING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);
        }

        form.setStatus(ConsignmentFormStatus.REJECTED);
        queueService.dequeue(QueueType.FORM_REVIEW, form.getId().toString());
        form.setRejectReason(request.getReason().trim());
        form.setBringInDeadline(null);

        consignmentFormRepository.save(form);
        logForm(moderator, form, "REJECT_FORM", "REJECTED");
        businessNotifications.form(form, "Yêu cầu ký gửi bị từ chối", "lý do: " + form.getRejectReason());
        return new ConsignmentFormStatusResponse(form.getId(), form.getStatus());
    }

    @Override
    @Transactional
    public ConsignmentFormStatusResponse returnOnsite(UUID id, ReturnOnsiteRequest request, Account moderator) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));
        assertModeratorOwnsWarehouse(moderator, form);

        if (form.getStatus() != ConsignmentFormStatus.INSPECTING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_INSPECTING);
        }

        form.setStatus(ConsignmentFormStatus.REJECTED);
        queueService.dequeue(QueueType.INSPECTION, form.getId().toString());
        form.setRejectReason(request.getReason().trim());
        form.setBringInDeadline(null);

        consignmentFormRepository.save(form);
        logForm(moderator, form, "RETURN_ONSITE", "RETURNED");
        businessNotifications.form(form, "Đã trả lại món tại quầy", "lý do: " + form.getRejectReason());
        return new ConsignmentFormStatusResponse(form.getId(), form.getStatus());
    }

    private void logForm(Account moderator, ConsignmentForm form, String action, String result) {
        auditLogger.logWarehouseOp(moderator, form.getWarehouse(), WarehouseAuditGroup.INSPECTION, action,
                "CONSIGNMENT_FORM", form.getId().toString(), result, form.getFormNo());
    }

    private void assertModeratorOwnsWarehouse(Account moderator, ConsignmentForm form) {
        if (moderator == null || moderator.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);
        }
        Warehouse formWarehouse = form.getWarehouse();
        if (formWarehouse == null
                || !formWarehouse.getId().equals(moderator.getWarehouse().getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);
        }
    }

    private void authorizeRead(Account account, ConsignmentForm form) {
        switch (account.getRole()) {
            case ADMIN -> {
            }
            case MODERATOR -> {
                if (form.getStatus() == ConsignmentFormStatus.DRAFT) {
                    throw new AppException(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);
                }
                assertModeratorOwnsWarehouse(account, form);
            }
            case MEMBER -> {
                if (!form.getMember().getId().equals(account.getId())) {
                    throw new AppException(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);
                }
            }
            default -> throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }
    }

    private void lockMember(Account account) {
        accountRepository.findByIdForUpdate(account.getId())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    private int maxPendingForms(Account account) {
        return membershipQuotaResolver.resolve(account.getId()).maxPendingConsignmentForms();
    }

    private int bringInDeadlineDays() {
        return systemConfigReader.getInt(SystemConfigKeys.BRING_IN_DEADLINE_DAYS,
                SystemConfigKeys.DEFAULT_BRING_IN_DEADLINE_DAYS);
    }

    private long pendingCount(Account account) {
        return consignmentFormRepository.countByMember_IdAndStatus(account.getId(), ConsignmentFormStatus.PENDING);
    }

    private ConsignmentForm loadOwnDraftForUpdate(UUID id, Account account) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));
        if (!form.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);
        }
        if (form.getStatus() != ConsignmentFormStatus.DRAFT) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_DRAFT);
        }
        return form;
    }

    private void validateCreateContent(ConsignmentFormRequest request) {
        validateDraftContent(request);
        if (request.getWarehouseId() == null) {
            throw new AppException(ErrorCode.WAREHOUSE_ID_REQUIRED);
        }
        if (request.getPhotoUrls() == null || request.getPhotoUrls().isEmpty()) {
            throw new AppException(ErrorCode.PHOTOS_REQUIRED);
        }
    }

    private void validateDraftContent(ConsignmentFormRequest request) {
        if (request.getCategoryId() == null) {
            throw new AppException(ErrorCode.CATEGORY_ID_REQUIRED);
        }
        if (request.getBrandId() == null) {
            throw new AppException(ErrorCode.BRAND_ID_REQUIRED);
        }
        if (request.getProductName() == null || request.getProductName().isBlank()) {
            throw new AppException(ErrorCode.PRODUCT_NAME_REQUIRED);
        }
        if (request.getDescription() == null || request.getDescription().isBlank()) {
            throw new AppException(ErrorCode.DESCRIPTION_REQUIRED);
        }
        if (request.getDesiredPrice() == null || request.getDesiredPrice().signum() <= 0) {
            throw new AppException(ErrorCode.INVALID_DESIRED_PRICE);
        }
    }

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

    private static final Comparator<ConsignmentReceipt> NEWER_RECEIPT = Comparator
            .comparing(ConsignmentReceipt::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(ConsignmentReceipt::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

    private Map<UUID, ConsignmentReceipt> loadLatestReceipts(List<ConsignmentForm> forms) {
        if (forms.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = forms.stream().map(ConsignmentForm::getId).toList();
        return consignmentReceiptRepository.findLatestByFormIds(ids).stream()
                .collect(Collectors.toMap(
                        r -> r.getConsignmentForm().getId(),
                        Function.identity(),
                        (a, b) -> NEWER_RECEIPT.compare(a, b) >= 0 ? a : b));
    }

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