package com.rehome.rehome.ServiceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.rehome.rehome.dto.response.ConsignmentReceiptConfirmResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.service.ReceiptPdfService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsignmentReceiptServiceImplTest {

    @Mock
    private ConsignmentReceiptRepository consignmentReceiptRepository;

    @Mock
    private ReceiptPdfService receiptPdfService;

    @InjectMocks
    private ConsignmentReceiptServiceImpl service;

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Account member() {
        Account account = new Account();
        account.setId(UUID.randomUUID()); // mỗi lần gọi ra một id khác nhau
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
}