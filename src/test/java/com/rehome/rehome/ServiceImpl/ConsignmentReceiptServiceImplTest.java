package com.rehome.rehome.ServiceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
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

    private void assertAppError(Executable executable, ErrorCode expected) {
        assertThatThrownBy(executable::execute)
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(expected);
    }

    // ------------------------------------------------------------------
    // getMyReceipt
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
        ConsignmentReceipt receipt = ConsignmentReceipt.builder()
                .id(UUID.randomUUID())
                .member(owner)
                .build();
        when(consignmentReceiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));

        assertAppError(() -> service.getMyReceipt(other, receipt.getId()),
                ErrorCode.CONSIGNMENT_RECEIPT_FORBIDDEN);
    }

    @Test
    void getMyReceipt_ownerNotConfirmed_returnsMappedDetailWithNullConfirmedAtAndPdfUrl() {
        Account owner = member();
        ConsignmentReceipt receipt = ConsignmentReceipt.builder()
                .id(UUID.randomUUID())
                .receiptNo("RC-2026-000045")
                .member(owner)
                .agreedPrice(new BigDecimal("220000"))
                .floorPrice(new BigDecimal("110000"))
                .withdrawalFee(new BigDecimal("20000"))
                .build(); // confirmedAt, pdfUrl = null: chưa xác nhận
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
        ConsignmentReceipt receipt = ConsignmentReceipt.builder()
                .id(UUID.randomUUID())
                .receiptNo("RC-2026-000046")
                .member(owner)
                .agreedPrice(new BigDecimal("300000"))
                .floorPrice(new BigDecimal("150000"))
                .withdrawalFee(new BigDecimal("20000"))
                .confirmedAt(confirmedAt)
                .pdfUrl("https://cdn.example.com/receipts/RC-2026-000046.pdf")
                .build();
        when(consignmentReceiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));

        ConsignmentReceiptResponse result = service.getMyReceipt(owner, receipt.getId());

        assertThat(result.getConfirmedAt()).isEqualTo(confirmedAt);
        assertThat(result.getPdfUrl()).isEqualTo("https://cdn.example.com/receipts/RC-2026-000046.pdf");
    }
}