package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.service.FileStorageService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReceiptPdfServiceImplTest {

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ReceiptPdfServiceImpl service;

    private ConsignmentReceipt receipt() {
        Account moderator = mock(Account.class);
        when(moderator.getUsername()).thenReturn("Trần Thị Bình");
        Account member = mock(Account.class);
        when(member.getUsername()).thenReturn("Nguyễn Minh Nguyên");

        Warehouse warehouse = mock(Warehouse.class);
        when(warehouse.getName()).thenReturn("Kho Quận 7");
        when(warehouse.getAddress()).thenReturn("123 Nguyễn Văn Linh, Quận 7, TP.HCM");

        ConsignmentForm form = mock(ConsignmentForm.class);
        when(form.getProductName()).thenReturn("Áo khoác denim");

        return ConsignmentReceipt.builder()
                .id(UUID.randomUUID())
                .receiptNo("RC-2026-000045")
                .consignmentForm(form)
                .member(member)
                .moderator(moderator)
                .warehouse(warehouse)
                .agreedPrice(new BigDecimal("220000"))
                .floorPrice(new BigDecimal("110000"))
                .withdrawalFee(new BigDecimal("20000"))
                .confirmedAt(Instant.parse("2026-10-20T03:00:00Z"))
                .build();
    }

    @Test
    void render_returnsValidPdfBytes() {
        byte[] pdf = service.render(receipt());

        assertThat(pdf).isNotEmpty();
        String header = new String(pdf, 0, 5, StandardCharsets.US_ASCII);
        assertThat(header).isEqualTo("%PDF-");
    }

    @Test
    void generate_storesPdfUnderReceiptNoAndReturnsUrl() {
        ConsignmentReceipt receipt = receipt();
        String url = "https://cdn.rehome.vn/receipts/RC-2026-000045.pdf";
        when(fileStorageService.store(eq("receipts/RC-2026-000045.pdf"), any(byte[].class),
                eq("application/pdf"))).thenReturn(url);

        String result = service.generate(receipt);

        assertThat(result).isEqualTo(url);

        ArgumentCaptor<byte[]> captor = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).store(eq("receipts/RC-2026-000045.pdf"), captor.capture(),
                eq("application/pdf"));
        assertThat(new String(captor.getValue(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}