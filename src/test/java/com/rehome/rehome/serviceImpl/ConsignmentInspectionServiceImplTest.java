package com.rehome.rehome.serviceImpl;

import static com.rehome.rehome.constant.SystemConfigKeys.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rehome.rehome.component.ReceiptNoGenerator;
import com.rehome.rehome.dto.request.CompleteInspectionRequest;
import com.rehome.rehome.dto.response.CompleteInspectionResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.InspectionResult;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.service.SystemConfigReader;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ConsignmentInspectionServiceImplTest {

    @Mock private ConsignmentFormRepository consignmentFormRepository;
    @Mock private ConsignmentReceiptRepository consignmentReceiptRepository;
    @Mock private SystemConfigReader systemConfigReader;
    @Mock private ReceiptNoGenerator receiptNoGenerator;
    @Mock private QueueService queueService;

    @InjectMocks private ConsignmentInspectionServiceImpl service;

    private UUID formId;
    private Warehouse warehouse;
    private Account moderator;
    private Account member;
    private ConsignmentForm form;

    @BeforeEach
    void setUp() {
        formId = UUID.randomUUID();
        warehouse = warehouse(1);
        moderator = Account.builder().id(UUID.randomUUID()).warehouse(warehouse).build();
        member = Account.builder().id(UUID.randomUUID()).build();
        form = ConsignmentForm.builder()
                .id(formId)
                .member(member)
                .warehouse(warehouse)
                .productName("Áo khoác denim")
                .status(ConsignmentFormStatus.INSPECTING)
                .build();
    }

    // ---------- helpers ----------

    private static void assertAppError(ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    // Tạo Warehouse với id xác định theo key; tự nhận biết kiểu id (UUID/Long/Integer/String)
    private static Warehouse warehouse(int key) {
        Warehouse w = new Warehouse();
        Class<?> idType;
        try {
            idType = Warehouse.class.getDeclaredField("id").getType();
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Warehouse không có field id", e);
        }
        Object id;
        if (idType == UUID.class) id = new UUID(0L, key);
        else if (idType == Long.class || idType == long.class) id = (long) key;
        else if (idType == Integer.class || idType == int.class) id = key;
        else id = "wh-" + key;
        ReflectionTestUtils.setField(w, "id", id);
        return w;
    }

    private static CompleteInspectionRequest completeRequest(
            InspectionResult result, String note, BigDecimal price, Integer termDays) {
        CompleteInspectionRequest r = new CompleteInspectionRequest();
        r.setResult(result);
        r.setConditionNote(note);
        r.setAgreedPrice(price);
        r.setTermLengthDays(termDays);
        return r;
    }

    private static CompleteInspectionRequest validComplete() {
        return completeRequest(InspectionResult.PASS, "Áo còn mới 90%, không lỗi", new BigDecimal("220000"), 30);
    }

    // Config trả về đúng giá trị mặc định được truyền vào, giống khi bảng system_configs trống
    private void stubConfigDefaults() {
        lenient().when(systemConfigReader.getInt(anyString(), anyInt())).thenAnswer(inv -> inv.getArgument(1));
        lenient()
                .when(systemConfigReader.getDecimal(anyString(), any(BigDecimal.class)))
                .thenAnswer(inv -> inv.getArgument(1));
    }

    private void stubFormFound() {
        when(consignmentFormRepository.findByIdForUpdate(formId)).thenReturn(Optional.of(form));
    }

    private void stubReceiptSave(UUID receiptId) {
        when(consignmentReceiptRepository.save(any(ConsignmentReceipt.class))).thenAnswer(inv -> {
            ConsignmentReceipt r = inv.getArgument(0);
            r.setId(receiptId);
            return r;
        });
    }

    // =====================================================================
    // API 34
    // =====================================================================
    @Nested
    @DisplayName("API 34 - completeInspection")
    class CompleteInspection {

        @Test
        @DisplayName("Thành công: tạo phiếu với dữ liệu form, giá, config mặc định; đóng việc INSPECTION")
        void success_createsReceipt() {
            UUID receiptId = UUID.randomUUID();
            stubFormFound();
            when(consignmentReceiptRepository.existsByConsignmentForm_Id(formId)).thenReturn(false);
            when(receiptNoGenerator.next()).thenReturn("KG-2026-000001");
            stubConfigDefaults();
            stubReceiptSave(receiptId);

            CompleteInspectionResponse response = service.completeInspection(formId, validComplete(), moderator);

            assertThat(response.getReceiptId()).isEqualTo(receiptId);

            ArgumentCaptor<ConsignmentReceipt> captor = ArgumentCaptor.forClass(ConsignmentReceipt.class);
            verify(consignmentReceiptRepository).save(captor.capture());
            ConsignmentReceipt r = captor.getValue();
            assertThat(r.getReceiptNo()).isEqualTo("KG-2026-000001");
            assertThat(r.getConsignmentForm()).isSameAs(form);
            assertThat(r.getMember()).isSameAs(member);
            assertThat(r.getModerator()).isSameAs(moderator);
            assertThat(r.getWarehouse()).isSameAs(warehouse);
            assertThat(r.getAgreedPrice()).isEqualByComparingTo("220000");
            assertThat(r.getFloorPrice()).isEqualByComparingTo("110000");
            assertThat(r.getTermLengthDays()).isEqualTo(30);
            assertThat(r.getMaxRenewals()).isEqualTo(DEFAULT_MAX_RENEWALS);
            assertThat(r.getFreeWithdrawalWindowDays()).isEqualTo(DEFAULT_FREE_WITHDRAWAL_WINDOW_DAYS);
            assertThat(r.getCommissionRateTerm1()).isEqualByComparingTo(DEFAULT_COMMISSION_RATE_TERM1);
            assertThat(r.getCommissionRateTerm2()).isEqualByComparingTo(DEFAULT_COMMISSION_RATE_TERM2);
            assertThat(r.getCommissionRateTerm3()).isEqualByComparingTo(DEFAULT_COMMISSION_RATE_TERM3);
            assertThat(r.getWithdrawalFee()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.getDegradedCompensationRate()).isEqualByComparingTo(DEFAULT_DEGRADED_COMPENSATION_RATE);
            assertThat(r.getConditionNote()).isEqualTo("Áo còn mới 90%, không lỗi");
            assertThat(r.getPdfUrl()).isNull();
            assertThat(r.getConfirmedAt()).isNull();

            verify(queueService).dequeue(QueueType.INSPECTION, formId.toString());
        }

        @Test
        @DisplayName("Không truyền termLengthDays: lấy từ system_configs")
        void termLengthDays_null_usesConfig() {
            stubFormFound();
            when(consignmentReceiptRepository.existsByConsignmentForm_Id(formId)).thenReturn(false);
            when(receiptNoGenerator.next()).thenReturn("KG-2026-000002");
            stubConfigDefaults();
            when(systemConfigReader.getInt(eq(TERM_LENGTH_DAYS), anyInt())).thenReturn(45);
            stubReceiptSave(UUID.randomUUID());

            service.completeInspection(
                    formId,
                    completeRequest(InspectionResult.PASS, null, new BigDecimal("100000"), null),
                    moderator);

            ArgumentCaptor<ConsignmentReceipt> captor = ArgumentCaptor.forClass(ConsignmentReceipt.class);
            verify(consignmentReceiptRepository).save(captor.capture());
            assertThat(captor.getValue().getTermLengthDays()).isEqualTo(45);
        }

        @Test
        @DisplayName("Có truyền termLengthDays: dùng giá trị truyền vào, không đọc config")
        void termLengthDays_provided_overridesConfig() {
            stubFormFound();
            when(consignmentReceiptRepository.existsByConsignmentForm_Id(formId)).thenReturn(false);
            when(receiptNoGenerator.next()).thenReturn("KG-2026-000003");
            stubConfigDefaults();
            stubReceiptSave(UUID.randomUUID());

            service.completeInspection(
                    formId,
                    completeRequest(InspectionResult.PASS, null, new BigDecimal("100000"), 60),
                    moderator);

            ArgumentCaptor<ConsignmentReceipt> captor = ArgumentCaptor.forClass(ConsignmentReceipt.class);
            verify(consignmentReceiptRepository).save(captor.capture());
            assertThat(captor.getValue().getTermLengthDays()).isEqualTo(60);
            verify(systemConfigReader, never()).getInt(eq(TERM_LENGTH_DAYS), anyInt());
        }

        @Test
        @DisplayName("Giá sàn và phí rút lấy theo config: ratio 0.60, phí 30000")
        void floorPriceAndWithdrawalFee_followConfig() {
            stubFormFound();
            when(consignmentReceiptRepository.existsByConsignmentForm_Id(formId)).thenReturn(false);
            when(receiptNoGenerator.next()).thenReturn("KG-2026-000004");
            stubConfigDefaults();
            when(systemConfigReader.getDecimal(eq(FLOOR_PRICE_RATIO), any(BigDecimal.class)))
                    .thenReturn(new BigDecimal("0.60"));
            when(systemConfigReader.getDecimal(eq(WITHDRAWAL_FEE), any(BigDecimal.class)))
                    .thenReturn(new BigDecimal("30000"));
            stubReceiptSave(UUID.randomUUID());

            service.completeInspection(formId, validComplete(), moderator);

            ArgumentCaptor<ConsignmentReceipt> captor = ArgumentCaptor.forClass(ConsignmentReceipt.class);
            verify(consignmentReceiptRepository).save(captor.capture());
            assertThat(captor.getValue().getFloorPrice()).isEqualByComparingTo("132000");
            assertThat(captor.getValue().getWithdrawalFee()).isEqualByComparingTo("30000");
        }

        @Test
        @DisplayName("conditionNote chỉ có khoảng trắng: lưu null")
        void blankConditionNote_savedAsNull() {
            stubFormFound();
            when(consignmentReceiptRepository.existsByConsignmentForm_Id(formId)).thenReturn(false);
            when(receiptNoGenerator.next()).thenReturn("KG-2026-000005");
            stubConfigDefaults();
            stubReceiptSave(UUID.randomUUID());

            service.completeInspection(
                    formId,
                    completeRequest(InspectionResult.PASS, "   ", new BigDecimal("100000"), 30),
                    moderator);

            ArgumentCaptor<ConsignmentReceipt> captor = ArgumentCaptor.forClass(ConsignmentReceipt.class);
            verify(consignmentReceiptRepository).save(captor.capture());
            assertThat(captor.getValue().getConditionNote()).isNull();
        }

        @Test
        @DisplayName("result null: 400 INVALID_INSPECTION_RESULT, chưa chạm DB")
        void resultNull_throws() {
            assertAppError(
                    () -> service.completeInspection(
                            formId,
                            completeRequest(null, "ok", new BigDecimal("100000"), 30),
                            moderator),
                    ErrorCode.INVALID_INSPECTION_RESULT);
            verifyNoInteractions(consignmentFormRepository, consignmentReceiptRepository);
        }

        @Test
        @DisplayName("agreedPrice null: 400 INVALID_AGREED_PRICE")
        void agreedPriceNull_throws() {
            assertAppError(
                    () -> service.completeInspection(
                            formId, completeRequest(InspectionResult.PASS, "ok", null, 30), moderator),
                    ErrorCode.INVALID_AGREED_PRICE);
            verifyNoInteractions(consignmentFormRepository, consignmentReceiptRepository);
        }

        @Test
        @DisplayName("agreedPrice = 0: 400 INVALID_AGREED_PRICE")
        void agreedPriceZero_throws() {
            assertAppError(
                    () -> service.completeInspection(
                            formId,
                            completeRequest(InspectionResult.PASS, "ok", BigDecimal.ZERO, 30),
                            moderator),
                    ErrorCode.INVALID_AGREED_PRICE);
        }

        @Test
        @DisplayName("agreedPrice âm: 400 INVALID_AGREED_PRICE")
        void agreedPriceNegative_throws() {
            assertAppError(
                    () -> service.completeInspection(
                            formId,
                            completeRequest(InspectionResult.PASS, "ok", new BigDecimal("-1"), 30),
                            moderator),
                    ErrorCode.INVALID_AGREED_PRICE);
        }

        @Test
        @DisplayName("termLengthDays <= 0: 400 INVALID_TERM_LENGTH_DAYS")
        void termLengthDaysNonPositive_throws() {
            assertAppError(
                    () -> service.completeInspection(
                            formId,
                            completeRequest(InspectionResult.PASS, "ok", new BigDecimal("100000"), 0),
                            moderator),
                    ErrorCode.INVALID_TERM_LENGTH_DAYS);
        }

        @Test
        @DisplayName("Moderator chưa gán kho: 403 MODERATOR_NO_WAREHOUSE")
        void noWarehouse_throws() {
            Account noWh = Account.builder().id(UUID.randomUUID()).build();

            assertAppError(
                    () -> service.completeInspection(formId, validComplete(), noWh),
                    ErrorCode.MODERATOR_NO_WAREHOUSE);
            verifyNoInteractions(consignmentFormRepository);
        }

        @Test
        @DisplayName("Không có form: 404 CONSIGNMENT_FORM_NOT_FOUND")
        void formNotFound_throws() {
            when(consignmentFormRepository.findByIdForUpdate(formId)).thenReturn(Optional.empty());

            assertAppError(
                    () -> service.completeInspection(formId, validComplete(), moderator),
                    ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);
            verify(consignmentReceiptRepository, never()).save(any());
        }

        @Test
        @DisplayName("Form thuộc kho khác: 404 CONSIGNMENT_FORM_NOT_FOUND")
        void formOfOtherWarehouse_treatedAsNotFound() {
            form.setWarehouse(warehouse(2));
            stubFormFound();

            assertAppError(
                    () -> service.completeInspection(formId, validComplete(), moderator),
                    ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);
            verify(consignmentReceiptRepository, never()).save(any());
        }

        @Test
        @DisplayName("Form chưa ở INSPECTING: 409 CONSIGNMENT_FORM_NOT_INSPECTING")
        void formNotInspecting_throws() {
            form.setStatus(ConsignmentFormStatus.APPROVED);
            stubFormFound();

            assertAppError(
                    () -> service.completeInspection(formId, validComplete(), moderator),
                    ErrorCode.CONSIGNMENT_FORM_NOT_INSPECTING);
            verify(consignmentReceiptRepository, never()).save(any());
            verify(queueService, never()).dequeue(any(), any());
        }

        @Test
        @DisplayName("Form đã có phiếu: 409 CONSIGNMENT_RECEIPT_ALREADY_EXISTS")
        void receiptAlreadyExists_throws() {
            stubFormFound();
            when(consignmentReceiptRepository.existsByConsignmentForm_Id(formId)).thenReturn(true);

            assertAppError(
                    () -> service.completeInspection(formId, validComplete(), moderator),
                    ErrorCode.CONSIGNMENT_RECEIPT_ALREADY_EXISTS);
            verify(consignmentReceiptRepository, never()).save(any());
            verify(receiptNoGenerator, never()).next();
            verify(queueService, never()).dequeue(any(), any());
        }
    }
}