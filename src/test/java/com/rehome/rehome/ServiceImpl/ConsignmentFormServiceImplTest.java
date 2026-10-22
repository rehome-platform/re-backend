package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.config.ConsignmentProperties;
import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.request.RejectConsignmentFormRequest;
import com.rehome.rehome.dto.request.ReturnOnsiteRequest;
import com.rehome.rehome.dto.response.ConsignmentFormDetailResponse;
import com.rehome.rehome.dto.response.ConsignmentFormListItemResponse;
import com.rehome.rehome.dto.response.ConsignmentFormResponse;
import com.rehome.rehome.dto.response.ConsignmentFormStatusResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.ReturnOnsiteResult;   // đổi package nếu enum của bạn nằm chỗ khác
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.*;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ConsignmentFormMapper;
import com.rehome.rehome.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsignmentFormServiceImplTest {

    @Mock private ConsignmentFormRepository consignmentFormRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ConsignmentFormPhotoRepository photoRepository;
    @Mock private ConsignmentFormMapper consignmentFormMapper;
    @Mock private ConsignmentReceiptRepository consignmentReceiptRepository;

    // MỚI: service có field ConsignmentProperties (dùng cho approve)
    @Spy private ConsignmentProperties consignmentProperties = new ConsignmentProperties();

    @InjectMocks private ConsignmentFormServiceImpl service;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

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

    // =====================================================================
    // create
    // =====================================================================

    @Test
    void create_success_setsRelationsSavesFormAndPhotos() {
        Account account = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
        ConsignmentFormRequest req = request();

        ConsignmentForm mapped = new ConsignmentForm();
        ConsignmentForm saved = new ConsignmentForm();           // save() trả về instance khác
        Category category = new Category();
        Brand brand = new Brand();
        Warehouse warehouse = new Warehouse();
        List<ConsignmentFormPhoto> photos = List.of(new ConsignmentFormPhoto(), new ConsignmentFormPhoto());
        ConsignmentFormResponse response = new ConsignmentFormResponse();

        when(consignmentFormMapper.toEntity(req)).thenReturn(mapped);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        when(brandRepository.findById(brandId)).thenReturn(Optional.of(brand));
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
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
        Account account = Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
        ConsignmentFormRequest req = request();

        when(consignmentFormMapper.toEntity(req)).thenReturn(new ConsignmentForm());
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.empty());

        assertAppError(() -> service.create(req, account), ErrorCode.CATEGORY_NOT_FOUND);

        verify(consignmentFormRepository, never()).save(any());
        verifyNoInteractions(photoRepository);
    }

    // SỬA: thêm role MEMBER vì getMyForms() có check role
    private Account member() {
        return Account.builder().id(UUID.randomUUID()).role(Role.MEMBER).build();
    }

    // =====================================================================
    // getMyForms
    // =====================================================================

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

    // MỚI
    @Test
    void getMyForms_nonMember_throwsNotMemberAccount() {
        Account moderator = Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR).build();

        assertAppError(() -> service.getMyForms(moderator, null, 0, 20), ErrorCode.NOT_MEMBER_ACCOUNT);

        verifyNoInteractions(consignmentFormRepository);
    }

    // =====================================================================
    // getMyForm
    // =====================================================================

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
    void getMyForm_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(consignmentFormRepository.findById(id)).thenReturn(Optional.empty());

        assertAppError(() -> service.getMyForm(member(), id), ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);

        verify(consignmentFormRepository).findById(id);
        verifyNoInteractions(consignmentFormMapper, consignmentReceiptRepository);
    }

    @Test
    void getMyForm_ownedByAnotherMember_throws403() {
        Account owner = member();
        Account other = member(); // phải có id khác owner
        ConsignmentForm form = formOf(owner, ConsignmentFormStatus.PENDING);
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));

        assertAppError(() -> service.getMyForm(other, form.getId()), ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);

        verifyNoInteractions(consignmentFormMapper, consignmentReceiptRepository);
    }

    @Test
    void getMyForm_ownerWithoutReceipt_returnsDetailWithNullReceiptId() {
        Account owner = member();
        ConsignmentForm form = formOf(owner, ConsignmentFormStatus.REJECTED);
        ConsignmentFormDetailResponse dto = detailOf(form, ConsignmentFormStatus.REJECTED);
        dto.setRejectReason(form.getRejectReason());

        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));
        when(consignmentFormMapper.toDetail(form)).thenReturn(dto);
        when(consignmentReceiptRepository.findByConsignmentForm_Id(form.getId()))
                .thenReturn(Optional.empty());

        ConsignmentFormDetailResponse result = service.getMyForm(owner, form.getId());

        assertThat(result).isSameAs(dto);
        assertThat(result.getReceiptId()).isNull();
    }

    private ConsignmentFormDetailResponse detailOf(ConsignmentForm form, ConsignmentFormStatus status) {
        ConsignmentFormDetailResponse dto = new ConsignmentFormDetailResponse();
        dto.setId(form.getId());
        dto.setStatus(status);
        dto.setProductName(form.getProductName());
        return dto;
    }

    @Test
    void getMyForm_ownerWithReceipt_setsReceiptId() {
        Account owner = member();
        ConsignmentForm form = formOf(owner, ConsignmentFormStatus.APPROVED);
        ConsignmentFormDetailResponse dto = detailOf(form, ConsignmentFormStatus.APPROVED);
        dto.setBringInDeadline(LocalDate.now().plusDays(7));

        UUID receiptId = UUID.randomUUID();
        ConsignmentReceipt receipt = mock(ConsignmentReceipt.class);
        when(receipt.getId()).thenReturn(receiptId);

        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));
        when(consignmentFormMapper.toDetail(form)).thenReturn(dto);
        when(consignmentReceiptRepository.findByConsignmentForm_Id(form.getId()))
                .thenReturn(Optional.of(receipt));

        ConsignmentFormDetailResponse result = service.getMyForm(owner, form.getId());

        assertThat(result).isSameAs(dto);
        assertThat(result.getReceiptId()).isEqualTo(receiptId);
        assertThat(result.getBringInDeadline()).isEqualTo(dto.getBringInDeadline());
    }

    // =====================================================================
    // API 31: approve
    // =====================================================================

    @Test
    void approve_success_setsApproved_deadline7Days_clearsRejectReason() {
        ConsignmentForm form = formOf(member(), ConsignmentFormStatus.PENDING);   // formOf có rejectReason cũ
        ConsignmentFormResponse dto = new ConsignmentFormResponse();
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));
        when(consignmentFormRepository.save(form)).thenReturn(form);
        when(consignmentFormMapper.toResponse(form)).thenReturn(dto);

        ConsignmentFormResponse result = service.approve(form.getId());

        assertThat(result).isSameAs(dto);
        assertThat(form.getStatus()).isEqualTo(ConsignmentFormStatus.APPROVED);
        assertThat(form.getBringInDeadline()).isEqualTo(LocalDate.now(VN_ZONE).plusDays(7));
        assertThat(form.getRejectReason()).isNull();
    }

    @Test
    void approve_usesConfiguredDeadlineDays() {
        consignmentProperties.setBringInDeadlineDays(3);
        ConsignmentForm form = formOf(member(), ConsignmentFormStatus.PENDING);
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));
        when(consignmentFormRepository.save(form)).thenReturn(form);
        when(consignmentFormMapper.toResponse(form)).thenReturn(new ConsignmentFormResponse());

        service.approve(form.getId());

        assertThat(form.getBringInDeadline()).isEqualTo(LocalDate.now(VN_ZONE).plusDays(3));
    }

    @Test
    void approve_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(consignmentFormRepository.findById(id)).thenReturn(Optional.empty());

        assertAppError(() -> service.approve(id), ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);

        verify(consignmentFormRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ConsignmentFormStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void approve_notPending_throws409(ConsignmentFormStatus status) {
        ConsignmentForm form = formOf(member(), status);
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));

        assertAppError(() -> service.approve(form.getId()), ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);

        assertThat(form.getStatus()).isEqualTo(status);   // không bị đổi
        verify(consignmentFormRepository, never()).save(any());
    }

    // =====================================================================
    // API 32: reject
    // =====================================================================

    private RejectConsignmentFormRequest rejectRequest(String reason) {
        RejectConsignmentFormRequest r = new RejectConsignmentFormRequest();
        r.setReason(reason);
        return r;
    }

    @Test
    void reject_success_setsRejectedTrimmedReason_clearsDeadline() {
        ConsignmentForm form = formOf(member(), ConsignmentFormStatus.PENDING);
        form.setBringInDeadline(LocalDate.now().plusDays(7));
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));

        ConsignmentFormStatusResponse result = service.reject(form.getId(), rejectRequest("  Ảnh không rõ  "));

        assertThat(form.getStatus()).isEqualTo(ConsignmentFormStatus.REJECTED);
        assertThat(form.getRejectReason()).isEqualTo("Ảnh không rõ");
        assertThat(form.getBringInDeadline()).isNull();
        assertThat(result.getId()).isEqualTo(form.getId());
        assertThat(result.getStatus()).isEqualTo(ConsignmentFormStatus.REJECTED);
        verify(consignmentFormRepository).save(form);
    }

    @Test
    void reject_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(consignmentFormRepository.findById(id)).thenReturn(Optional.empty());

        assertAppError(() -> service.reject(id, rejectRequest("x")), ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(value = ConsignmentFormStatus.class, names = "PENDING", mode = EnumSource.Mode.EXCLUDE)
    void reject_notPending_throws409(ConsignmentFormStatus status) {
        ConsignmentForm form = formOf(member(), status);
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));

        assertAppError(() -> service.reject(form.getId(), rejectRequest("x")),
                ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);

        verify(consignmentFormRepository, never()).save(any());
    }

    // =====================================================================
    // API 33: startInspection
    // =====================================================================

    @Test
    void startInspection_success_approvedBecomesInspecting() {
        ConsignmentForm form = formOf(member(), ConsignmentFormStatus.APPROVED);
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));

        service.startInspection(form.getId());

        assertThat(form.getStatus()).isEqualTo(ConsignmentFormStatus.INSPECTING);
        verify(consignmentFormRepository).save(form);
    }

    @Test
    void startInspection_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(consignmentFormRepository.findById(id)).thenReturn(Optional.empty());

        assertAppError(() -> service.startInspection(id), ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(value = ConsignmentFormStatus.class, names = "APPROVED", mode = EnumSource.Mode.EXCLUDE)
    void startInspection_notApproved_throws409(ConsignmentFormStatus status) {
        ConsignmentForm form = formOf(member(), status);
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));

        assertAppError(() -> service.startInspection(form.getId()), ErrorCode.CONSIGNMENT_FORM_NOT_APPROVED);

        verify(consignmentFormRepository, never()).save(any());
    }

    // =====================================================================
    // API 35: returnOnsite
    // =====================================================================

    private ReturnOnsiteRequest returnRequest(ReturnOnsiteResult result, String reason) {
        ReturnOnsiteRequest r = new ReturnOnsiteRequest();
        r.setResult(result);
        r.setReason(reason);
        return r;
    }

    @ParameterizedTest
    @EnumSource(ReturnOnsiteResult.class)
    void returnOnsite_success_inspectingBecomesRejected(ReturnOnsiteResult result) {
        ConsignmentForm form = formOf(member(), ConsignmentFormStatus.INSPECTING);
        form.setBringInDeadline(LocalDate.now().plusDays(3));
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));

        ConsignmentFormStatusResponse res =
                service.returnOnsite(form.getId(), returnRequest(result, "  Vải bị mốc  "));

        assertThat(form.getStatus()).isEqualTo(ConsignmentFormStatus.REJECTED);
        assertThat(form.getRejectReason()).isEqualTo("Vải bị mốc");
        assertThat(form.getBringInDeadline()).isNull();
        assertThat(res.getStatus()).isEqualTo(ConsignmentFormStatus.REJECTED);
        verify(consignmentFormRepository).save(form);
    }

    @Test
    void returnOnsite_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(consignmentFormRepository.findById(id)).thenReturn(Optional.empty());

        assertAppError(() -> service.returnOnsite(id, returnRequest(ReturnOnsiteResult.FAIL, "x")),
                ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);
    }

    @ParameterizedTest
    @EnumSource(value = ConsignmentFormStatus.class, names = "INSPECTING", mode = EnumSource.Mode.EXCLUDE)
    void returnOnsite_notInspecting_throws409(ConsignmentFormStatus status) {
        ConsignmentForm form = formOf(member(), status);
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));

        assertAppError(() -> service.returnOnsite(form.getId(), returnRequest(ReturnOnsiteResult.FAIL, "x")),
                ErrorCode.CONSIGNMENT_FORM_NOT_INSPECTING);

        assertThat(form.getStatus()).isEqualTo(status);
        verify(consignmentFormRepository, never()).save(any());
    }
}