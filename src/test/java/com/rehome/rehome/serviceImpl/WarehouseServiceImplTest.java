package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.marketplace.response.WarehouseResponse;
import com.rehome.rehome.dto.response.MyWarehouseResponse;
import com.rehome.rehome.dto.response.WarehouseNearestResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WarehouseServiceImplTest {

    @Mock
    private WarehouseRepository warehouseRepository;
    // Thiếu mock này là nguyên nhân gây NullPointerException ở getManagedWarehouses
    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private WarehouseServiceImpl service;

    // ---------- helpers ----------

    private void assertAppException(ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(expected);
    }

    private Warehouse warehouseAt(double lat, double lng) {
        Warehouse w = mock(Warehouse.class);
        when(w.getName()).thenReturn("Kho " + lat);
        when(w.getAddress()).thenReturn("Địa chỉ " + lat);
        when(w.getLatitude()).thenReturn(BigDecimal.valueOf(lat));
        when(w.getLongitude()).thenReturn(BigDecimal.valueOf(lng));
        return w;
    }

    // ================= getNearest =================

    @ParameterizedTest
    @CsvSource({
            "NaN, 106.0",
            "10.0, NaN",
            "90.1, 106.0",
            "-90.1, 106.0",
            "10.0, 180.1",
            "10.0, -180.1"
    })
    void getNearest_throwsInvalidCoordinates_whenOutOfRange(double lat, double lng) {
        assertAppException(() -> service.getNearest(lat, lng), ErrorCode.INVALID_COORDINATES);

        verifyNoInteractions(warehouseRepository);
    }

    @Test
    void getNearest_sortsByDistanceAscending() {
        Warehouse far = warehouseAt(11.0, 106.0);    // ~111 km
        Warehouse near = warehouseAt(10.0, 106.0);   // 0 km
        Warehouse mid = warehouseAt(10.5, 106.0);    // ~55.6 km
        when(warehouseRepository.findByStatus(CatalogStatus.ACTIVE)).thenReturn(List.of(far, near, mid));

        List<WarehouseNearestResponse> result = service.getNearest(10.0, 106.0);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).distanceKm()).isLessThan(1.0);
        assertThat(result.get(1).distanceKm()).isBetween(55.0, 56.5);
        assertThat(result.get(2).distanceKm()).isBetween(110.0, 112.0);
        assertThat(result).extracting(WarehouseNearestResponse::distanceKm).isSorted();
    }

    @Test
    void getNearest_limitsResultsTo20() {
        List<Warehouse> many = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            many.add(warehouseAt(10.0 + i * 0.01, 106.0));
        }
        when(warehouseRepository.findByStatus(CatalogStatus.ACTIVE)).thenReturn(many);

        List<WarehouseNearestResponse> result = service.getNearest(10.0, 106.0);

        assertThat(result).hasSize(20);
    }

    @Test
    void getNearest_returnsEmpty_whenNoActiveWarehouse() {
        when(warehouseRepository.findByStatus(CatalogStatus.ACTIVE)).thenReturn(List.of());

        assertThat(service.getNearest(10.0, 106.0)).isEmpty();
    }

    // ================= getActiveWarehouses =================

    @Test
    void getActiveWarehouses_mapsAllActiveWarehouses() {
        Warehouse a = warehouseAt(10.0, 106.0);
        Warehouse b = warehouseAt(11.0, 107.0);
        when(warehouseRepository.findByStatusOrderByNameAsc(CatalogStatus.ACTIVE)).thenReturn(List.of(a, b));

        List<WarehouseResponse> result = service.getActiveWarehouses();

        assertThat(result).hasSize(2);
        verify(warehouseRepository).findByStatusOrderByNameAsc(CatalogStatus.ACTIVE);
    }

    @Test
    void getActiveWarehouses_returnsEmpty_whenNone() {
        when(warehouseRepository.findByStatusOrderByNameAsc(CatalogStatus.ACTIVE)).thenReturn(List.of());

        assertThat(service.getActiveWarehouses()).isEmpty();
    }

    // ================= getManagedWarehouses =================

    @Test
    void getManagedWarehouses_whenProfileNotFound_throwsModeratorNotFound() {
        UUID accountId = UUID.randomUUID();
        when(accountRepository.findWithWarehouseById(accountId)).thenReturn(Optional.empty());

        assertAppException(() -> service.getManagedWarehouses(accountId), ErrorCode.MODERATOR_NOT_FOUND);
    }

    @Test
    void getManagedWarehouses_whenAccountHasNoWarehouse_throwsModeratorNotFound() {
        UUID accountId = UUID.randomUUID();
        Account account = mock(Account.class);
        when(account.getWarehouse()).thenReturn(null);
        when(accountRepository.findWithWarehouseById(accountId)).thenReturn(Optional.of(account));

        assertAppException(() -> service.getManagedWarehouses(accountId), ErrorCode.MODERATOR_NOT_FOUND);
    }

    @Test
    void getManagedWarehouses_whenProfileExists_returnsListWithItsWarehouse() {
        UUID accountId = UUID.randomUUID();
        Warehouse warehouse = warehouseAt(10.0, 106.0);
        Account account = mock(Account.class);
        when(account.getWarehouse()).thenReturn(warehouse);
        when(accountRepository.findWithWarehouseById(accountId)).thenReturn(Optional.of(account));

        List<MyWarehouseResponse> result = service.getManagedWarehouses(accountId);

        assertThat(result).hasSize(1);
        verify(accountRepository).findWithWarehouseById(accountId);
    }
}