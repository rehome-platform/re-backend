package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.config.ConsignmentProperties;
import com.rehome.rehome.dto.request.DecideEndOfTermRequest;
import com.rehome.rehome.dto.request.ReConsignTermsRequest;
import com.rehome.rehome.dto.response.DecideEndOfTermResponse;
import com.rehome.rehome.dto.response.PendingReConsignResponse;
import com.rehome.rehome.dto.response.ReConsignTermsResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Member chọn ký gửi lại -> Moderator chốt giá và điều khoản -> biên nhận mới chờ Member xác nhận
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EndOfTermReConsignTest {

    @Mock EndOfTermDecisionRepository decisionRepository;
    @Mock ProductRepository productRepository;
    @Mock ConsignmentReceiptRepository consignmentReceiptRepository;
    @Spy ConsignmentProperties consignmentProperties = new ConsignmentProperties();

    @InjectMocks EndOfTermDecisionServiceImpl service;

    private Warehouse khoA;
    private Account member;
    private Account moderatorKhoA;
    private ConsignmentReceipt oldReceipt;
    private Product product;

    @BeforeEach
    void setUp() {
        khoA = Warehouse.builder().id(UUID.randomUUID()).name("Kho A").build();
        member = Account.builder().id(UUID.randomUUID()).email("member@test.com").role(Role.MEMBER).build();
        moderatorKhoA = Account.builder().id(UUID.randomUUID()).email("mod@test.com")
                .role(Role.MODERATOR).warehouse(khoA).build();

        oldReceipt = ConsignmentReceipt.builder().id(UUID.randomUUID())
                .member(member).warehouse(khoA).conditionNote("Còn mới")
                .agreedPrice(new BigDecimal("450000")).build();
        product = Product.builder().id("Q7-AK-000845").member(member).warehouse(khoA)
                .consignmentReceipt(oldReceipt).name("Áo khoác Uniqlo")
                .currentPrice(new BigDecimal("400000")).build();

        when(consignmentReceiptRepository.nextReceiptSequence()).thenReturn(7L);
        when(consignmentReceiptRepository.save(any(ConsignmentReceipt.class))).thenAnswer(inv -> {
            ConsignmentReceipt r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
    }

    private EndOfTermDecision decision(EndOfTermChoice choice, Instant deadline) {
        EndOfTermDecision d = EndOfTermDecision.builder().id(UUID.randomUUID())
                .product(product).decision(choice).deadline(deadline).build();
        when(decisionRepository.findByIdForUpdate(d.getId())).thenReturn(Optional.of(d));
        return d;
    }

    private EndOfTermDecision openDecision() {
        return decision(null, Instant.now().plus(10, ChronoUnit.DAYS));
    }

    private DecideEndOfTermRequest choose(EndOfTermChoice choice) {
        DecideEndOfTermRequest r = new DecideEndOfTermRequest();
        r.setDecision(choice);
        return r;
    }

    private ReConsignTermsRequest terms(String agreed) {
        ReConsignTermsRequest r = new ReConsignTermsRequest();
        r.setAgreedPrice(new BigDecimal(agreed));
        return r;
    }

    // ---------------------------------------------------------------- Member chọn

    @Test
    void memberChonKyGuiLai_chuaTaoBienNhan() {
        EndOfTermDecision d = openDecision();

        DecideEndOfTermResponse res = service.decide(member, d.getId(), choose(EndOfTermChoice.RE_CONSIGN));

        assertEquals(EndOfTermChoice.RE_CONSIGN, d.getDecision());
        assertNull(res.getNewReceiptId());
        assertNull(d.getNewReceipt());
        verify(consignmentReceiptRepository, never()).save(any());
    }

    @Test
    void memberChonSauHan_biTuChoi() {
        EndOfTermDecision d = decision(null, Instant.now().minus(1, ChronoUnit.DAYS));

        AppException ex = assertThrows(AppException.class,
                () -> service.decide(member, d.getId(), choose(EndOfTermChoice.RE_CONSIGN)));
        assertEquals(ErrorCode.DECISION_EXPIRED, ex.getErrorCode());
    }

    @Test
    void jobDaDanhDauVoThuaNhan_memberKhongChonDuoc() {
        EndOfTermDecision d = decision(EndOfTermChoice.UNCLAIMED, Instant.now().plus(1, ChronoUnit.DAYS));

        AppException ex = assertThrows(AppException.class,
                () -> service.decide(member, d.getId(), choose(EndOfTermChoice.RE_CONSIGN)));
        assertEquals(ErrorCode.DECISION_EXPIRED, ex.getErrorCode());
    }

    // ---------------------------------------------------------------- Moderator chốt giá

    @Test
    void moderatorCungKho_chotGia_taoBienNhanMoiChoMemberXacNhan() {
        EndOfTermDecision d = decision(EndOfTermChoice.RE_CONSIGN, Instant.now().plus(10, ChronoUnit.DAYS));
        ReConsignTermsRequest req = terms("300000");
        req.setModeratorNote("Giảm giá vì đã qua một kỳ");

        ReConsignTermsResponse res = service.setReConsignTerms(moderatorKhoA, d.getId(), req);

        ArgumentCaptor<ConsignmentReceipt> captor = ArgumentCaptor.forClass(ConsignmentReceipt.class);
        verify(consignmentReceiptRepository).save(captor.capture());
        ConsignmentReceipt created = captor.getValue();

        // Giá lấy từ Moderator, không sao chép giá cũ (450000)
        assertEquals(0, new BigDecimal("300000").compareTo(created.getAgreedPrice()));
        assertEquals(0, new BigDecimal("150000").compareTo(created.getFloorPrice()));   // 50% mặc định
        assertNull(created.getConfirmedAt());                                            // chờ Member xác nhận
        assertSame(moderatorKhoA, created.getModerator());
        assertEquals("Còn mới", created.getConditionNote());
        assertEquals("Giảm giá vì đã qua một kỳ", created.getModeratorNote());
        assertTrue(created.getReceiptNo().startsWith("KG-"));
        assertSame(created, d.getNewReceipt());
        assertEquals(created.getId(), res.getNewReceiptId());
    }

    @Test
    void moderatorChotGia_duocDatGiaSanVaThoiHanRieng() {
        EndOfTermDecision d = decision(EndOfTermChoice.RE_CONSIGN, Instant.now().plus(10, ChronoUnit.DAYS));
        ReConsignTermsRequest req = terms("300000");
        req.setFloorPrice(new BigDecimal("200000"));
        req.setTermLengthDays(45);

        service.setReConsignTerms(moderatorKhoA, d.getId(), req);

        ArgumentCaptor<ConsignmentReceipt> captor = ArgumentCaptor.forClass(ConsignmentReceipt.class);
        verify(consignmentReceiptRepository).save(captor.capture());
        assertEquals(0, new BigDecimal("200000").compareTo(captor.getValue().getFloorPrice()));
        assertEquals(45, captor.getValue().getTermLengthDays());
    }

    @Test
    void moderatorKhacKho_biTuChoi() {
        EndOfTermDecision d = decision(EndOfTermChoice.RE_CONSIGN, Instant.now().plus(10, ChronoUnit.DAYS));
        Account moderatorKhoB = Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR)
                .warehouse(Warehouse.builder().id(UUID.randomUUID()).name("Kho B").build()).build();

        AppException ex = assertThrows(AppException.class,
                () -> service.setReConsignTerms(moderatorKhoB, d.getId(), terms("300000")));

        assertEquals(ErrorCode.END_OF_TERM_DECISION_FORBIDDEN, ex.getErrorCode());
        verify(consignmentReceiptRepository, never()).save(any());
    }

    @Test
    void moderatorChuaGanKho_biTuChoi() {
        EndOfTermDecision d = decision(EndOfTermChoice.RE_CONSIGN, Instant.now().plus(10, ChronoUnit.DAYS));
        Account khongKho = Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR).build();

        AppException ex = assertThrows(AppException.class,
                () -> service.setReConsignTerms(khongKho, d.getId(), terms("300000")));
        assertEquals(ErrorCode.MODERATOR_NO_WAREHOUSE, ex.getErrorCode());
    }

    @Test
    void memberChuaChonKyGuiLai_moderatorKhongChotGiaDuoc() {
        EndOfTermDecision chuaChon = openDecision();
        EndOfTermDecision daRutHang = decision(EndOfTermChoice.TAKE_BACK, Instant.now().plus(5, ChronoUnit.DAYS));

        AppException ex1 = assertThrows(AppException.class,
                () -> service.setReConsignTerms(moderatorKhoA, chuaChon.getId(), terms("300000")));
        AppException ex2 = assertThrows(AppException.class,
                () -> service.setReConsignTerms(moderatorKhoA, daRutHang.getId(), terms("300000")));

        assertEquals(ErrorCode.RE_CONSIGN_NOT_REQUESTED, ex1.getErrorCode());
        assertEquals(ErrorCode.RE_CONSIGN_NOT_REQUESTED, ex2.getErrorCode());
        verify(consignmentReceiptRepository, never()).save(any());
    }

    @Test
    void daChotGiaRoi_khongChotLanHai() {
        EndOfTermDecision d = decision(EndOfTermChoice.RE_CONSIGN, Instant.now().plus(10, ChronoUnit.DAYS));
        d.setNewReceipt(ConsignmentReceipt.builder().id(UUID.randomUUID()).build());

        AppException ex = assertThrows(AppException.class,
                () -> service.setReConsignTerms(moderatorKhoA, d.getId(), terms("300000")));
        assertEquals(ErrorCode.RE_CONSIGN_TERMS_ALREADY_SET, ex.getErrorCode());
        verify(consignmentReceiptRepository, never()).save(any());
    }

    @Test
    void giaSanLonHonGiaThoaThuan_biTuChoi() {
        EndOfTermDecision d = decision(EndOfTermChoice.RE_CONSIGN, Instant.now().plus(10, ChronoUnit.DAYS));
        ReConsignTermsRequest req = terms("300000");
        req.setFloorPrice(new BigDecimal("350000"));

        AppException ex = assertThrows(AppException.class,
                () -> service.setReConsignTerms(moderatorKhoA, d.getId(), req));
        assertEquals(ErrorCode.INVALID_FLOOR_PRICE, ex.getErrorCode());
    }

    // ---------------------------------------------------------------- danh sách chờ chốt giá

    @Test
    void danhSachChoChotGia_chiLayTheoKhoCuaModerator() {
        EndOfTermDecision d = EndOfTermDecision.builder().id(UUID.randomUUID()).product(product)
                .decision(EndOfTermChoice.RE_CONSIGN).decidedAt(Instant.now()).build();
        when(decisionRepository.findPendingPricingByWarehouse(khoA.getId())).thenReturn(List.of(d));

        List<PendingReConsignResponse> list = service.listPendingPricing(moderatorKhoA);

        assertEquals(1, list.size());
        assertEquals("Q7-AK-000845", list.get(0).getProductId());
        assertEquals(0, new BigDecimal("450000").compareTo(list.get(0).getPreviousAgreedPrice()));
        verify(decisionRepository).findPendingPricingByWarehouse(khoA.getId());
    }
}
