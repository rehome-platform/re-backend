package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.config.ConsignmentProperties;
import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.request.RejectConsignmentFormRequest;
import com.rehome.rehome.dto.request.ReturnOnsiteRequest;
import com.rehome.rehome.dto.request.SubmitConsignmentFormsRequest;
import com.rehome.rehome.dto.response.ConsignmentFormDetailResponse;
import com.rehome.rehome.dto.response.ConsignmentFormListItemResponse;
import com.rehome.rehome.dto.response.ConsignmentFormPageResponse;
import com.rehome.rehome.dto.response.ConsignmentFormResponse;
import com.rehome.rehome.dto.response.ConsignmentFormStatusResponse;
import com.rehome.rehome.dto.response.SubmitConsignmentFormsResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentFormPhoto;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ConsignmentFormMapper;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.BrandRepository;
import com.rehome.rehome.repository.CategoryRepository;
import com.rehome.rehome.repository.ConsignmentFormPhotoRepository;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import com.rehome.rehome.service.QueueService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsignmentFormServiceImplTest {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final AtomicLong SEQ = new AtomicLong(1000);

    @Mock private ConsignmentFormRepository consignmentFormRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private BrandRepository brandRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private ConsignmentFormPhotoRepository photoRepository;
    @Mock private ConsignmentFormMapper consignmentFormMapper;
    @Mock private ConsignmentReceiptRepository consignmentReceiptRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private QueueService queueService;

    private ConsignmentProperties consignmentProperties;
    private ConsignmentFormServiceImpl service;

    private int maxPending = 5;
    private int bringInDays = 2;

    private Account member;
    private UUID memberId;
    private Object catMock;
    private Object brandMock;
    private Object catId;
    private Object brandId;
    private Warehouse warehouse;
    private Object warehouseId;

    @BeforeEach
    void setUp() {
        consignmentProperties = mock(ConsignmentProperties.class, (Answer<Object>) inv -> {
            String name = inv.getMethod().getName();
            Class<?> rt = inv.getMethod().getReturnType();
            if (name.equals("getMaxPendingForms")) {
                return numberOf(rt, maxPending);
            }
            if (name.equals("getBringInDeadlineDays")) {
                return numberOf(rt, bringInDays);
            }
            return null;
        });

        service = new ConsignmentFormServiceImpl(
                consignmentFormRepository, categoryRepository, brandRepository, warehouseRepository,
                photoRepository, consignmentFormMapper, consignmentReceiptRepository,
                consignmentProperties, accountRepository, queueService);

        memberId = UUID.randomUUID();
        member = mock(Account.class);
        when(member.getId()).thenReturn(memberId);
        when(member.getRole()).thenReturn(Role.MEMBER);
        when(accountRepository.findByIdForUpdate(memberId)).thenReturn(Optional.of(member));

        catMock = mockOfField(ConsignmentForm.class, "category");
        catId = stubAuto(catMock, "getId");
        brandMock = mockOfField(ConsignmentForm.class, "brand");
        brandId = stubAuto(brandMock, "getId");

        warehouse = mock(Warehouse.class);
        warehouseId = stubAuto(warehouse, "getId");

        doReturn(Optional.of(catMock)).when(categoryRepository).findById(any());
        doReturn(Optional.of(brandMock)).when(brandRepository).findById(any());
        when(warehouseRepository.findById(any())).thenReturn(Optional.of(warehouse));

        when(consignmentFormRepository.save(any(ConsignmentForm.class))).thenAnswer(i -> i.getArgument(0));
        when(consignmentFormRepository.countByMember_IdAndStatus(any(), any())).thenReturn(0L);
        stubNextFormNo(1);
    }

    // ================================================================= helpers

    private Account nonMember() {
        Account a = mock(Account.class);
        when(a.getId()).thenReturn(UUID.randomUUID());
        when(a.getRole()).thenReturn(Arrays.stream(Role.values()).filter(r -> r != Role.MEMBER).findFirst().orElseThrow());
        return a;
    }

    private Object numberOf(Class<?> type, long v) {
        if (type == int.class || type == Integer.class) return (int) v;
        if (type == long.class || type == Long.class) return v;
        throw new IllegalArgumentException("Unsupported numeric type " + type);
    }

    private Object sampleValue(Class<?> t) {
        if (t == UUID.class) return UUID.randomUUID();
        if (t == Long.class || t == long.class) return SEQ.incrementAndGet();
        if (t == Integer.class || t == int.class) return (int) SEQ.incrementAndGet();
        if (t == Instant.class) return Instant.now();
        if (t == LocalDateTime.class) return LocalDateTime.now();
        if (t == LocalDate.class) return LocalDate.now();
        if (t == String.class) return "sample-" + SEQ.incrementAndGet();
        throw new IllegalArgumentException("Unsupported sample type " + t);
    }

    private void stub(Object mockObj, String getter, Object value) {
        try {
            Object stubbing = doReturn(value).when(mockObj);
            mockObj.getClass().getMethod(getter).invoke(stubbing);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Object stubAuto(Object mockObj, String getter) {
        try {
            Class<?> type = mockObj.getClass().getMethod(getter).getReturnType();
            Object value = sampleValue(type);
            stub(mockObj, getter, value);
            return value;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private Object mockOfField(Class<?> owner, String fieldName) {
        Field f = ReflectionUtils.findField(owner, fieldName);
        if (f == null) {
            throw new IllegalStateException("Field not found: " + fieldName);
        }
        return mock(f.getType());
    }

    private void stubNextFormNo(long first, long... rest) {
        try {
            Class<?> type = ConsignmentFormRepository.class.getMethod("nextFormNo").getReturnType();
            Object[] others = new Object[rest.length];
            for (int i = 0; i < rest.length; i++) {
                others[i] = numberOf(type, rest[i]);
            }
            doReturn(numberOf(type, first), others).when(consignmentFormRepository).nextFormNo();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private ErrorCode errorCodeOf(Throwable t) {
        for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (ErrorCode.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        return (ErrorCode) f.get(t);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        }
        throw new AssertionError("AppException does not hold an ErrorCode field");
    }

    private void assertAppError(ThrowingCallable call, ErrorCode expected) {
        Throwable t = catchThrowable(call);
        assertThat(t).isInstanceOf(AppException.class);
        assertThat(errorCodeOf(t)).isEqualTo(expected);
    }

    /** Request hợp lệ đầy đủ: đủ category, brand, warehouse, tên, mô tả, giá, ảnh. */
    private ConsignmentFormRequest validReq() {
        ConsignmentFormRequest r = mock(ConsignmentFormRequest.class);
        stub(r, "getCategoryId", catId);
        stub(r, "getBrandId", brandId);
        stub(r, "getWarehouseId", warehouseId);
        when(r.getProductName()).thenReturn("Áo khoác");
        when(r.getDescription()).thenReturn("Còn mới 90%");
        when(r.getDesiredPrice()).thenReturn(new BigDecimal("500000"));
        when(r.getPhotoUrls()).thenReturn(List.of("http://a/1.jpg", "http://a/2.jpg"));
        return r;
    }

    private ConsignmentForm newForm(ConsignmentFormStatus status, Account owner) {
        ConsignmentForm f = BeanUtils.instantiateClass(ConsignmentForm.class);
        ReflectionTestUtils.setField(f, "id", UUID.randomUUID());
        f.setStatus(status);
        f.setMember(owner);
        ReflectionTestUtils.setField(f, "category", catMock);
        ReflectionTestUtils.setField(f, "brand", brandMock);
        return f;
    }

    /** Form do chính member sở hữu, đã đăng ký vào repository (findByIdForUpdate + findWithDetailsById). */
    private ConsignmentForm givenForm(ConsignmentFormStatus status) {
        ConsignmentForm f = newForm(status, member);
        when(consignmentFormRepository.findByIdForUpdate(f.getId())).thenReturn(Optional.of(f));
        when(consignmentFormRepository.findWithDetailsById(f.getId())).thenReturn(Optional.of(f));
        return f;
    }

    private ConsignmentFormDetailResponse givenDetailMapping() {
        ConsignmentFormDetailResponse detail = mock(ConsignmentFormDetailResponse.class);
        when(consignmentFormMapper.toDetail(any(), any())).thenReturn(detail);
        when(consignmentReceiptRepository.findByConsignmentForm_Id(any())).thenReturn(Optional.empty());
        return detail;
    }

    private ConsignmentFormPhoto photo(ConsignmentForm form, String url) {
        ConsignmentFormPhoto p = mock(ConsignmentFormPhoto.class);
        when(p.getForm()).thenReturn(form);
        when(p.getImageUrl()).thenReturn(url);
        return p;
    }

    // ================================================================= create

    @Nested
    class Create {

        @Test
        void notMember() {
            assertAppError(() -> service.create(validReq(), nonMember()), ErrorCode.NOT_MEMBER_CREATE_FORM);
        }

        @Test
        void missingCategory() {
            ConsignmentFormRequest r = validReq();
            stub(r, "getCategoryId", null);
            assertAppError(() -> service.create(r, member), ErrorCode.CATEGORY_ID_REQUIRED);
        }

        @Test
        void missingBrand() {
            ConsignmentFormRequest r = validReq();
            stub(r, "getBrandId", null);
            assertAppError(() -> service.create(r, member), ErrorCode.BRAND_ID_REQUIRED);
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "   "})
        void blankProductName(String name) {
            ConsignmentFormRequest r = validReq();
            when(r.getProductName()).thenReturn(name);
            assertAppError(() -> service.create(r, member), ErrorCode.PRODUCT_NAME_REQUIRED);
        }

        @Test
        void nullProductName() {
            ConsignmentFormRequest r = validReq();
            when(r.getProductName()).thenReturn(null);
            assertAppError(() -> service.create(r, member), ErrorCode.PRODUCT_NAME_REQUIRED);
        }

        @Test
        void blankDescription() {
            ConsignmentFormRequest r = validReq();
            when(r.getDescription()).thenReturn(" ");
            assertAppError(() -> service.create(r, member), ErrorCode.DESCRIPTION_REQUIRED);
        }

        @Test
        void nullDescription() {
            ConsignmentFormRequest r = validReq();
            when(r.getDescription()).thenReturn(null);
            assertAppError(() -> service.create(r, member), ErrorCode.DESCRIPTION_REQUIRED);
        }

        @ParameterizedTest
        @ValueSource(strings = {"0", "-1"})
        void nonPositivePrice(String price) {
            ConsignmentFormRequest r = validReq();
            when(r.getDesiredPrice()).thenReturn(new BigDecimal(price));
            assertAppError(() -> service.create(r, member), ErrorCode.INVALID_DESIRED_PRICE);
        }

        @Test
        void nullPrice() {
            ConsignmentFormRequest r = validReq();
            when(r.getDesiredPrice()).thenReturn(null);
            assertAppError(() -> service.create(r, member), ErrorCode.INVALID_DESIRED_PRICE);
        }

        @Test
        void missingWarehouse() {
            ConsignmentFormRequest r = validReq();
            stub(r, "getWarehouseId", null);
            assertAppError(() -> service.create(r, member), ErrorCode.WAREHOUSE_ID_REQUIRED);
        }

        @Test
        void nullPhotos() {
            ConsignmentFormRequest r = validReq();
            when(r.getPhotoUrls()).thenReturn(null);
            assertAppError(() -> service.create(r, member), ErrorCode.PHOTOS_REQUIRED);
        }

        @Test
        void emptyPhotos() {
            ConsignmentFormRequest r = validReq();
            when(r.getPhotoUrls()).thenReturn(List.of());
            assertAppError(() -> service.create(r, member), ErrorCode.PHOTOS_REQUIRED);
        }

        @Test
        void memberRowNotFound() {
            when(accountRepository.findByIdForUpdate(memberId)).thenReturn(Optional.empty());
            assertAppError(() -> service.create(validReq(), member), ErrorCode.ACCOUNT_NOT_FOUND);
        }

        @Test
        void tooManyPendingForms() {
            when(consignmentFormRepository.countByMember_IdAndStatus(memberId, ConsignmentFormStatus.PENDING))
                    .thenReturn((long) maxPending);

            assertAppError(() -> service.create(validReq(), member), ErrorCode.TOO_MANY_PENDING_FORMS);
            verify(consignmentFormRepository, never()).save(any());
        }

        @Test
        void categoryNotFound() {
            ConsignmentFormRequest r = validReq();
            when(consignmentFormMapper.toEntity(r)).thenReturn(newForm(ConsignmentFormStatus.PENDING, member));
            doReturn(Optional.empty()).when(categoryRepository).findById(any());

            assertAppError(() -> service.create(r, member), ErrorCode.CATEGORY_NOT_FOUND);
        }

        @Test
        void brandNotFound() {
            ConsignmentFormRequest r = validReq();
            when(consignmentFormMapper.toEntity(r)).thenReturn(newForm(ConsignmentFormStatus.PENDING, member));
            doReturn(Optional.empty()).when(brandRepository).findById(any());

            assertAppError(() -> service.create(r, member), ErrorCode.BRAND_NOT_FOUND);
        }

        @Test
        void warehouseNotFound() {
            ConsignmentFormRequest r = validReq();
            when(consignmentFormMapper.toEntity(r)).thenReturn(newForm(ConsignmentFormStatus.PENDING, member));
            when(warehouseRepository.findById(any())).thenReturn(Optional.empty());

            assertAppError(() -> service.create(r, member), ErrorCode.WAREHOUSE_NOT_FOUND);
        }

        @Test
        void success_setsRefsFormNoSavesPhotosAndEnqueues() {
            ConsignmentFormRequest r = validReq();
            ConsignmentForm entity = newForm(ConsignmentFormStatus.PENDING, null);
            when(consignmentFormMapper.toEntity(r)).thenReturn(entity);
            stubNextFormNo(7);
            List<ConsignmentFormPhoto> photos = List.of(mock(ConsignmentFormPhoto.class));
            when(consignmentFormMapper.toPhotoEntities(entity, r.getPhotoUrls())).thenReturn(photos);
            ConsignmentFormResponse expected = mock(ConsignmentFormResponse.class);
            when(consignmentFormMapper.toResponse(entity)).thenReturn(expected);

            ConsignmentFormResponse res = service.create(r, member);

            assertThat(res).isSameAs(expected);
            assertThat(entity.getMember()).isSameAs(member);
            assertThat(entity.getWarehouse()).isSameAs(warehouse);
            assertThat(entity.getFormNo()).isEqualTo("KG-000007");
            verify(consignmentFormRepository).save(entity);
            verify(photoRepository).saveAll(photos);
            verify(queueService).enqueue(QueueType.FORM_REVIEW, entity.getId().toString(), warehouse);
        }

        @Test
        void pendingBelowLimit_isAllowed() {
            when(consignmentFormRepository.countByMember_IdAndStatus(memberId, ConsignmentFormStatus.PENDING))
                    .thenReturn((long) maxPending - 1);
            ConsignmentFormRequest r = validReq();
            when(consignmentFormMapper.toEntity(r)).thenReturn(newForm(ConsignmentFormStatus.PENDING, null));

            service.create(r, member);

            verify(consignmentFormRepository).save(any(ConsignmentForm.class));
        }
    }

    // ================================================================= getMyForms

    @Nested
    class GetMyForms {

        private ConsignmentFormListItemResponse item = mock(ConsignmentFormListItemResponse.class);

        @Test
        void notMember() {
            assertAppError(() -> service.getMyForms(nonMember(), null, 0, 10), ErrorCode.NOT_MEMBER_ACCOUNT);
        }

        @Test
        void noStatus_excludesDrafts_andReportsPendingCount() {
            ConsignmentForm f = newForm(ConsignmentFormStatus.PENDING, member);
            when(consignmentFormRepository.findByMemberIdAndStatusNot(eq(memberId),
                    eq(ConsignmentFormStatus.DRAFT), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(f)));
            when(consignmentFormRepository.countByMember_IdAndStatus(memberId, ConsignmentFormStatus.PENDING))
                    .thenReturn(3L);
            ConsignmentFormPhoto p1 = photo(f, "http://a/1.jpg");
            when(photoRepository.findByForm_IdInOrderByDisplayOrderAscIdAsc(anyList()))
                    .thenReturn(List.of(p1));
            when(consignmentFormMapper.toListItem(f, "http://a/1.jpg")).thenReturn(item);

            ConsignmentFormPageResponse res = service.getMyForms(member, null, 0, 10);

            assertThat(res.getContent()).containsExactly(item);
            assertThat(res.getPendingCount()).isEqualTo(3L);
            assertThat(res.getMaxPending()).isEqualTo((long) maxPending);
            assertThat(res.getTotalElements()).isEqualTo(1L);
        }

        @Test
        void withStatus_filtersByStatus() {
            when(consignmentFormRepository.findByMemberIdAndStatus(eq(memberId),
                    eq(ConsignmentFormStatus.APPROVED), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            ConsignmentFormPageResponse res = service.getMyForms(member, ConsignmentFormStatus.APPROVED, 0, 10);

            assertThat(res.getContent()).isEmpty();
            verify(consignmentFormRepository, never())
                    .findByMemberIdAndStatusNot(any(), any(), any(Pageable.class));
        }

        @Test
        void emptyPage_doesNotQueryThumbnails() {
            when(consignmentFormRepository.findByMemberIdAndStatusNot(any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.getMyForms(member, null, 0, 10);

            verify(photoRepository, never()).findByForm_IdInOrderByDisplayOrderAscIdAsc(anyList());
        }

        @Test
        void clampsNegativePage_andZeroSizeToDefault() {
            when(consignmentFormRepository.findByMemberIdAndStatusNot(any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.getMyForms(member, null, -3, 0);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(consignmentFormRepository).findByMemberIdAndStatusNot(any(), any(), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isZero();
            assertThat(captor.getValue().getPageSize()).isEqualTo(20);
            assertThat(captor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        }

        @Test
        void clampsHugeSizeToMax() {
            when(consignmentFormRepository.findByMemberIdAndStatusNot(any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of()));

            service.getMyForms(member, null, 2, 1000);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(consignmentFormRepository).findByMemberIdAndStatusNot(any(), any(), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
            assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        }

        @Test
        void thumbnail_usesFirstPhotoOfEachForm() {
            ConsignmentForm f = newForm(ConsignmentFormStatus.PENDING, member);
            when(consignmentFormRepository.findByMemberIdAndStatusNot(any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(f)));
            ConsignmentFormPhoto first = photo(f, "first.jpg");
            ConsignmentFormPhoto second = photo(f, "second.jpg");
            when(photoRepository.findByForm_IdInOrderByDisplayOrderAscIdAsc(anyList()))
                    .thenReturn(List.of(first, second));
            when(consignmentFormMapper.toListItem(any(), any())).thenReturn(item);

            service.getMyForms(member, null, 0, 10);

            verify(consignmentFormMapper).toListItem(f, "first.jpg");
        }

        @Test
        void formWithoutPhoto_getsNullThumbnail() {
            ConsignmentForm f = newForm(ConsignmentFormStatus.PENDING, member);
            when(consignmentFormRepository.findByMemberIdAndStatusNot(any(), any(), any(Pageable.class)))
                    .thenReturn(new PageImpl<>(List.of(f)));
            when(photoRepository.findByForm_IdInOrderByDisplayOrderAscIdAsc(anyList())).thenReturn(List.of());
            when(consignmentFormMapper.toListItem(any(), any())).thenReturn(item);

            service.getMyForms(member, null, 0, 10);

            verify(consignmentFormMapper).toListItem(f, null);
        }
    }

    // ================================================================= getMyForm

    @Nested
    class GetMyForm {

        @Test
        void notFound() {
            UUID id = UUID.randomUUID();
            when(consignmentFormRepository.findWithDetailsById(id)).thenReturn(Optional.empty());

            assertAppError(() -> service.getMyForm(member, id), ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);
        }

        @Test
        void forbidden_whenNotOwner() {
            ConsignmentForm f = newForm(ConsignmentFormStatus.PENDING, nonMember());
            when(consignmentFormRepository.findWithDetailsById(f.getId())).thenReturn(Optional.of(f));

            assertAppError(() -> service.getMyForm(member, f.getId()), ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);
        }

        @Test
        void success_withoutReceipt() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.PENDING);
            ConsignmentFormDetailResponse detail = givenDetailMapping();

            ConsignmentFormDetailResponse res = service.getMyForm(member, f.getId());

            assertThat(res).isSameAs(detail);
            verify(detail, never()).setReceiptId(any());
        }

        @Test
        void success_withConfirmedReceipt() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.INSPECTING);
            ConsignmentFormDetailResponse detail = givenDetailMapping();
            ConsignmentReceipt receipt = mock(ConsignmentReceipt.class);
            stubAuto(receipt, "getId");
            stubAuto(receipt, "getConfirmedAt");
            when(consignmentReceiptRepository.findByConsignmentForm_Id(f.getId())).thenReturn(Optional.of(receipt));

            service.getMyForm(member, f.getId());

            verify(detail).setReceiptId(any());
            verify(detail).setReceiptConfirmed(true);
        }

        @Test
        void success_withUnconfirmedReceipt() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.INSPECTING);
            ConsignmentFormDetailResponse detail = givenDetailMapping();
            ConsignmentReceipt receipt = mock(ConsignmentReceipt.class);
            stubAuto(receipt, "getId");
            stub(receipt, "getConfirmedAt", null);
            when(consignmentFormRepository.findWithDetailsById(f.getId())).thenReturn(Optional.of(f));
            when(consignmentReceiptRepository.findByConsignmentForm_Id(f.getId())).thenReturn(Optional.of(receipt));

            service.getMyForm(member, f.getId());

            verify(detail).setReceiptConfirmed(false);
        }
    }

    // ================================================================= update

    @Nested
    class Update {

        @Test
        void notMember() {
            assertAppError(() -> service.update(UUID.randomUUID(), validReq(), nonMember()),
                    ErrorCode.NOT_MEMBER_ACCOUNT);
        }

        @Test
        void validationRunsBeforeLookup() {
            ConsignmentFormRequest r = validReq();
            stub(r, "getCategoryId", null);

            assertAppError(() -> service.update(UUID.randomUUID(), r, member), ErrorCode.CATEGORY_ID_REQUIRED);
            verify(consignmentFormRepository, never()).findByIdForUpdate(any());
        }

        @Test
        void notFound() {
            UUID id = UUID.randomUUID();
            when(consignmentFormRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

            assertAppError(() -> service.update(id, validReq(), member), ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);
        }

        @Test
        void forbidden() {
            ConsignmentForm f = newForm(ConsignmentFormStatus.PENDING, nonMember());
            when(consignmentFormRepository.findByIdForUpdate(f.getId())).thenReturn(Optional.of(f));

            assertAppError(() -> service.update(f.getId(), validReq(), member),
                    ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);
        }

        @Test
        void notPending() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.APPROVED);

            assertAppError(() -> service.update(f.getId(), validReq(), member),
                    ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);
        }

        @Test
        void success_sameRefs_keepsPhotosWhenNullOrEmpty() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.PENDING);
            givenDetailMapping();
            ConsignmentFormRequest r = validReq();
            when(r.getPhotoUrls()).thenReturn(null);

            service.update(f.getId(), r, member);

            verify(consignmentFormMapper).applyUpdate(f, r);
            verify(categoryRepository, never()).findById(any());
            verify(brandRepository, never()).findById(any());
            verify(photoRepository, never()).deleteByForm_Id(any());
            verify(consignmentFormRepository).save(f);

            when(r.getPhotoUrls()).thenReturn(List.of());
            service.update(f.getId(), r, member);
            verify(photoRepository, never()).deleteByForm_Id(any());
        }

        @Test
        void categoryChanged_loadsNewCategory() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.PENDING);
            givenDetailMapping();
            ConsignmentFormRequest r = validReq();
            Object newCat = mockOfField(ConsignmentForm.class, "category");
            Object newCatId = stubAuto(newCat, "getId");
            stub(r, "getCategoryId", newCatId);
            doReturn(Optional.of(newCat)).when(categoryRepository).findById(any());

            service.update(f.getId(), r, member);

            assertThat(ReflectionTestUtils.getField(f, "category")).isSameAs(newCat);
        }

        @Test
        void categoryChanged_notFound() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.PENDING);
            ConsignmentFormRequest r = validReq();
            stub(r, "getCategoryId", sampleValue(catId.getClass()));
            doReturn(Optional.empty()).when(categoryRepository).findById(any());

            assertAppError(() -> service.update(f.getId(), r, member), ErrorCode.CATEGORY_NOT_FOUND);
        }

        @Test
        void brandChanged_loadsNewBrand() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.PENDING);
            givenDetailMapping();
            ConsignmentFormRequest r = validReq();
            Object newBrand = mockOfField(ConsignmentForm.class, "brand");
            stub(r, "getBrandId", sampleValue(brandId.getClass()));
            doReturn(Optional.of(newBrand)).when(brandRepository).findById(any());

            service.update(f.getId(), r, member);

            assertThat(ReflectionTestUtils.getField(f, "brand")).isSameAs(newBrand);
        }

        @Test
        void brandChanged_notFound() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.PENDING);
            ConsignmentFormRequest r = validReq();
            stub(r, "getBrandId", sampleValue(brandId.getClass()));
            doReturn(Optional.empty()).when(brandRepository).findById(any());

            assertAppError(() -> service.update(f.getId(), r, member), ErrorCode.BRAND_NOT_FOUND);
        }

        @Test
        void newPhotos_replaceAll() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.PENDING);
            givenDetailMapping();
            ConsignmentFormRequest r = validReq();
            List<ConsignmentFormPhoto> photos = List.of(mock(ConsignmentFormPhoto.class));
            when(consignmentFormMapper.toPhotoEntities(f, r.getPhotoUrls())).thenReturn(photos);

            service.update(f.getId(), r, member);

            verify(photoRepository).deleteByForm_Id(f.getId());
            verify(photoRepository).saveAll(photos);
        }
    }

    // ================================================================= drafts

    @Nested
    class Drafts {

        @Test
        void createDraft_notMember() {
            assertAppError(() -> service.createDraft(validReq(), nonMember()), ErrorCode.NOT_MEMBER_CREATE_FORM);
        }

        @Test
        void createDraft_validationFails() {
            ConsignmentFormRequest r = validReq();
            stub(r, "getBrandId", null);
            assertAppError(() -> service.createDraft(r, member), ErrorCode.BRAND_ID_REQUIRED);
        }

        @Test
        void createDraft_categoryNotFound() {
            ConsignmentFormRequest r = validReq();
            when(consignmentFormMapper.toEntity(r)).thenReturn(newForm(ConsignmentFormStatus.DRAFT, null));
            doReturn(Optional.empty()).when(categoryRepository).findById(any());

            assertAppError(() -> service.createDraft(r, member), ErrorCode.CATEGORY_NOT_FOUND);
        }

        @Test
        void createDraft_brandNotFound() {
            ConsignmentFormRequest r = validReq();
            when(consignmentFormMapper.toEntity(r)).thenReturn(newForm(ConsignmentFormStatus.DRAFT, null));
            doReturn(Optional.empty()).when(brandRepository).findById(any());

            assertAppError(() -> service.createDraft(r, member), ErrorCode.BRAND_NOT_FOUND);
        }

        @Test
        void createDraft_success_noWarehouseNoFormNoQueue() {
            ConsignmentFormRequest r = validReq();
            stub(r, "getWarehouseId", null); // nháp không cần kho
            ConsignmentForm entity = newForm(ConsignmentFormStatus.PENDING, null);
            when(consignmentFormMapper.toEntity(r)).thenReturn(entity);
            ConsignmentFormDetailResponse detail = givenDetailMapping();

            ConsignmentFormDetailResponse res = service.createDraft(r, member);

            assertThat(res).isSameAs(detail);
            assertThat(entity.getStatus()).isEqualTo(ConsignmentFormStatus.DRAFT);
            assertThat(entity.getMember()).isSameAs(member);
            assertThat(entity.getWarehouse()).isNull();
            assertThat(entity.getFormNo()).isNull();
            verify(photoRepository).saveAll(any());
            verify(queueService, never()).enqueue(any(), any(), any());
            verify(consignmentFormRepository, never()).nextFormNo();
        }

        // ---- updateDraft

        @Test
        void updateDraft_notMember() {
            assertAppError(() -> service.updateDraft(UUID.randomUUID(), validReq(), nonMember()),
                    ErrorCode.NOT_MEMBER_ACCOUNT);
        }

        @Test
        void updateDraft_notFound() {
            UUID id = UUID.randomUUID();
            when(consignmentFormRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

            assertAppError(() -> service.updateDraft(id, validReq(), member),
                    ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);
        }

        @Test
        void updateDraft_forbidden() {
            ConsignmentForm f = newForm(ConsignmentFormStatus.DRAFT, nonMember());
            when(consignmentFormRepository.findByIdForUpdate(f.getId())).thenReturn(Optional.of(f));

            assertAppError(() -> service.updateDraft(f.getId(), validReq(), member),
                    ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);
        }

        @Test
        void updateDraft_notDraft() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.PENDING);

            assertAppError(() -> service.updateDraft(f.getId(), validReq(), member),
                    ErrorCode.CONSIGNMENT_FORM_NOT_DRAFT);
        }

        @Test
        void updateDraft_validationFails() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.DRAFT);
            ConsignmentFormRequest r = validReq();
            stub(r, "getCategoryId", null);

            assertAppError(() -> service.updateDraft(f.getId(), r, member), ErrorCode.CATEGORY_ID_REQUIRED);
            verify(consignmentFormMapper, never()).applyUpdate(any(), any());
        }

        @Test
        void updateDraft_success_keepsPhotosAndRefs() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.DRAFT);
            givenDetailMapping();
            ConsignmentFormRequest r = validReq();
            when(r.getPhotoUrls()).thenReturn(null);

            service.updateDraft(f.getId(), r, member);

            verify(consignmentFormMapper).applyUpdate(f, r);
            verify(categoryRepository, never()).findById(any());
            verify(brandRepository, never()).findById(any());
            verify(photoRepository, never()).deleteByForm_Id(any());
            verify(consignmentFormRepository).save(f);
        }

        @Test
        void updateDraft_categoryAndBrandChanged() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.DRAFT);
            givenDetailMapping();
            ConsignmentFormRequest r = validReq();
            Object newCat = mockOfField(ConsignmentForm.class, "category");
            Object newBrand = mockOfField(ConsignmentForm.class, "brand");
            stub(r, "getCategoryId", sampleValue(catId.getClass()));
            stub(r, "getBrandId", sampleValue(brandId.getClass()));
            doReturn(Optional.of(newCat)).when(categoryRepository).findById(any());
            doReturn(Optional.of(newBrand)).when(brandRepository).findById(any());

            service.updateDraft(f.getId(), r, member);

            assertThat(ReflectionTestUtils.getField(f, "category")).isSameAs(newCat);
            assertThat(ReflectionTestUtils.getField(f, "brand")).isSameAs(newBrand);
        }

        @Test
        void updateDraft_categoryChanged_notFound() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.DRAFT);
            ConsignmentFormRequest r = validReq();
            stub(r, "getCategoryId", sampleValue(catId.getClass()));
            doReturn(Optional.empty()).when(categoryRepository).findById(any());

            assertAppError(() -> service.updateDraft(f.getId(), r, member), ErrorCode.CATEGORY_NOT_FOUND);
        }

        @Test
        void updateDraft_brandChanged_notFound() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.DRAFT);
            ConsignmentFormRequest r = validReq();
            stub(r, "getBrandId", sampleValue(brandId.getClass()));
            doReturn(Optional.empty()).when(brandRepository).findById(any());

            assertAppError(() -> service.updateDraft(f.getId(), r, member), ErrorCode.BRAND_NOT_FOUND);
        }

        @Test
        void updateDraft_newPhotos_replaceAll() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.DRAFT);
            givenDetailMapping();
            ConsignmentFormRequest r = validReq();

            service.updateDraft(f.getId(), r, member);

            verify(photoRepository).deleteByForm_Id(f.getId());
            verify(photoRepository).saveAll(any());
        }

        @Test
        void updateDraft_emptyPhotos_keepsOld() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.DRAFT);
            givenDetailMapping();
            ConsignmentFormRequest r = validReq();
            when(r.getPhotoUrls()).thenReturn(List.of());

            service.updateDraft(f.getId(), r, member);

            verify(photoRepository, never()).deleteByForm_Id(any());
        }

        // ---- deleteDraft

        @Test
        void deleteDraft_notMember() {
            assertAppError(() -> service.deleteDraft(UUID.randomUUID(), nonMember()),
                    ErrorCode.NOT_MEMBER_ACCOUNT);
        }

        @Test
        void deleteDraft_notFound() {
            UUID id = UUID.randomUUID();
            when(consignmentFormRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

            assertAppError(() -> service.deleteDraft(id, member), ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);
        }

        @Test
        void deleteDraft_forbidden() {
            ConsignmentForm f = newForm(ConsignmentFormStatus.DRAFT, nonMember());
            when(consignmentFormRepository.findByIdForUpdate(f.getId())).thenReturn(Optional.of(f));

            assertAppError(() -> service.deleteDraft(f.getId(), member), ErrorCode.CONSIGNMENT_FORM_FORBIDDEN);
        }

        @Test
        void deleteDraft_notDraft() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.PENDING);

            assertAppError(() -> service.deleteDraft(f.getId(), member), ErrorCode.CONSIGNMENT_FORM_NOT_DRAFT);
            verify(consignmentFormRepository, never()).delete(any());
        }

        @Test
        void deleteDraft_success() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.DRAFT);

            service.deleteDraft(f.getId(), member);

            verify(photoRepository).deleteByForm_Id(f.getId());
            verify(consignmentFormRepository).delete(f);
        }
    }

    // ================================================================= submitDrafts

    @Nested
    class SubmitDrafts {

        private SubmitConsignmentFormsRequest submitReq(List<UUID> ids) {
            SubmitConsignmentFormsRequest r = mock(SubmitConsignmentFormsRequest.class);
            stub(r, "getWarehouseId", warehouseId);
            when(r.getFormIds()).thenReturn(ids);
            return r;
        }

        @Test
        void notMember() {
            assertAppError(() -> service.submitDrafts(submitReq(List.of()), nonMember()),
                    ErrorCode.NOT_MEMBER_CREATE_FORM);
        }

        @Test
        void warehouseNotFound() {
            when(warehouseRepository.findById(any())).thenReturn(Optional.empty());

            assertAppError(() -> service.submitDrafts(submitReq(List.of(UUID.randomUUID())), member),
                    ErrorCode.WAREHOUSE_NOT_FOUND);
        }

        @Test
        void memberRowNotFound() {
            when(accountRepository.findByIdForUpdate(memberId)).thenReturn(Optional.empty());

            assertAppError(() -> service.submitDrafts(submitReq(List.of(UUID.randomUUID())), member),
                    ErrorCode.ACCOUNT_NOT_FOUND);
        }

        @Test
        void success_movesDraftToPending() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.DRAFT);
            f.setBringInDeadline(LocalDate.now());
            f.setRejectReason("cũ");
            stubNextFormNo(12);

            SubmitConsignmentFormsResponse res = service.submitDrafts(submitReq(List.of(f.getId())), member);

            assertThat(res.getSuccessCount()).isEqualTo(1);
            assertThat(res.getFailureCount()).isZero();
            assertThat(res.getResults()).extracting("success").containsExactly(true);
            assertThat(res.getResults()).extracting("formNo").containsExactly("KG-000012");
            assertThat(f.getStatus()).isEqualTo(ConsignmentFormStatus.PENDING);
            assertThat(f.getWarehouse()).isSameAs(warehouse);
            assertThat(f.getFormNo()).isEqualTo("KG-000012");
            assertThat(f.getBringInDeadline()).isNull();
            assertThat(f.getRejectReason()).isNull();
            verify(consignmentFormRepository).save(f);
            verify(queueService).enqueue(QueueType.FORM_REVIEW, f.getId().toString(), warehouse);
        }

        @Test
        void duplicateIds_areProcessedOnce() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.DRAFT);

            SubmitConsignmentFormsResponse res =
                    service.submitDrafts(submitReq(List.of(f.getId(), f.getId())), member);

            assertThat(res.getResults()).hasSize(1);
            assertThat(res.getSuccessCount()).isEqualTo(1);
            verify(queueService).enqueue(any(), any(), any());
        }

        @Test
        void formNotFound_reportedPerItem() {
            UUID missing = UUID.randomUUID();
            when(consignmentFormRepository.findByIdForUpdate(missing)).thenReturn(Optional.empty());

            SubmitConsignmentFormsResponse res = service.submitDrafts(submitReq(List.of(missing)), member);

            assertThat(res.getSuccessCount()).isZero();
            assertThat(res.getFailureCount()).isEqualTo(1);
            assertThat(res.getResults()).extracting("errorCode")
                    .containsExactly(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND.name());
            assertThat(res.getResults()).extracting("success").containsExactly(false);
        }

        @Test
        void formOfAnotherMember_forbidden_andStatusHidden() {
            ConsignmentForm f = newForm(ConsignmentFormStatus.DRAFT, nonMember());
            when(consignmentFormRepository.findByIdForUpdate(f.getId())).thenReturn(Optional.of(f));

            SubmitConsignmentFormsResponse res = service.submitDrafts(submitReq(List.of(f.getId())), member);

            assertThat(res.getResults()).extracting("errorCode")
                    .containsExactly(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN.name());
            assertThat(res.getResults()).extracting("status").containsNull();
            assertThat(f.getStatus()).isEqualTo(ConsignmentFormStatus.DRAFT);
        }

        @Test
        void formNotDraft_reportsCurrentStatus() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.PENDING);

            SubmitConsignmentFormsResponse res = service.submitDrafts(submitReq(List.of(f.getId())), member);

            assertThat(res.getResults()).extracting("errorCode")
                    .containsExactly(ErrorCode.CONSIGNMENT_FORM_NOT_DRAFT.name());
            assertThat(res.getResults()).extracting("status").containsExactly(ConsignmentFormStatus.PENDING);
        }

        @Test
        void limitReached_midway_restFail() {
            maxPending = 2;
            when(consignmentFormRepository.countByMember_IdAndStatus(memberId, ConsignmentFormStatus.PENDING))
                    .thenReturn(1L);
            ConsignmentForm a = givenForm(ConsignmentFormStatus.DRAFT);
            ConsignmentForm b = givenForm(ConsignmentFormStatus.DRAFT);
            stubNextFormNo(1, 2);

            SubmitConsignmentFormsResponse res =
                    service.submitDrafts(submitReq(List.of(a.getId(), b.getId())), member);

            assertThat(res.getSuccessCount()).isEqualTo(1);
            assertThat(res.getFailureCount()).isEqualTo(1);
            assertThat(res.getResults()).extracting("success").containsExactly(true, false);
            assertThat(res.getResults().get(1)).hasFieldOrPropertyWithValue(
                    "errorCode", ErrorCode.TOO_MANY_PENDING_FORMS.name());
            assertThat(a.getStatus()).isEqualTo(ConsignmentFormStatus.PENDING);
            assertThat(b.getStatus()).isEqualTo(ConsignmentFormStatus.DRAFT);
        }

        @Test
        void mixedResults_countsAreConsistent() {
            ConsignmentForm ok = givenForm(ConsignmentFormStatus.DRAFT);
            UUID missing = UUID.randomUUID();
            when(consignmentFormRepository.findByIdForUpdate(missing)).thenReturn(Optional.empty());

            SubmitConsignmentFormsResponse res =
                    service.submitDrafts(submitReq(List.of(missing, ok.getId())), member);

            assertThat(res.getSuccessCount()).isEqualTo(1);
            assertThat(res.getFailureCount()).isEqualTo(1);
            assertThat(res.getResults()).hasSize(2);
            assertThat(res.getWarehouseId()).isEqualTo(warehouseId);
        }
    }

    // ================================================================= moderator

    @Nested
    class Moderator {

        private RejectConsignmentFormRequest rejectReq(String reason) {
            RejectConsignmentFormRequest r = mock(RejectConsignmentFormRequest.class);
            when(r.getReason()).thenReturn(reason);
            return r;
        }

        private ReturnOnsiteRequest onsiteReq(String reason) {
            ReturnOnsiteRequest r = mock(ReturnOnsiteRequest.class);
            when(r.getReason()).thenReturn(reason);
            return r;
        }

        // ---- approve

        @Test
        void approve_notFound() {
            UUID id = UUID.randomUUID();
            when(consignmentFormRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

            assertAppError(() -> service.approve(id), ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);
        }

        @Test
        void approve_notPending() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.APPROVED);

            assertAppError(() -> service.approve(f.getId()), ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);
            verify(queueService, never()).dequeue(any(), any());
        }

        @Test
        void approve_success_setsDeadlineAndDequeues() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.PENDING);
            f.setRejectReason("cũ");
            ConsignmentFormResponse expected = mock(ConsignmentFormResponse.class);
            when(consignmentFormMapper.toResponse(f)).thenReturn(expected);

            ConsignmentFormResponse res = service.approve(f.getId());

            assertThat(res).isSameAs(expected);
            assertThat(f.getStatus()).isEqualTo(ConsignmentFormStatus.APPROVED);
            assertThat(f.getRejectReason()).isNull();
            assertThat(f.getBringInDeadline()).isEqualTo(LocalDate.now(VN_ZONE).plusDays(bringInDays));
            verify(queueService).dequeue(QueueType.FORM_REVIEW, f.getId().toString());
            verify(consignmentFormRepository).save(f);
        }

        // ---- startInspection

        @Test
        void startInspection_notFound() {
            UUID id = UUID.randomUUID();
            when(consignmentFormRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

            assertAppError(() -> service.startInspection(id), ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);
        }

        @Test
        void startInspection_notApproved() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.PENDING);

            assertAppError(() -> service.startInspection(f.getId()), ErrorCode.CONSIGNMENT_FORM_NOT_APPROVED);
            verify(queueService, never()).enqueue(any(), any(), any());
        }

        @Test
        void startInspection_success_enqueuesInspection() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.APPROVED);
            f.setWarehouse(warehouse);

            service.startInspection(f.getId());

            assertThat(f.getStatus()).isEqualTo(ConsignmentFormStatus.INSPECTING);
            verify(consignmentFormRepository).save(f);
            verify(queueService).enqueue(QueueType.INSPECTION, f.getId().toString(), warehouse);
        }

        // ---- reject

        @Test
        void reject_notFound() {
            UUID id = UUID.randomUUID();
            when(consignmentFormRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

            assertAppError(() -> service.reject(id, rejectReq("x")), ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);
        }

        @Test
        void reject_notPending() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.INSPECTING);

            assertAppError(() -> service.reject(f.getId(), rejectReq("x")),
                    ErrorCode.CONSIGNMENT_FORM_NOT_PENDING);
        }

        @Test
        void reject_success_trimsReason() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.PENDING);
            f.setBringInDeadline(LocalDate.now());

            ConsignmentFormStatusResponse res = service.reject(f.getId(), rejectReq("  Ảnh mờ  "));

            assertThat(f.getStatus()).isEqualTo(ConsignmentFormStatus.REJECTED);
            assertThat(f.getRejectReason()).isEqualTo("Ảnh mờ");
            assertThat(f.getBringInDeadline()).isNull();
            assertThat(res).hasFieldOrPropertyWithValue("status", ConsignmentFormStatus.REJECTED);
            verify(queueService).dequeue(QueueType.FORM_REVIEW, f.getId().toString());
            verify(consignmentFormRepository).save(f);
        }

        // ---- returnOnsite

        @Test
        void returnOnsite_notFound() {
            UUID id = UUID.randomUUID();
            when(consignmentFormRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

            assertAppError(() -> service.returnOnsite(id, onsiteReq("x")), ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);
        }

        @Test
        void returnOnsite_notInspecting() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.APPROVED);

            assertAppError(() -> service.returnOnsite(f.getId(), onsiteReq("x")),
                    ErrorCode.CONSIGNMENT_FORM_NOT_INSPECTING);
        }

        @Test
        void returnOnsite_success_trimsReasonAndDequeuesInspection() {
            ConsignmentForm f = givenForm(ConsignmentFormStatus.INSPECTING);
            f.setBringInDeadline(LocalDate.now());

            ConsignmentFormStatusResponse res = service.returnOnsite(f.getId(), onsiteReq("  Hàng giả  "));

            assertThat(f.getStatus()).isEqualTo(ConsignmentFormStatus.REJECTED);
            assertThat(f.getRejectReason()).isEqualTo("Hàng giả");
            assertThat(f.getBringInDeadline()).isNull();
            assertThat(res).hasFieldOrPropertyWithValue("status", ConsignmentFormStatus.REJECTED);
            verify(queueService).dequeue(QueueType.INSPECTION, f.getId().toString());
            verify(consignmentFormRepository).save(f);
        }
    }
}