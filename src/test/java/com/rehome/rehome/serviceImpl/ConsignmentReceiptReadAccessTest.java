package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.config.ConsignmentProperties;
import com.rehome.rehome.dto.request.CompleteInspectionRequest;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.dto.response.PdfDownload;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.Brand;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.ConsignmentReceiptPdf;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.ConsignmentReceiptPdfRepository;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.ReceiptPdfService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

// Quyền đọc dữ liệu biên nhận theo Role; quyền tải PDF giữ nguyên
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsignmentReceiptReadAccessTest {

    @Mock ConsignmentReceiptRepository consignmentReceiptRepository;
    @Mock ConsignmentReceiptPdfRepository consignmentReceiptPdfRepository;
    @Mock ReceiptPdfService receiptPdfService;
    @Mock ConsignmentFormRepository consignmentFormRepository;
    @Mock AccountRepository accountRepository;
    @Mock ProductRepository productRepository;
    @Mock EndOfTermDecisionRepository endOfTermDecisionRepository;
    @Mock ConsignmentProperties consignmentProperties;

    @InjectMocks ConsignmentReceiptServiceImpl service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private Warehouse warehouse(String name) {
        return Warehouse.builder().id(UUID.randomUUID()).name(name).build();
    }

    private Account account(Role role, Warehouse warehouse) {
        return Account.builder().id(UUID.randomUUID()).email(role + "@test.com")
                .role(role).warehouse(warehouse).build();
    }

    private ConsignmentReceipt receipt(Account owner, Warehouse warehouse) {
        ConsignmentForm form = ConsignmentForm.builder()
                .id(UUID.randomUUID()).member(owner).warehouse(warehouse)
                .productName("Áo khoác Uniqlo").size("M")
                .brand(Brand.builder().id(UUID.randomUUID()).name("Uniqlo").build())
                .status(ConsignmentFormStatus.INSPECTING).build();
        ConsignmentReceipt receipt = ConsignmentReceipt.builder()
                .id(UUID.randomUUID()).receiptNo("KG-2026-000001")
                .consignmentForm(form).member(owner).warehouse(warehouse)
                .conditionNote("Còn mới").agreedPrice(new BigDecimal("450000"))
                .createdAt(Instant.parse("2026-10-26T08:00:00Z")).build();
        when(consignmentReceiptRepository.findById(receipt.getId())).thenReturn(Optional.of(receipt));
        return receipt;
    }

    private static ErrorCode codeOf(AppException ex) {
        return ex.getErrorCode();
    }

    // ---------------------------------------------------------------- đọc dữ liệu

    @Test
    void moderatorCungKho_docThanhCong_vaCoDuTruongMoi() {
        Warehouse w = warehouse("Kho Thủ Đức");
        ConsignmentReceipt receipt = receipt(account(Role.MEMBER, null), w);

        ConsignmentReceiptResponse res = service.getMyReceipt(account(Role.MODERATOR, w), receipt.getId());

        assertEquals("Áo khoác Uniqlo", res.getProductName());
        assertEquals("Uniqlo", res.getBrandName());
        assertEquals("Kho Thủ Đức", res.getWarehouseName());
        assertEquals("Còn mới", res.getConditionNote());
        assertEquals(receipt.getCreatedAt(), res.getCreatedAt());
        assertNull(res.getSku());
    }

    @Test
    void moderatorKhacKho_biTuChoi() {
        ConsignmentReceipt receipt = receipt(account(Role.MEMBER, null), warehouse("Kho A"));
        Account moderatorKhoB = account(Role.MODERATOR, warehouse("Kho B"));

        AppException ex = assertThrows(AppException.class,
                () -> service.getMyReceipt(moderatorKhoB, receipt.getId()));
        assertEquals(ErrorCode.CONSIGNMENT_RECEIPT_FORBIDDEN, codeOf(ex));
    }

    @Test
    void memberDocBienNhanCuaNguoiKhac_biTuChoi() {
        ConsignmentReceipt receipt = receipt(account(Role.MEMBER, null), warehouse("Kho A"));

        AppException ex = assertThrows(AppException.class,
                () -> service.getMyReceipt(account(Role.MEMBER, null), receipt.getId()));
        assertEquals(ErrorCode.CONSIGNMENT_RECEIPT_FORBIDDEN, codeOf(ex));
    }

    @Test
    void memberDocBienNhanCuaMinh_thanhCong() {
        Account owner = account(Role.MEMBER, null);
        ConsignmentReceipt receipt = receipt(owner, warehouse("Kho A"));

        assertNotNull(service.getMyReceipt(owner, receipt.getId()));
    }

    @Test
    void adminDocMoiBienNhan_theoQuyenQuanTri() {
        ConsignmentReceipt receipt = receipt(account(Role.MEMBER, null), warehouse("Kho A"));

        assertNotNull(service.getMyReceipt(account(Role.ADMIN, null), receipt.getId()));
    }

    @Test
    void bienNhanKyGuiLai_coProductIdVaPreviousPrice() {
        Warehouse w = warehouse("Kho A");
        Account owner = account(Role.MEMBER, null);
        ConsignmentReceipt receipt = receipt(owner, w);
        Product product = Product.builder().id("Q7-AK-000845")
                .currentPrice(new BigDecimal("400000")).build();
        when(endOfTermDecisionRepository.findByNewReceipt_Id(receipt.getId()))
                .thenReturn(Optional.of(EndOfTermDecision.builder().product(product).build()));

        ConsignmentReceiptResponse res = service.getMyReceipt(owner, receipt.getId());

        assertEquals("Q7-AK-000845", res.getProductId());
        assertEquals(0, new BigDecimal("400000").compareTo(res.getPreviousPrice()));
    }

    // ---------------------------------------------------------------- tải PDF giữ nguyên

    @Test
    void moderatorKhacKho_van_taiDuocPdf() {
        ConsignmentReceipt receipt = receipt(account(Role.MEMBER, null), warehouse("Kho A"));
        receipt.setConfirmedAt(Instant.now());
        when(consignmentReceiptPdfRepository.findById(receipt.getId()))
                .thenReturn(Optional.of(ConsignmentReceiptPdf.builder().content(new byte[]{1, 2}).build()));

        PdfDownload pdf = service.getReceiptPdf(account(Role.MODERATOR, warehouse("Kho B")), receipt.getId());

        assertEquals("KG-2026-000001.pdf", pdf.fileName());
    }

    // ---------------------------------------------------------------- hoàn tất kiểm định theo kho

    @Test
    void completeInspection_moderatorKhacKho_biTuChoi() {
        Warehouse khoA = warehouse("Kho A");
        ConsignmentForm form = ConsignmentForm.builder().id(UUID.randomUUID())
                .member(account(Role.MEMBER, null)).warehouse(khoA)
                .status(ConsignmentFormStatus.INSPECTING).build();
        when(consignmentFormRepository.findByIdForUpdate(form.getId())).thenReturn(Optional.of(form));

        Account moderatorKhoB = account(Role.MODERATOR, warehouse("Kho B"));
        when(accountRepository.findByEmail(moderatorKhoB.getEmail())).thenReturn(Optional.of(moderatorKhoB));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(moderatorKhoB.getEmail(), null));

        AppException ex = assertThrows(AppException.class,
                () -> service.completeInspection(form.getId(), new CompleteInspectionRequest()));
        assertEquals(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN, codeOf(ex));
    }
}
