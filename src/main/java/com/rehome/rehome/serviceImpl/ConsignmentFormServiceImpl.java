package com.rehome.rehome.serviceImpl;

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
    private final AuditLogger auditLogger;
    private final ConsignmentFormRepository consignmentFormRepository;
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

    // ---------------------------------------------------------------- Member

    @Override
    @Transactional
    public ConsignmentFormResponse create(ConsignmentFormRequest request, Account account) {
        moderation.check(account.getId(), "CONSIGNMENT_FORM", request.getProductName(), request.getDescription());

        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_CREATE_FORM);
        }

        // Thiếu id/ảnh phải trả 400, không để findById(null) ném lỗi thành 500
        validateCreateContent(request);

        // Khoá dòng Member rồi đếm, để hai request song song không cùng lọt qua giới hạn
        lockMember(account);
        if (pendingCount(account) >= maxPendingForms(account)) {
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

        // Một query lấy ảnh đầu của cả trang, tránh N+1
        Map<UUID, String> thumbnails = loadThumbnails(result.getContent());
        // Biên nhận mới nhất của từng form, để mobile biết form nào đang chờ xác nhận biên nhận
        // (vẫn INSPECTING) hoặc đã xác nhận (ẩn khỏi "đang xử lý", trùng với sản phẩm trong kho)
        Map<UUID, ConsignmentReceipt> latestReceipts = loadLatestReceipts(result.getContent());

        PageResponse<ConsignmentFormListItemResponse> p = PageResponse.of(result.map(
                f -> consignmentFormMapper.toListItem(f, thumbnails.get(f.getId()), latestReceipts.get(f.getId()))));

        // pendingCount + maxPending để mobile hiện "3/5 form chờ"
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

    // PUT /consignment-forms/{id}: chỉ khi PENDING, không đổi kho
    @Override
    @Transactional
    public ConsignmentFormDetailResponse update(UUID id, ConsignmentFormRequest request, Account account) {
        moderation.check(account.getId(), "CONSIGNMENT_FORM", request.getProductName(), request.getDescription());

        if (account.getRole() != Role.MEMBER) {
            throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }

        // applyUpdate ghi đè thẳng các cột NOT NULL, nên body phải đủ như POST
        validateDraftContent(request);

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
        // warehouse và formNo để trống tới lúc gửi

        cf = consignmentFormRepository.save(cf);
        photoRepository.saveAll(consignmentFormMapper.toPhotoEntities(cf, request.getPhotoUrls()));
        return buildDetail(cf);
    }

    // PUT /consignment-forms/drafts/{id}: bỏ qua warehouseId trong body
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
        int max = maxPendingForms(account);

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

    // ------------------------------------------------------------- Moderator

    @Override
    @Transactional
    public ConsignmentFormResponse approve(UUID id, Account moderator) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));   // 404
        assertModeratorOwnsWarehouse(moderator, form);

        if (form.getStatus() != ConsignmentFormStatus.PENDING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);                    // 409
        }

        form.setStatus(ConsignmentFormStatus.APPROVED);
        queueService.dequeue(QueueType.FORM_REVIEW, form.getId().toString());
        form.setBringInDeadline(
                LocalDate.now(VN_ZONE).plusDays(bringInDeadlineDays()));
        form.setRejectReason(null);

        ConsignmentForm saved = consignmentFormRepository.save(form);
        logForm(moderator, form, "APPROVE_FORM", "APPROVED");
        return consignmentFormMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void startInspection(UUID id, Account moderator) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));   // 404
        assertModeratorOwnsWarehouse(moderator, form);

        if (form.getStatus() != ConsignmentFormStatus.APPROVED) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_APPROVED);                   // 409
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
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));   // 404
        assertModeratorOwnsWarehouse(moderator, form);

        if (form.getStatus() != ConsignmentFormStatus.PENDING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);                    // 409
        }

        form.setStatus(ConsignmentFormStatus.REJECTED);
        queueService.dequeue(QueueType.FORM_REVIEW, form.getId().toString());
        form.setRejectReason(request.getReason().trim());
        form.setBringInDeadline(null);

        consignmentFormRepository.save(form);
        logForm(moderator, form, "REJECT_FORM", "REJECTED");
        return new ConsignmentFormStatusResponse(form.getId(), form.getStatus());
    }

    @Override
    @Transactional
    public ConsignmentFormStatusResponse returnOnsite(UUID id, ReturnOnsiteRequest request, Account moderator) {
        ConsignmentForm form = consignmentFormRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND));   // 404
        assertModeratorOwnsWarehouse(moderator, form);

        if (form.getStatus() != ConsignmentFormStatus.INSPECTING) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_NOT_INSPECTING);                 // 409
        }

        form.setStatus(ConsignmentFormStatus.REJECTED);
        queueService.dequeue(QueueType.INSPECTION, form.getId().toString());
        form.setRejectReason(request.getReason().trim());
        form.setBringInDeadline(null);

        consignmentFormRepository.save(form);
        logForm(moderator, form, "RETURN_ONSITE", "RETURNED");
        return new ConsignmentFormStatusResponse(form.getId(), form.getStatus());
    }

    // --------------------------------------------------------------- helpers

    // Log nhóm INSPECTION; gọi trong transaction của thao tác nên lỗi/rollback thì không còn log
    private void logForm(Account moderator, ConsignmentForm form, String action, String result) {
        auditLogger.logWarehouseOp(moderator, form.getWarehouse(), WarehouseAuditGroup.INSPECTION, action,
                "CONSIGNMENT_FORM", form.getId().toString(), result, form.getFormNo());
    }

    // Moderator chỉ thao tác trên form thuộc kho mình phụ trách
    private void assertModeratorOwnsWarehouse(Account moderator, ConsignmentForm form) {
        if (moderator == null || moderator.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);                          // 403
        }
        Warehouse formWarehouse = form.getWarehouse();
        if (formWarehouse == null
                || !formWarehouse.getId().equals(moderator.getWarehouse().getId())) {
            throw new AppException(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);                      // 403
        }
    }

    // Quyền đọc form: Member chỉ form của mình; Moderator form thuộc kho mình (không gồm nháp);
    // Admin đọc mọi form
    private void authorizeRead(Account account, ConsignmentForm form) {
        switch (account.getRole()) {
            case ADMIN -> {
                // được đọc theo quyền quản trị
            }
            case MODERATOR -> {
                if (form.getStatus() == ConsignmentFormStatus.DRAFT) {
                    throw new AppException(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);              // 403
                }
                assertModeratorOwnsWarehouse(account, form);
            }
            case MEMBER -> {
                if (!form.getMember().getId().equals(account.getId())) {
                    throw new AppException(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);              // 403
                }
            }
            default -> throw new AppException(ErrorCode.NOT_MEMBER_ACCOUNT);
        }
    }

    private void lockMember(Account account) {
        accountRepository.findByIdForUpdate(account.getId())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
    }

    /**
     * Số form PENDING tối đa của một Member - <b>đọc theo GÓI THÀNH VIÊN</b>
     * ({@code membership_plans.max_pending_consignment_forms}, Admin sửa ở màn W-01):
     * FREE 5 -> SILVER 10 -> GOLD 15 -> DIAMOND 30.
     *
     * <p>Trước đây đọc {@code system_configs.max_pending_forms_per_member} và áp chung cho mọi
     * Member. Key đó <b>giữ lại làm giá trị dự phòng duy nhất</b> cho trường hợp không resolve được
     * gói (bảng {@code membership_plans} chưa seed) - xem {@code MembershipQuotaResolver.FALLBACK}
     * và ghi chú ở {@code SystemConfigKeys.MAX_PENDING_FORMS_PER_MEMBER}. Nguồn thắng là GÓI;
     * sửa key kia sẽ không đổi hạn mức của Member nào resolve được gói.
     *
     * <p>Có 3 nơi gọi và cả 3 đều phải đi qua đây: lúc tạo form, lúc dựng {@code maxPending} cho
     * API danh sách (mobile hiển thị "3/5 form"), và lúc gửi hàng loạt form nháp.
     */
    private int maxPendingForms(Account account) {
        return membershipQuotaResolver.resolve(account.getId()).maxPendingConsignmentForms();
    }

    // Số ngày Member phải mang đồ đến kho sau khi form được duyệt, Admin chỉnh được trong system_configs
    private int bringInDeadlineDays() {
        return systemConfigReader.getInt(SystemConfigKeys.BRING_IN_DEADLINE_DAYS,
                SystemConfigKeys.DEFAULT_BRING_IN_DEADLINE_DAYS);
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

    // POST /consignment-forms: như nháp nhưng bắt buộc có kho và ít nhất 1 ảnh
    private void validateCreateContent(ConsignmentFormRequest request) {
        validateDraftContent(request);
        if (request.getWarehouseId() == null) {
            throw new AppException(ErrorCode.WAREHOUSE_ID_REQUIRED);
        }
        if (request.getPhotoUrls() == null || request.getPhotoUrls().isEmpty()) {
            throw new AppException(ErrorCode.PHOTOS_REQUIRED);
        }
    }

    // Nháp chỉ được thiếu kho; các cột còn lại của bảng đều NOT NULL nên vẫn phải đủ
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

    // Cùng một quy tắc "biên nhận mới nhất" với buildDetail/ModeratorConsignmentFormServiceImpl:
    // createdAt lớn nhất, hòa thì id lớn hơn - để danh sách và chi tiết luôn khớp nhau
    private static final Comparator<ConsignmentReceipt> NEWER_RECEIPT = Comparator
            .comparing(ConsignmentReceipt::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(ConsignmentReceipt::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

    // Biên nhận mới nhất của mỗi form (1 form có thể có nhiều biên nhận do ký gửi lại),
    // lấy bằng một query cho cả trang
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