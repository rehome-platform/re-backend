package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.ModeratorConsignmentFormResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.ModeratorResumeStep;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.Brand;
import com.rehome.rehome.entity.marketplace.Category;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.ConsignmentFormPhoto;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.QueueItem;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentFormPhotoRepository;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.ModeratorProfileRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.QueueItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// API dành riêng cho Moderator: danh sách/chi tiết form ký gửi, tab theo stage, resumeStep,
// người phụ trách theo giai đoạn và quy tắc "biên nhận mới nhất"
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModeratorConsignmentFormServiceImplTest {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Mock ConsignmentFormRepository consignmentFormRepository;
    @Mock ConsignmentReceiptRepository consignmentReceiptRepository;
    @Mock ProductRepository productRepository;
    @Mock QueueItemRepository queueItemRepository;
    @Mock ConsignmentFormPhotoRepository photoRepository;
    @Mock MemberProfileRepository memberProfileRepository;
    @Mock ModeratorProfileRepository moderatorProfileRepository;

    @InjectMocks ModeratorConsignmentFormServiceImpl service;

    // ---------------------------------------------------------------- helpers

    private Warehouse warehouse(String name) {
        return Warehouse.builder().id(UUID.randomUUID()).name(name).build();
    }

    private Account account(Role role, Warehouse warehouse) {
        return Account.builder().id(UUID.randomUUID()).email(role + "@test.com")
                .role(role).warehouse(warehouse).build();
    }

    private ConsignmentForm form(Account member, Warehouse warehouse, ConsignmentFormStatus status) {
        return ConsignmentForm.builder()
                .id(UUID.randomUUID())
                .member(member)
                .warehouse(warehouse)
                .status(status)
                .productName("Áo khoác")
                .desiredPrice(BigDecimal.valueOf(500_000))
                .createdAt(Instant.now())
                .build();
    }

    private ConsignmentReceipt receipt(ConsignmentForm form, Account moderator,
                                       Instant createdAt, Instant confirmedAt) {
        return ConsignmentReceipt.builder()
                .id(UUID.randomUUID())
                .receiptNo("KG-2026-" + UUID.randomUUID().toString().substring(0, 6))
                .consignmentForm(form)
                .member(form.getMember())
                .moderator(moderator)
                .warehouse(form.getWarehouse())
                .agreedPrice(BigDecimal.valueOf(400_000))
                .floorPrice(BigDecimal.valueOf(200_000))
                .createdAt(createdAt)
                .confirmedAt(confirmedAt)
                .build();
    }

    private Product product(ConsignmentReceipt receipt, String sku) {
        return Product.builder().id(sku).consignmentReceipt(receipt).name("Áo khoác").build();
    }

    private void stubAll(Warehouse w, ConsignmentForm... forms) {
        when(consignmentFormRepository.findForModeratorExcludingStatus(
                eq(w.getId()), eq(ConsignmentFormStatus.DRAFT), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(forms)));
    }

    private static ErrorCode codeOf(AppException ex) {
        return ex.getErrorCode();
    }

    // ----------------------------------------------------------------- quyền

    @Test
    void list_moderatorChuaGanKho_biTuChoi() {
        AppException ex = assertThrows(AppException.class,
                () -> service.list(account(Role.MODERATOR, null), null, 0, 20));
        assertEquals(ErrorCode.MODERATOR_NO_WAREHOUSE, codeOf(ex));
    }

    @Test
    void list_stageKhongHopLe_tra400() {
        Warehouse w = warehouse("Kho A");
        AppException ex = assertThrows(AppException.class,
                () -> service.list(account(Role.MODERATOR, w), "ABC", 0, 20));
        assertEquals(ErrorCode.INVALID_FORM_STAGE, codeOf(ex));
    }

    // ------------------------------------------------------- chọn query theo stage

    @Test
    void list_khongTruyenStage_layTatCaTruNhap() {
        Warehouse w = warehouse("Kho A");
        stubAll(w);

        PageResponse<ModeratorConsignmentFormResponse> res = service.list(account(Role.MODERATOR, w), null, 0, 20);

        assertTrue(res.getContent().isEmpty());
        verify(consignmentFormRepository).findForModeratorExcludingStatus(
                eq(w.getId()), eq(ConsignmentFormStatus.DRAFT), any(Pageable.class));
    }

    @Test
    void list_stagePending_vaApproved_locTheoStatus() {
        Warehouse w = warehouse("Kho A");
        when(consignmentFormRepository.findForModeratorByStatus(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        Account mod = account(Role.MODERATOR, w);

        service.list(mod, "pending", 0, 20);      // không phân biệt hoa thường
        service.list(mod, " APPROVED ", 0, 20);

        verify(consignmentFormRepository).findForModeratorByStatus(
                eq(w.getId()), eq(ConsignmentFormStatus.PENDING), any(Pageable.class));
        verify(consignmentFormRepository).findForModeratorByStatus(
                eq(w.getId()), eq(ConsignmentFormStatus.APPROVED), any(Pageable.class));
    }

    @Test
    void list_stageInspecting_khongPhuThuocHangDoi() {
        Warehouse w = warehouse("Kho A");
        when(consignmentFormRepository.findInspectionTabForModerator(
                eq(w.getId()), eq(ConsignmentFormStatus.INSPECTING), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(account(Role.MODERATOR, w), "INSPECTING", 0, 20);

        verify(consignmentFormRepository).findInspectionTabForModerator(
                eq(w.getId()), eq(ConsignmentFormStatus.INSPECTING), any(Pageable.class));
        // Không lấy danh sách từ hàng đợi kiểm định
        verify(queueItemRepository, never()).findVisibleByType(any(), any());
        verify(queueItemRepository, never()).findAllVisible(any());
    }

    @Test
    void list_stageStockedToday_khoangThoiGianLaNgayHienTaiTheoGioVietNam() {
        Warehouse w = warehouse("Kho A");
        when(consignmentFormRepository.findStockedBetweenForModerator(
                any(), any(), any(Instant.class), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Instant before = Instant.now();
        service.list(account(Role.MODERATOR, w), "STOCKED_TODAY", 0, 20);
        Instant after = Instant.now();

        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        verify(consignmentFormRepository).findStockedBetweenForModerator(
                eq(w.getId()), eq(ConsignmentFormStatus.DRAFT), start.capture(), end.capture(), any(Pageable.class));

        // Cửa sổ đúng 24 giờ, bắt đầu đúng 00:00 giờ Việt Nam
        assertEquals(Duration.ofHours(24), Duration.between(start.getValue(), end.getValue()));
        assertEquals(LocalTime.MIDNIGHT, start.getValue().atZone(VN_ZONE).toLocalTime());
        // Thời điểm gọi nằm trong cửa sổ (trừ khi chạy test đúng lúc qua nửa đêm VN)
        assertFalse(before.isBefore(start.getValue()) && !after.isBefore(end.getValue()));
        assertTrue(!start.getValue().isAfter(after));
        assertTrue(end.getValue().isAfter(before));
    }

    @Test
    void list_phanTrang_giamSizeToiDaVaPageAm() {
        Warehouse w = warehouse("Kho A");
        stubAll(w);

        service.list(account(Role.MODERATOR, w), null, -5, 1000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(consignmentFormRepository).findForModeratorExcludingStatus(any(), any(), pageable.capture());
        assertEquals(0, pageable.getValue().getPageNumber());
        assertEquals(50, pageable.getValue().getPageSize());
    }

    @Test
    void list_sizeKhongHopLe_dungMacDinh20() {
        Warehouse w = warehouse("Kho A");
        stubAll(w);

        service.list(account(Role.MODERATOR, w), null, 0, 0);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(consignmentFormRepository).findForModeratorExcludingStatus(any(), any(), pageable.capture());
        assertEquals(20, pageable.getValue().getPageSize());
    }

    // ------------------------------------------------------------- resumeStep

    @Test
    void resolveResumeStep_theoStatusKhiChuaCoBienNhan() {
        Account member = account(Role.MEMBER, null);
        Warehouse w = warehouse("Kho A");

        assertEquals(ModeratorResumeStep.REVIEW_FORM,
                ModeratorConsignmentFormServiceImpl.resolveResumeStep(
                        form(member, w, ConsignmentFormStatus.PENDING), null, false));
        assertEquals(ModeratorResumeStep.START_INSPECTION,
                ModeratorConsignmentFormServiceImpl.resolveResumeStep(
                        form(member, w, ConsignmentFormStatus.APPROVED), null, false));
        assertEquals(ModeratorResumeStep.INSPECTION,
                ModeratorConsignmentFormServiceImpl.resolveResumeStep(
                        form(member, w, ConsignmentFormStatus.INSPECTING), null, false));
    }

    @Test
    void resolveResumeStep_theoBienNhanVaSanPham() {
        Account member = account(Role.MEMBER, null);
        Account mod = account(Role.MODERATOR, warehouse("Kho A"));
        ConsignmentForm f = form(member, mod.getWarehouse(), ConsignmentFormStatus.INSPECTING);

        ConsignmentReceipt chuaXacNhan = receipt(f, mod, Instant.now(), null);
        ConsignmentReceipt daXacNhan = receipt(f, mod, Instant.now(), Instant.now());

        assertEquals(ModeratorResumeStep.AWAIT_MEMBER_CONFIRM,
                ModeratorConsignmentFormServiceImpl.resolveResumeStep(f, chuaXacNhan, false));
        assertEquals(ModeratorResumeStep.STOCK_IN,
                ModeratorConsignmentFormServiceImpl.resolveResumeStep(f, daXacNhan, false));
        assertEquals(ModeratorResumeStep.COMPLETED,
                ModeratorConsignmentFormServiceImpl.resolveResumeStep(f, daXacNhan, true));
    }

    @Test
    void resolveResumeStep_formDaDong_khongConBuoc() {
        Account member = account(Role.MEMBER, null);
        Warehouse w = warehouse("Kho A");

        for (ConsignmentFormStatus s : List.of(ConsignmentFormStatus.REJECTED,
                ConsignmentFormStatus.EXPIRED, ConsignmentFormStatus.CANCELLED)) {
            assertEquals(ModeratorResumeStep.NONE,
                    ModeratorConsignmentFormServiceImpl.resolveResumeStep(form(member, w, s), null, false));
        }
    }

    // -------------------------------------------------- biên nhận mới nhất, SKU

    @Test
    void list_formCoNhieuBienNhan_layBienNhanMoiNhat() {
        Warehouse w = warehouse("Kho A");
        Account mod = account(Role.MODERATOR, w);
        ConsignmentForm f = form(account(Role.MEMBER, null), w, ConsignmentFormStatus.INSPECTING);
        stubAll(w, f);

        ConsignmentReceipt cu = receipt(f, mod, Instant.now().minus(Duration.ofDays(90)),
                Instant.now().minus(Duration.ofDays(89)));
        ConsignmentReceipt moi = receipt(f, mod, Instant.now(), null);
        // Cố tình trả cả hai để kiểm tra service tự chọn đúng biên nhận mới nhất
        when(consignmentReceiptRepository.findLatestByFormIds(any())).thenReturn(List.of(cu, moi));
        when(productRepository.findByConsignmentReceipt_IdIn(any())).thenReturn(List.of());

        ModeratorConsignmentFormResponse item = service.list(mod, null, 0, 20).getContent().get(0);

        assertEquals(moi.getId(), item.getReceiptId());
        assertNull(item.getConfirmedAt());                  // confirmedAt của biên nhận mới, không phải của biên nhận cũ
        assertNull(item.getProductSku());
        assertEquals(ModeratorResumeStep.AWAIT_MEMBER_CONFIRM, item.getResumeStep());
        // Sản phẩm chỉ được tra theo biên nhận mới nhất
        verify(productRepository).findByConsignmentReceipt_IdIn(
                argThat(ids -> ids.size() == 1 && ids.contains(moi.getId())));
    }

    @Test
    void list_daXacNhanChuaNhapKho_resumeStepLaStockIn() {
        Warehouse w = warehouse("Kho A");
        Account mod = account(Role.MODERATOR, w);
        ConsignmentForm f = form(account(Role.MEMBER, null), w, ConsignmentFormStatus.INSPECTING);
        stubAll(w, f);

        Instant confirmed = Instant.now();
        ConsignmentReceipt r = receipt(f, mod, Instant.now(), confirmed);
        when(consignmentReceiptRepository.findLatestByFormIds(any())).thenReturn(List.of(r));
        when(productRepository.findByConsignmentReceipt_IdIn(any())).thenReturn(List.of());

        ModeratorConsignmentFormResponse item = service.list(mod, null, 0, 20).getContent().get(0);

        assertEquals(r.getId(), item.getReceiptId());
        assertEquals(confirmed, item.getConfirmedAt());
        assertNull(item.getProductSku());
        assertEquals(ModeratorResumeStep.STOCK_IN, item.getResumeStep());
    }

    @Test
    void list_daNhapKho_coProductSkuVaCompleted() {
        Warehouse w = warehouse("Kho A");
        Account mod = account(Role.MODERATOR, w);
        ConsignmentForm f = form(account(Role.MEMBER, null), w, ConsignmentFormStatus.INSPECTING);
        stubAll(w, f);

        ConsignmentReceipt r = receipt(f, mod, Instant.now(), Instant.now());
        when(consignmentReceiptRepository.findLatestByFormIds(any())).thenReturn(List.of(r));
        when(productRepository.findByConsignmentReceipt_IdIn(any()))
                .thenReturn(List.of(product(r, "Q7-AK-000845")));

        ModeratorConsignmentFormResponse item = service.list(mod, null, 0, 20).getContent().get(0);

        assertEquals("Q7-AK-000845", item.getProductSku());
        assertEquals(ModeratorResumeStep.COMPLETED, item.getResumeStep());
    }

    // ------------------------------------------------ người phụ trách theo giai đoạn

    @Test
    void list_truocBienNhan_nguoiPhuTrachLayTuClaimHangDoi_uuTienKiemDinh() {
        Warehouse w = warehouse("Kho A");
        Account viewer = account(Role.MODERATOR, w);
        Account modKiemDinh = account(Role.MODERATOR, w);
        Account modDuyet = account(Role.MODERATOR, w);
        ConsignmentForm f = form(account(Role.MEMBER, null), w, ConsignmentFormStatus.INSPECTING);
        stubAll(w, f);

        when(consignmentReceiptRepository.findLatestByFormIds(any())).thenReturn(List.of());
        when(queueItemRepository.findByTypesAndRefIds(any(), any())).thenReturn(List.of(
                QueueItem.builder().type(QueueType.FORM_REVIEW).refId(f.getId().toString()).claimedBy(modDuyet).build(),
                QueueItem.builder().type(QueueType.INSPECTION).refId(f.getId().toString()).claimedBy(modKiemDinh).build()));
        when(moderatorProfileRepository.findAllById(any())).thenReturn(List.of(
                ModeratorProfile.builder().accountId(modKiemDinh.getId())
                        .fullName("Nguyễn Kiểm Định").moderatorCode("MOD-01").build()));

        ModeratorConsignmentFormResponse item = service.list(viewer, null, 0, 20).getContent().get(0);

        assertNotNull(item.getModerator());
        assertEquals(modKiemDinh.getId(), item.getModerator().getId());
        assertEquals("Nguyễn Kiểm Định", item.getModerator().getFullName());
        assertEquals("MOD-01", item.getModerator().getModeratorCode());
        assertEquals(ModeratorResumeStep.INSPECTION, item.getResumeStep());
    }

    @Test
    void list_truocBienNhan_hangDoiChuaAiNhan_khongCoNguoiPhuTrach() {
        Warehouse w = warehouse("Kho A");
        ConsignmentForm f = form(account(Role.MEMBER, null), w, ConsignmentFormStatus.PENDING);
        stubAll(w, f);

        when(consignmentReceiptRepository.findLatestByFormIds(any())).thenReturn(List.of());
        when(queueItemRepository.findByTypesAndRefIds(any(), any())).thenReturn(List.of(
                QueueItem.builder().type(QueueType.FORM_REVIEW).refId(f.getId().toString()).build()));

        ModeratorConsignmentFormResponse item = service.list(account(Role.MODERATOR, w), null, 0, 20)
                .getContent().get(0);

        assertNull(item.getModerator());
        assertEquals(ModeratorResumeStep.REVIEW_FORM, item.getResumeStep());
    }

    @Test
    void list_sauBienNhan_nguoiPhuTrachLayTuBienNhan_khongTraHangDoi() {
        Warehouse w = warehouse("Kho A");
        Account viewer = account(Role.MODERATOR, w);
        Account modLapBienNhan = account(Role.MODERATOR, w);
        ConsignmentForm f = form(account(Role.MEMBER, null), w, ConsignmentFormStatus.INSPECTING);
        stubAll(w, f);

        ConsignmentReceipt r = receipt(f, modLapBienNhan, Instant.now(), null);
        when(consignmentReceiptRepository.findLatestByFormIds(any())).thenReturn(List.of(r));
        when(moderatorProfileRepository.findAllById(any())).thenReturn(List.of(
                ModeratorProfile.builder().accountId(modLapBienNhan.getId())
                        .fullName("Trần Lập Phiếu").moderatorCode("MOD-02").build()));

        ModeratorConsignmentFormResponse item = service.list(viewer, null, 0, 20).getContent().get(0);

        assertEquals(modLapBienNhan.getId(), item.getModerator().getId());
        assertEquals("Trần Lập Phiếu", item.getModerator().getFullName());
        // Đã có biên nhận thì không phụ thuộc hàng đợi (hàng đợi có thể đã bị xóa)
        verify(queueItemRepository, never()).findByTypesAndRefIds(any(), any());
    }

    // ------------------------------------------------------------ member, ảnh

    @Test
    void list_coThongTinMemberVaAnhDaiDien() {
        Warehouse w = warehouse("Kho A");
        Account member = Account.builder().id(UUID.randomUUID()).email("m@test.com")
                .role(Role.MEMBER).phone("0901234567").build();
        ConsignmentForm f = form(member, w, ConsignmentFormStatus.PENDING);
        stubAll(w, f);

        when(memberProfileRepository.findAllById(any())).thenReturn(List.of(
                MemberProfile.builder().accountId(member.getId()).account(member)
                        .fullName("Lê Văn Member").memberCode("MB-0001").build()));
        when(photoRepository.findByForm_IdInOrderByDisplayOrderAscIdAsc(any())).thenReturn(List.of(
                ConsignmentFormPhoto.builder().form(f).imageUrl("https://img/1.jpg").displayOrder(0).build(),
                ConsignmentFormPhoto.builder().form(f).imageUrl("https://img/2.jpg").displayOrder(1).build()));

        ModeratorConsignmentFormResponse item = service.list(account(Role.MODERATOR, w), null, 0, 20)
                .getContent().get(0);

        assertEquals("Lê Văn Member", item.getMember().getFullName());
        assertEquals("MB-0001", item.getMember().getMemberCode());
        assertEquals("0901234567", item.getMember().getPhone());
        assertEquals("https://img/1.jpg", item.getThumbnailUrl());
        assertNull(item.getPhotoUrls());                    // ảnh đầy đủ chỉ có ở chi tiết
        assertNull(item.getReceipt());
    }

    // ----------------------------------------------------------------- chi tiết

    private ConsignmentForm detailForm(Account member, Warehouse w, ConsignmentFormStatus status) {
        ConsignmentForm f = form(member, w, status);
        f.setDescription("Còn mới 90%");
        f.setCategory(Category.builder().id(UUID.randomUUID()).name("Áo").build());
        f.setBrand(Brand.builder().id(UUID.randomUUID()).name("Uniqlo").build());
        when(consignmentFormRepository.findWithDetailsById(f.getId())).thenReturn(Optional.of(f));
        return f;
    }

    @Test
    void getDetail_khongTimThay_404() {
        UUID id = UUID.randomUUID();
        when(consignmentFormRepository.findWithDetailsById(id)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> service.getDetail(account(Role.MODERATOR, warehouse("Kho A")), id));
        assertEquals(ErrorCode.CONSIGNMENT_FORM_NOT_FOUND, codeOf(ex));
    }

    @Test
    void getDetail_moderatorChuaGanKho_biTuChoi() {
        Warehouse w = warehouse("Kho A");
        ConsignmentForm f = detailForm(account(Role.MEMBER, null), w, ConsignmentFormStatus.PENDING);

        AppException ex = assertThrows(AppException.class,
                () -> service.getDetail(account(Role.MODERATOR, null), f.getId()));
        assertEquals(ErrorCode.MODERATOR_NO_WAREHOUSE, codeOf(ex));
    }

    @Test
    void getDetail_formNhapCuaMember_biTuChoi() {
        Warehouse w = warehouse("Kho A");
        ConsignmentForm f = detailForm(account(Role.MEMBER, null), w, ConsignmentFormStatus.DRAFT);

        AppException ex = assertThrows(AppException.class,
                () -> service.getDetail(account(Role.MODERATOR, w), f.getId()));
        assertEquals(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN, codeOf(ex));
    }

    @Test
    void getDetail_formKhoKhac_biTuChoi() {
        ConsignmentForm f = detailForm(account(Role.MEMBER, null), warehouse("Kho A"), ConsignmentFormStatus.PENDING);

        AppException ex = assertThrows(AppException.class,
                () -> service.getDetail(account(Role.MODERATOR, warehouse("Kho B")), f.getId()));
        assertEquals(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN, codeOf(ex));
    }

    @Test
    void getDetail_formChuaCoKho_biTuChoi() {
        ConsignmentForm f = detailForm(account(Role.MEMBER, null), null, ConsignmentFormStatus.PENDING);

        AppException ex = assertThrows(AppException.class,
                () -> service.getDetail(account(Role.MODERATOR, warehouse("Kho A")), f.getId()));
        assertEquals(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN, codeOf(ex));
    }

    @Test
    void getDetail_thanhCong_coDayDuBienNhanVaSanPham() {
        Warehouse w = warehouse("Kho A");
        w.setAddress("12 Nguyễn Huệ");
        Account mod = account(Role.MODERATOR, w);
        ConsignmentForm f = detailForm(account(Role.MEMBER, null), w, ConsignmentFormStatus.INSPECTING);

        ConsignmentReceipt cu = receipt(f, mod, Instant.now().minus(Duration.ofDays(120)),
                Instant.now().minus(Duration.ofDays(119)));
        ConsignmentReceipt moi = receipt(f, mod, Instant.now(), Instant.now());
        when(consignmentReceiptRepository.findLatestByFormIds(any())).thenReturn(List.of(cu, moi));
        when(productRepository.findByConsignmentReceipt_IdIn(any()))
                .thenReturn(List.of(product(moi, "Q7-AK-000900")));
        when(photoRepository.findByForm_IdInOrderByDisplayOrderAscIdAsc(any())).thenReturn(List.of(
                ConsignmentFormPhoto.builder().form(f).imageUrl("https://img/1.jpg").displayOrder(0).build()));

        ModeratorConsignmentFormResponse res = service.getDetail(mod, f.getId());

        assertEquals(f.getId(), res.getId());
        assertEquals("Còn mới 90%", res.getDescription());
        assertEquals("Áo", res.getCategoryName());
        assertEquals("Uniqlo", res.getBrandName());
        assertEquals("12 Nguyễn Huệ", res.getWarehouse().getAddress());
        assertEquals(List.of("https://img/1.jpg"), res.getPhotoUrls());
        // Chi tiết dùng cùng quy tắc biên nhận mới nhất như danh sách
        assertEquals(moi.getId(), res.getReceiptId());
        assertEquals(moi.getId(), res.getReceipt().getId());
        assertEquals("Q7-AK-000900", res.getProductSku());
        assertEquals("Q7-AK-000900", res.getProduct().getSku());
        assertEquals(ModeratorResumeStep.COMPLETED, res.getResumeStep());
    }

    @Test
    void getDetail_vaList_cungMotQuyTacChonBienNhan() {
        Warehouse w = warehouse("Kho A");
        Account mod = account(Role.MODERATOR, w);
        ConsignmentForm f = detailForm(account(Role.MEMBER, null), w, ConsignmentFormStatus.INSPECTING);
        stubAll(w, f);

        ConsignmentReceipt cu = receipt(f, mod, Instant.now().minus(Duration.ofDays(30)), Instant.now());
        ConsignmentReceipt moi = receipt(f, mod, Instant.now(), null);
        when(consignmentReceiptRepository.findLatestByFormIds(any())).thenReturn(List.of(moi, cu));

        ModeratorConsignmentFormResponse fromList = service.list(mod, null, 0, 20).getContent().get(0);
        ModeratorConsignmentFormResponse fromDetail = service.getDetail(mod, f.getId());

        assertEquals(fromList.getReceiptId(), fromDetail.getReceiptId());
        assertEquals(fromList.getConfirmedAt(), fromDetail.getConfirmedAt());
        assertEquals(fromList.getResumeStep(), fromDetail.getResumeStep());
        assertEquals(moi.getId(), fromDetail.getReceiptId());
    }
}