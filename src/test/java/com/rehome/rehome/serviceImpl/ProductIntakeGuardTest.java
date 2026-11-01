package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateProductRequest;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentReceiptRepository;
import com.rehome.rehome.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Nhập kho (đăng bán, kể cả ký gửi lại) chỉ khi biên nhận đã được Member xác nhận và đúng kho
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductIntakeGuardTest {

    @Mock ConsignmentReceiptRepository receiptRepository;
    @Mock ProductRepository productRepository;
    @Mock CreateProductRequest request;

    @InjectMocks ProductServiceImpl service;

    private Account moderator(Warehouse warehouse) {
        return Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR).warehouse(warehouse).build();
    }

    private ConsignmentReceipt receipt(Warehouse warehouse, Instant confirmedAt) {
        ConsignmentReceipt receipt = ConsignmentReceipt.builder().id(UUID.randomUUID())
                .warehouse(warehouse).confirmedAt(confirmedAt).build();
        when(receiptRepository.findById(any())).thenReturn(Optional.of(receipt));
        return receipt;
    }

    @Test
    void bienNhanChuaDuocMemberXacNhan_khongNhapKhoDuoc() {
        Warehouse w = Warehouse.builder().id(UUID.randomUUID()).build();
        receipt(w, null);

        AppException ex = assertThrows(AppException.class, () -> service.create(request, moderator(w)));

        assertEquals(ErrorCode.RECEIPT_NOT_CONFIRMED, ex.getErrorCode());
        verify(productRepository, never()).save(any());
    }

    @Test
    void moderatorKhacKho_khongNhapKhoDuoc() {
        Warehouse khoA = Warehouse.builder().id(UUID.randomUUID()).build();
        Warehouse khoB = Warehouse.builder().id(UUID.randomUUID()).build();
        receipt(khoA, Instant.now());

        AppException ex = assertThrows(AppException.class, () -> service.create(request, moderator(khoB)));

        assertEquals(ErrorCode.PRODUCT_NOT_IN_YOUR_WAREHOUSE, ex.getErrorCode());
        verify(productRepository, never()).save(any());
    }

    @Test
    void moderatorChuaGanKho_khongNhapKhoDuoc() {
        Warehouse w = Warehouse.builder().id(UUID.randomUUID()).build();
        receipt(w, Instant.now());

        AppException ex = assertThrows(AppException.class, () -> service.create(request, moderator(null)));

        assertEquals(ErrorCode.PRODUCT_NOT_IN_YOUR_WAREHOUSE, ex.getErrorCode());
    }
}
