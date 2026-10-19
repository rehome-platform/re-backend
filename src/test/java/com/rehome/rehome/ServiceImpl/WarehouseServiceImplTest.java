package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.response.WarehouseNearestResponse;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceImplTest {

    @Mock private WarehouseRepository warehouseRepository;
    @InjectMocks private WarehouseServiceImpl service;

    private Warehouse warehouse(String name, double lat, double lng) {
        Warehouse w = new Warehouse();
        w.setId(UUID.randomUUID());
        w.setName(name);
        w.setAddress("Địa chỉ " + name);
        w.setLatitude(BigDecimal.valueOf(lat));
        w.setLongitude(BigDecimal.valueOf(lng));
        return w;
    }

    // ---------- toạ độ không hợp lệ ----------

    @ParameterizedTest
    @CsvSource({
            "NaN, 10",
            "10, NaN",
            "90.0001, 10",
            "-90.0001, 10",
            "10, 180.0001",
            "10, -180.0001"
    })
    void getNearest_invalidCoordinates_throws(double lat, double lng) {
        assertThatThrownBy(() -> service.getNearest(lat, lng))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.INVALID_COORDINATES));

        verifyNoInteractions(warehouseRepository);
    }

    // ---------- biên hợp lệ ----------

    @ParameterizedTest
    @CsvSource({"90, 180", "-90, -180", "0, 0"})
    void getNearest_boundaryCoordinates_areAccepted(double lat, double lng) {
        when(warehouseRepository.findByStatus(CatalogStatus.ACTIVE)).thenReturn(List.of());

        assertThat(service.getNearest(lat, lng)).isEmpty();
    }

    // ---------- logic chính ----------

    @Test
    void getNearest_noActiveWarehouse_returnsEmpty() {
        when(warehouseRepository.findByStatus(CatalogStatus.ACTIVE)).thenReturn(List.of());

        assertThat(service.getNearest(10.77, 106.69)).isEmpty();
        verify(warehouseRepository).findByStatus(CatalogStatus.ACTIVE);
    }

    @Test
    void getNearest_sortsByDistanceAscending() {
        Warehouse far = warehouse("Xa", 12.0, 106.0);
        Warehouse near = warehouse("Gần", 10.0, 106.0);
        Warehouse mid = warehouse("Giữa", 11.0, 106.0);

        when(warehouseRepository.findByStatus(CatalogStatus.ACTIVE)).thenReturn(List.of(far, near, mid));

        List<WarehouseNearestResponse> result = service.getNearest(10.0, 106.0);

        assertThat(result).extracting(WarehouseNearestResponse::name)
                .containsExactly("Gần", "Giữa", "Xa");
        assertThat(result).extracting(WarehouseNearestResponse::distanceKm).isSorted();
    }

    @Test
    void getNearest_mapsFieldsAndComputesDistance() {
        Warehouse same = warehouse("Cùng điểm", 10.0, 106.0);
        Warehouse oneDegreeNorth = warehouse("Lệch 1 độ vĩ", 11.0, 106.0);

        when(warehouseRepository.findByStatus(CatalogStatus.ACTIVE)).thenReturn(List.of(oneDegreeNorth, same));

        List<WarehouseNearestResponse> result = service.getNearest(10.0, 106.0);

        WarehouseNearestResponse first = result.get(0);
        assertThat(first.id()).isEqualTo(same.getId());
        assertThat(first.name()).isEqualTo("Cùng điểm");
        assertThat(first.address()).isEqualTo("Địa chỉ Cùng điểm");
        assertThat(first.distanceKm()).isEqualTo(0.0, within(1e-6));

        // 1 độ vĩ ≈ 6371 * π / 180 ≈ 111.19 km
        assertThat(result.get(1).distanceKm()).isEqualTo(111.19, within(0.05));
    }

    @Test
    void getNearest_limitsResultsToTwenty_keepingTheNearest() {
        List<Warehouse> warehouses = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            warehouses.add(warehouse("W" + i, 10.0 + i * 0.01, 106.0));  // W1 gần nhất
        }
        // xáo trộn thứ tự đầu vào
        java.util.Collections.reverse(warehouses);

        when(warehouseRepository.findByStatus(CatalogStatus.ACTIVE)).thenReturn(warehouses);

        List<WarehouseNearestResponse> result = service.getNearest(10.0, 106.0);

        assertThat(result).hasSize(20);
        assertThat(result.get(0).name()).isEqualTo("W1");
        assertThat(result.get(19).name()).isEqualTo("W20");
        assertThat(result).extracting(WarehouseNearestResponse::name)
                .doesNotContain("W21", "W22", "W23", "W24", "W25");
    }
}