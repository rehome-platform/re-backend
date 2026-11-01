package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.config.ConsignmentProperties;
import com.rehome.rehome.dto.response.ConsignmentFormDetailResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.ConsignmentFormMapper;
import com.rehome.rehome.repository.*;
import com.rehome.rehome.service.QueueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Quyền đọc form ký gửi theo Role, và Moderator chỉ thao tác form thuộc kho mình
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConsignmentFormReadAccessTest {

    @Mock ConsignmentFormRepository consignmentFormRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock BrandRepository brandRepository;
    @Mock WarehouseRepository warehouseRepository;
    @Mock ConsignmentFormPhotoRepository photoRepository;
    @Mock ConsignmentFormMapper consignmentFormMapper;
    @Mock ConsignmentReceiptRepository consignmentReceiptRepository;
    @Mock ConsignmentProperties consignmentProperties;
    @Mock AccountRepository accountRepository;
    @Mock QueueService queueService;

    @InjectMocks ConsignmentFormServiceImpl service;

    private Warehouse warehouse(String name) {
        return Warehouse.builder().id(UUID.randomUUID()).name(name).build();
    }

    private Account account(Role role, Warehouse warehouse) {
        return Account.builder().id(UUID.randomUUID()).email(role + "@test.com")
                .role(role).warehouse(warehouse).build();
    }

    private ConsignmentForm form(Account owner, Warehouse warehouse, ConsignmentFormStatus status) {
        ConsignmentForm form = ConsignmentForm.builder()
                .id(UUID.randomUUID()).member(owner).warehouse(warehouse).status(status).build();
        when(consignmentFormRepository.findWithDetailsById(form.getId())).thenReturn(Optional.of(form));
        when(consignmentFormRepository.findByIdForUpdate(form.getId())).thenReturn(Optional.of(form));
        when(consignmentFormMapper.toDetail(any(), any()))
                .thenReturn(ConsignmentFormDetailResponse.builder().id(form.getId()).build());
        return form;
    }

    private static ErrorCode codeOf(AppException ex) {
        return ex.getErrorCode();
    }

    // ---------------------------------------------------------------- đọc form

    @Test
    void moderatorCungKho_docThanhCong() {
        Warehouse w = warehouse("Kho Thủ Đức");
        ConsignmentForm form = form(account(Role.MEMBER, null), w, ConsignmentFormStatus.PENDING);

        assertNotNull(service.getMyForm(account(Role.MODERATOR, w), form.getId()));
    }

    @Test
    void moderatorKhacKho_biTuChoi() {
        ConsignmentForm form = form(account(Role.MEMBER, null), warehouse("Kho A"), ConsignmentFormStatus.PENDING);
        Account moderatorKhoB = account(Role.MODERATOR, warehouse("Kho B"));

        AppException ex = assertThrows(AppException.class,
                () -> service.getMyForm(moderatorKhoB, form.getId()));
        assertEquals(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN, codeOf(ex));
    }

    @Test
    void moderatorChuaGanKho_biTuChoi() {
        ConsignmentForm form = form(account(Role.MEMBER, null), warehouse("Kho A"), ConsignmentFormStatus.PENDING);

        AppException ex = assertThrows(AppException.class,
                () -> service.getMyForm(account(Role.MODERATOR, null), form.getId()));
        assertEquals(ErrorCode.MODERATOR_NO_WAREHOUSE, codeOf(ex));
    }

    @Test
    void moderatorKhongDocDuocFormNhap() {
        Warehouse w = warehouse("Kho A");
        ConsignmentForm form = form(account(Role.MEMBER, null), w, ConsignmentFormStatus.DRAFT);

        AppException ex = assertThrows(AppException.class,
                () -> service.getMyForm(account(Role.MODERATOR, w), form.getId()));
        assertEquals(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN, codeOf(ex));
    }

    @Test
    void memberDocFormCuaMinh_thanhCong() {
        Account owner = account(Role.MEMBER, null);
        ConsignmentForm form = form(owner, warehouse("Kho A"), ConsignmentFormStatus.PENDING);

        assertNotNull(service.getMyForm(owner, form.getId()));
    }

    @Test
    void memberDocFormCuaNguoiKhac_biTuChoi() {
        ConsignmentForm form = form(account(Role.MEMBER, null), warehouse("Kho A"), ConsignmentFormStatus.PENDING);
        Account nguoiKhac = account(Role.MEMBER, null);

        AppException ex = assertThrows(AppException.class,
                () -> service.getMyForm(nguoiKhac, form.getId()));
        assertEquals(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN, codeOf(ex));
    }

    @Test
    void adminDocMoiForm_theoQuyenQuanTri() {
        ConsignmentForm form = form(account(Role.MEMBER, null), warehouse("Kho A"), ConsignmentFormStatus.PENDING);

        assertNotNull(service.getMyForm(account(Role.ADMIN, null), form.getId()));
    }

    // ------------------------------------------------- thao tác Moderator theo kho

    @Test
    void approve_moderatorCungKho_thanhCong() {
        Warehouse w = warehouse("Kho A");
        ConsignmentForm form = form(account(Role.MEMBER, null), w, ConsignmentFormStatus.PENDING);

        service.approve(form.getId(), account(Role.MODERATOR, w));

        assertEquals(ConsignmentFormStatus.APPROVED, form.getStatus());
    }

    @Test
    void approve_moderatorKhacKho_biTuChoiVaKhongDoiTrangThai() {
        ConsignmentForm form = form(account(Role.MEMBER, null), warehouse("Kho A"), ConsignmentFormStatus.PENDING);
        Account moderatorKhoB = account(Role.MODERATOR, warehouse("Kho B"));

        AppException ex = assertThrows(AppException.class,
                () -> service.approve(form.getId(), moderatorKhoB));

        assertEquals(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN, codeOf(ex));
        assertEquals(ConsignmentFormStatus.PENDING, form.getStatus());
    }

    @Test
    void reject_moderatorKhacKho_biTuChoi() {
        ConsignmentForm form = form(account(Role.MEMBER, null), warehouse("Kho A"), ConsignmentFormStatus.PENDING);
        Account moderatorKhoB = account(Role.MODERATOR, warehouse("Kho B"));

        AppException ex = assertThrows(AppException.class,
                () -> service.reject(form.getId(), null, moderatorKhoB));
        assertEquals(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN, codeOf(ex));
    }

    @Test
    void startInspection_moderatorKhacKho_biTuChoi() {
        ConsignmentForm form = form(account(Role.MEMBER, null), warehouse("Kho A"), ConsignmentFormStatus.APPROVED);
        Account moderatorKhoB = account(Role.MODERATOR, warehouse("Kho B"));

        AppException ex = assertThrows(AppException.class,
                () -> service.startInspection(form.getId(), moderatorKhoB));
        assertEquals(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN, codeOf(ex));
    }

    @Test
    void returnOnsite_moderatorKhacKho_biTuChoi() {
        ConsignmentForm form = form(account(Role.MEMBER, null), warehouse("Kho A"), ConsignmentFormStatus.INSPECTING);
        Account moderatorKhoB = account(Role.MODERATOR, warehouse("Kho B"));

        AppException ex = assertThrows(AppException.class,
                () -> service.returnOnsite(form.getId(), null, moderatorKhoB));
        assertEquals(ErrorCode.CONSIGNMENT_FORM_FORBIDDEN, codeOf(ex));
    }
}
