package com.rehome.rehome.serviceImpl;

import com.lowagie.text.pdf.PdfReader;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Yêu cầu: /fonts/NotoSans-Regular.ttf và /fonts/NotoSans-Bold.ttf phải có trên classpath
 * (src/main/resources/fonts hoặc src/test/resources/fonts).
 */
@ExtendWith(MockitoExtension.class)
class SkuLabelPdfServiceImplTest {

    @Mock private FileStorageService fileStorageService;
    @InjectMocks private SkuLabelPdfServiceImpl service;

    private Product product(String id, String name, String size) {
        Product p = mock(Product.class);
        when(p.getId()).thenReturn(id);
        if (name != null || size != null) {
            when(p.getName()).thenReturn(name);
            when(p.getSize()).thenReturn(size);
        }
        return p;
    }

    private void assertValidLabelPdf(byte[] pdf) throws Exception {
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        PdfReader reader = new PdfReader(pdf);
        try {
            assertThat(reader.getNumberOfPages()).isEqualTo(1);
            // Tem 70x50mm ~ 198.5 x 141.7 pt
            assertThat(reader.getPageSize(1).getWidth()).isEqualTo(198.5f);
            assertThat(reader.getPageSize(1).getHeight()).isEqualTo(141.7f);
        } finally {
            reader.close();
        }
    }

    // ---------------------------------------------------------------- render

    @Test
    @DisplayName("render: có size -> sinh PDF hợp lệ 1 trang, đúng kích thước tem")
    void render_withSize_ok() throws Exception {
        Product p = product("SKU-0001", "Áo khoác jeans", "M");

        assertValidLabelPdf(service.render(p));
    }

    @Test
    @DisplayName("render: size null -> vẫn sinh PDF")
    void render_nullSize_ok() throws Exception {
        Product p = product("SKU-0002", "Váy hoa nhí", null);

        assertValidLabelPdf(service.render(p));
    }

    @Test
    @DisplayName("render: size blank -> vẫn sinh PDF")
    void render_blankSize_ok() throws Exception {
        Product p = product("SKU-0003", "Quần tây", "   ");

        assertValidLabelPdf(service.render(p));
    }

    @Test
    @DisplayName("render: tên tiếng Việt có dấu, tên dài -> không lỗi font")
    void render_vietnameseLongName_ok() throws Exception {
        Product p = product("SKU-0004",
                "Đầm công sở thanh lịch Nguyễn Thị Ánh Tuyết phiên bản giới hạn mùa thu đông", "XL");

        assertValidLabelPdf(service.render(p));
    }

    @Test
    @DisplayName("render: id null -> QR lỗi -> RECEIPT_PDF_GENERATION_FAILED")
    void render_nullId_throwsGenerationFailed() {
        Product p = mock(Product.class);
        when(p.getId()).thenReturn(null);

        assertThatThrownBy(() -> service.render(p))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RECEIPT_PDF_GENERATION_FAILED);
    }

    // ---------------------------------------------------------------- generate

    @Test
    @DisplayName("generate: lưu PDF vào labels/{id}.pdf và trả về URL từ storage")
    void generate_storesPdfAndReturnsUrl() {
        Product p = product("SKU-0005", "Áo thun basic", "S");
        when(fileStorageService.store(eq("labels/SKU-0005.pdf"), org.mockito.ArgumentMatchers.any(byte[].class),
                eq("application/pdf")))
                .thenReturn("https://cdn.example.com/labels/SKU-0005.pdf");

        String url = service.generate(p);

        assertThat(url).isEqualTo("https://cdn.example.com/labels/SKU-0005.pdf");

        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).store(eq("labels/SKU-0005.pdf"), bytes.capture(), eq("application/pdf"));
        assertThat(new String(bytes.getValue(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}