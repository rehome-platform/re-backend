package com.rehome.rehome.ServiceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rehome.rehome.config.ConsignmentProperties;
import com.rehome.rehome.dto.request.CompleteInspectionRequest;
import com.rehome.rehome.dto.response.CompleteInspectionResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptConfirmResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.service.ReceiptPdfService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class ConsignmentReceiptServiceImplTest {

    @Mock
    private ConsignmentReceiptRepository consignmentReceiptRepository;

    @Mock
    private ReceiptPdfService receiptPdfService;

    // MỚI: 3 dependency cho completeInspection
    @Mock
    private ConsignmentFormRepository consignmentFormRepository;

    @Mock
    private AccountRepository accountRepository;

    @Spy
    private ConsignmentProperties consignmentProperties = new ConsignmentProperties();

    @InjectMocks
    private ConsignmentReceiptServiceImpl service;

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final UUID NEW_RECEIPT_ID = UUID.randomUUID();

    // MỚI
    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    // SỬA: thêm role MEMBER vì getMyReceipt() có check role
    private Account member() {
        Account account = new Account();
        account.setId(UUID.randomUUID()); // mỗi lần gọi ra một id khác nhau
        account.setRole(Role.MEMBER);
        return account;
    }

    private ConsignmentReceipt unconfirmedReceiptOf(Account owner) {
        return ConsignmentReceipt.builder()
                .id(UUID.randomUUID())
                .receiptNo("RC-2026-000045")
                .member(owner)
                .agreedPrice(new BigDecimal("220000"))
                .floorPrice(new BigDecimal("110000"))
                .withdrawalFee(new BigDecimal("20000"))
                .build(); // confirmedAt, pdfUrl = null
    }

    private void assertAppError(Executable executable, ErrorCode expected) {
        assertThatThrownBy(executable::execute)
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(expected);
    }

    // ------------------------------------------------------------------
    // getMyReceipt (API 19)
    // ------------------------------------------------------------------

    // MỚI
    @Test
    void getMyReceipt_nonMember_throwsNotMemberAccount() {
        Account moderator = new Account();
        moderator.setId(UUID.randomUUID());
        moderator.setRole(Role.MODERATOR);

        assertAppError(() -> service.getMyReceipt(moderator, UUID.randomUUID()),
                ErrorCode.NOT_MEMBER_ACCOUNT);

        verifyNoInteractions(consignmentReceiptRepository);
    }

    @Test
    void getMyReceipt_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(consignmentReceiptRepository.findById(id)).thenReturn(Optional.empty());

        assertAppError(() -> service.getMyReceipt(member(), id),
                ErrorCode.CONSIGNMENT_RECEIPT_NOT_FOUND);

        verify(consignmentReceiptRepository).findById(id);
    }

    @Test
    void getMyReceipt_ownedByAnotherMember_throws403() {
        Account owner = member();
        Account other = member();
        ConsignmentReceipt receipt = unconfirmedReceiptOf(owner);
        when(consignmentReceiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));

        assertAppError(() -> service.getMyReceipt(other, receipt.getId()),
                ErrorCode.CONSIGNMENT_RECEIPT_FORBIDDEN);
    }

    @Test
    void getMyReceipt_ownerNotConfirmed_returnsMappedDetailWithNullConfirmedAtAndPdfUrl() {
        Account owner = member();
        ConsignmentReceipt receipt = unconfirmedReceiptOf(owner);
        when(consignmentReceiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));

        ConsignmentReceiptResponse result = service.getMyReceipt(owner, receipt.getId());

        assertThat(result.getId()).isEqualTo(receipt.getId());
        assertThat(result.getReceiptNo()).isEqualTo("RC-2026-000045");
        assertThat(result.getAgreedPrice()).isEqualByComparingTo("220000");
        assertThat(result.getFloorPrice()).isEqualByComparingTo("110000");
        assertThat(result.getWithdrawalFee()).isEqualByComparingTo("20000");
        // giá trị mặc định từ @Builder.Default
        assertThat(result.getTermLengthDays()).isEqualTo(30);
        assertThat(result.getMaxRenewals()).isEqualTo(2);
        assertThat(result.getFreeWithdrawalWindowDays()).isEqualTo(7);
        assertThat(result.getCommissionRateTerm1()).isEqualByComparingTo("0.10");
        assertThat(result.getCommissionRateTerm2()).isEqualByComparingTo("0.15");
        assertThat(result.getCommissionRateTerm3()).isEqualByComparingTo("0.20");
        assertThat(result.getDegradedCompensationRate()).isEqualByComparingTo("0.50");
        assertThat(result.getConfirmedAt()).isNull();
        assertThat(result.getPdfUrl()).isNull();
    }

    @Test
    void getMyReceipt_ownerConfirmed_returnsConfirmedAtAndPdfUrl() {
        Account owner = member();
        Instant confirmedAt = Instant.parse("2026-10-20T03:00:00Z");
        ConsignmentReceipt receipt = unconfirmedReceiptOf(owner);
        receipt.setConfirmedAt(confirmedAt);
        receipt.setPdfUrl("https://cdn.example.com/receipts/RC-2026-000045.pdf");
        when(consignmentReceiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));

        ConsignmentReceiptResponse result = service.getMyReceipt(owner, receipt.getId());

        assertThat(result.getConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(result.getPdfUrl()).isEqualTo("https://cdn.example.com/receipts/RC-2026-000045.pdf");
    }

    // ------------------------------------------------------------------
    // confirmReceipt (API 20)
    // ------------------------------------------------------------------

    @Test
    void confirmReceipt_notFound_throws404() {
        UUID id = UUID.randomUUID();
        when(consignmentReceiptRepository.findByIdForUpdate(id)).thenReturn(Optional.empty());

        assertAppError(() -> service.confirmReceipt(member(), id),
                ErrorCode.CONSIGNMENT_RECEIPT_NOT_FOUND);

        verifyNoInteractions(receiptPdfService);
    }

    @Test
    void confirmReceipt_ownedByAnotherMember_throws403() {
        Account owner = member();
        Account other = member();
        ConsignmentReceipt receipt = unconfirmedReceiptOf(owner);
        when(consignmentReceiptRepository.findByIdForUpdate(receipt.getId()))
                .thenReturn(Optional.of(receipt));

        assertAppError(() -> service.confirmReceipt(other, receipt.getId()),
                ErrorCode.CONSIGNMENT_RECEIPT_FORBIDDEN);

        assertThat(receipt.getConfirmedAt()).isNull();
        verifyNoInteractions(receiptPdfService);
    }

    @Test
    void confirmReceipt_alreadyConfirmed_throws409() {
        Account owner = member();
        ConsignmentReceipt receipt = unconfirmedReceiptOf(owner);
        Instant earlier = Instant.parse("2026-10-13T08:10:00Z");
        receipt.setConfirmedAt(earlier);
        when(consignmentReceiptRepository.findByIdForUpdate(receipt.getId()))
                .thenReturn(Optional.of(receipt));

        assertAppError(() -> service.confirmReceipt(owner, receipt.getId()),
                ErrorCode.CONSIGNMENT_RECEIPT_ALREADY_CONFIRMED);

        assertThat(receipt.getConfirmedAt()).isEqualTo(earlier); // không bị ghi đè
        verifyNoInteractions(receiptPdfService);
        verify(consignmentReceiptRepository, never()).save(receipt);
    }

    @Test
    void confirmReceipt_owner_confirmsAndGeneratesPdf() {
        Account owner = member();
        ConsignmentReceipt receipt = unconfirmedReceiptOf(owner);
        String pdfUrl = "https://cdn.rehome.vn/receipts/rc-001.pdf";

        when(consignmentReceiptRepository.findByIdForUpdate(receipt.getId()))
                .thenReturn(Optional.of(receipt));
        when(receiptPdfService.generate(receipt)).thenReturn(pdfUrl);

        Instant before = Instant.now();
        ConsignmentReceiptConfirmResponse result = service.confirmReceipt(owner, receipt.getId());
        Instant after = Instant.now();

        // Response
        assertThat(result.getId()).isEqualTo(receipt.getId());
        assertThat(result.getPdfUrl()).isEqualTo(pdfUrl);
        assertThat(result.getConfirmedAt()).isBetween(before, after);

        // Receipt được cập nhật
        assertThat(receipt.getConfirmedAt()).isEqualTo(result.getConfirmedAt());
        assertThat(receipt.getPdfUrl()).isEqualTo(pdfUrl);

        verify(receiptPdfService).generate(receipt);
        verify(consignmentReceiptRepository).save(receipt);
    }

    // ------------------------------------------------------------------
    // completeInspection (API 34) - MỚI
    // ------------------------------------------------------------------

    private ConsignmentForm formWithStatus(ConsignmentFormStatus status) {
        return ConsignmentForm.builder()
                .id(UUID.randomUUID())
                .member(member())
                .warehouse(Warehouse.builder().id(UUID.randomUUID()).build())
                .status(status)
                .build();
    }

    private CompleteInspectionRequest completeRequest(BigDecimal price, Integer termDays) {
        CompleteInspectionRequest r = new CompleteInspectionRequest();
        r.setConditionNote("  Áo còn mới 90%, không lẹi  ");
        r.setAgreedPrice(price);
        r.setTermLengthDays(termDays);
        return r;
    }

    private Account loginAsModerator() {
        Account moderator = new Account();
        moderator.setId(UUID.randomUUID());
        moderator.setRole(Role.MODERATOR);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("moderator1@rehome.com", null, List.of()));
        when(accountRepository.findByEmail("moderator1@rehome.com")).thenReturn(Optional.of(moderator));
        return moderator;
    }

    /** Chạy happy path và trả về receipt đã được truyền vào save(). */
    private ConsignmentReceipt completeAndCapture(ConsignmentForm form, CompleteInspectionRequest req) {
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));
        when(consignmentReceiptRepository.existsByConsignmentFormId(form.getId())).thenReturn(false);
        when(consignmentReceiptRepository.nextReceiptSequence()).thenReturn(7L);
        when(consignmentReceiptRepository.save(any(ConsignmentReceipt.class))).thenAnswer(inv -> {
            ConsignmentReceipt r = inv.getArgument(0);
            r.setId(NEW_RECEIPT_ID);   // giả lập DB sinh id
            return r;
        });

        CompleteInspectionResponse res = service.completeInspection(form.getId(), req);

        assertThat(res.getReceiptId()).isEqualTo(NEW_RECEIPT_ID);
        ArgumentCaptor<ConsignmentReceipt> captor = ArgumentCaptor.forClass(ConsignmentReceipt.class);
        verify(consignmentReceiptRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void completeInspection_success_buildsUnconfirmedReceiptWithSnapshots() {
        ConsignmentForm form = formWithStatus(ConsignmentFormStatus.INSPECTING);
        Account moderator = loginAsModerator();

        ConsignmentReceipt r = completeAndCapture(form, completeRequest(new BigDecimal("220000"), 45));

        assertThat(r.getReceiptNo()).isEqualTo("KG-%d-000007".formatted(Year.now(VN_ZONE).getValue()));
        assertThat(r.getConsignmentForm()).isSameAs(form);
        assertThat(r.getMember()).isSameAs(form.getMember());
        assertThat(r.getWarehouse()).isSameAs(form.getWarehouse());
        assertThat(r.getModerator()).isSameAs(moderator);
        assertThat(r.getConditionNote()).isEqualTo("Áo còn mới 90%, không lẹi");   // đã trim
        assertThat(r.getAgreedPrice()).isEqualByComparingTo("220000");
        assertThat(r.getFloorPrice()).isEqualByComparingTo("110000");              // 50%
        assertThat(r.getTermLengthDays()).isEqualTo(45);

        // snapshot lấy từ cấu hình
        assertThat(r.getMaxRenewals()).isEqualTo(consignmentProperties.getMaxRenewals());
        assertThat(r.getFreeWithdrawalWindowDays()).isEqualTo(consignmentProperties.getFreeWithdrawalWindowDays());
        assertThat(r.getCommissionRateTerm1()).isEqualByComparingTo(consignmentProperties.getCommissionRateTerm1());
        assertThat(r.getCommissionRateTerm2()).isEqualByComparingTo(consignmentProperties.getCommissionRateTerm2());
        assertThat(r.getCommissionRateTerm3()).isEqualByComparingTo(consignmentProperties.getCommissionRateTerm3());
        assertThat(r.getWithdrawalFee()).isEqualByComparingTo(consignmentProperties.getWithdrawalFee());
        assertThat(r.getDegradedCompensationRate())
                .isEqualByComparingTo(consignmentProperties.getDegradedCompensationRate());

        // phiếu mới tạo: chưa xác nhận, chưa có PDF (Member xác nhận ở API 20)
        assertThat(r.getConfirmedAt()).isNull();
        assertThat(r.getPdfUrl()).isNull();
        verifyNoInteractions(receiptPdfService);
    }

    @Test
    void completeInspection_termLengthDaysDefaultsFromConfig_whenNull() {
        loginAsModerator();

        ConsignmentReceipt r = completeAndCapture(
                formWithStatus(ConsignmentFormStatus.INSPECTING), completeRequest(new BigDecimal("100000"), null));

        assertThat(r.getTermLengthDays()).isEqualTo(consignmentProperties.getTermLengthDays());
    }

    @Test
    void completeInspection_floorPriceRoundsHalfUp_toTwoDecimals() {
        loginAsModerator();

        ConsignmentReceipt r = completeAndCapture(
                formWithStatus(ConsignmentFormStatus.INSPECTING), completeRequest(new BigDecimal("100000.01"), 30));

        assertThat(r.getAgreedPrice().scale()).isEqualTo(2);
        assertThat(r.getFloorPrice().scale()).isEqualTo(2);
        assertThat(r.getFloorPrice()).isEqualByComparingTo("50000.01");   // 50000.0050 -> HALF_UP
    }

    @Test
    void completeInspection_formNotFound_throws404() {
        UUID id = UUID.randomUUID();
        when(consignmentFormRepository.findById(id)).thenReturn(Optional.empty());

        assertAppError(() -> service.completeInspection(id, completeRequest(BigDecimal.TEN, 30)),
                ErrorCode.CONSIGNMENT_FORM_NOT_FOUND);

        verify(consignmentReceiptRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = ConsignmentFormStatus.class, names = "INSPECTING", mode = EnumSource.Mode.EXCLUDE)
    void completeInspection_notInspecting_throws409(ConsignmentFormStatus status) {
        ConsignmentForm form = formWithStatus(status);
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));

        assertAppError(() -> service.completeInspection(form.getId(), completeRequest(BigDecimal.TEN, 30)),
                ErrorCode.CONSIGNMENT_FORM_NOT_INSPECTING);

        verify(consignmentReceiptRepository, never()).save(any());
    }

    @Test
    void completeInspection_receiptAlreadyExists_throws409() {
        ConsignmentForm form = formWithStatus(ConsignmentFormStatus.INSPECTING);
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));
        when(consignmentReceiptRepository.existsByConsignmentFormId(form.getId())).thenReturn(true);

        assertAppError(() -> service.completeInspection(form.getId(), completeRequest(BigDecimal.TEN, 30)),
                ErrorCode.CONSIGNMENT_RECEIPT_ALREADY_EXISTS);

        verify(consignmentReceiptRepository, never()).save(any());
    }

    @Test
    void completeInspection_unknownModerator_throwsUnauthorized() {
        ConsignmentForm form = formWithStatus(ConsignmentFormStatus.INSPECTING);
        when(consignmentFormRepository.findById(form.getId())).thenReturn(Optional.of(form));
        when(consignmentReceiptRepository.existsByConsignmentFormId(form.getId())).thenReturn(false);
        when(consignmentReceiptRepository.nextReceiptSequence()).thenReturn(1L);   // được gọi trước currentModerator()
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("ghost@rehome.com", null, List.of()));
        when(accountRepository.findByEmail("ghost@rehome.com")).thenReturn(Optional.empty());

        assertAppError(() -> service.completeInspection(form.getId(), completeRequest(BigDecimal.TEN, 30)),
                ErrorCode.UNAUTHORIZED);

        verify(consignmentReceiptRepository, never()).save(any());
    }
}