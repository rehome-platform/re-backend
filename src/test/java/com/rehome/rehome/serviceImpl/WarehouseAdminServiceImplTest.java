package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.dto.request.CreateWarehouseRequest;
import com.rehome.rehome.dto.request.UpdateWarehouseRequest;
import com.rehome.rehome.dto.response.WarehouseAdminResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseAdminServiceImplTest {

    @Mock WarehouseRepository warehouseRepository;
    @Mock ProductRepository productRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock ConsignmentFormRepository consignmentFormRepository;
    @Mock AuditLogger auditLogger;
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks WarehouseAdminServiceImpl service;

    Account admin;
    UUID warehouseId;
    Warehouse warehouse;

    @BeforeEach
    void setup() {
        admin = Account.builder().id(UUID.randomUUID()).email("admin@rehome.com")
                .role(Role.ADMIN).status(AccountStatus.ACTIVE).build();
        warehouseId = UUID.randomUUID();
        warehouse = Warehouse.builder().id(warehouseId).name("Kho Quận 7").code("Q7")
                .address("105 Nguyễn Thị Thập").latitude(new BigDecimal("10.7340000"))
                .longitude(new BigDecimal("106.7220000")).openingHours("08:00 - 21:00")
                .status(CatalogStatus.ACTIVE).build();
    }

    private void assertAppError(ThrowingCallable action, ErrorCode expected) {
        assertThatThrownBy(action)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    private CreateWarehouseRequest createRequest(String name) {
        CreateWarehouseRequest r = new CreateWarehouseRequest();
        r.setName(name);
        r.setAddress(" 12 Lê Lợi, Q.1 ");
        r.setLatitude(new BigDecimal("10.7710000"));
        r.setLongitude(new BigDecimal("106.6900000"));
        r.setOpeningHours("08:00 - 20:00");
        return r;
    }

    private UpdateWarehouseRequest updateRequest(String name) {
        UpdateWarehouseRequest r = new UpdateWarehouseRequest();
        r.setName(name);
        r.setAddress("200 Điện Biên Phủ");
        r.setLatitude(new BigDecimal("10.7900000"));
        r.setLongitude(new BigDecimal("106.6900000"));
        r.setOpeningHours("09:00 - 18:00");
        return r;
    }

    // ==================== list (API 141) ====================

    @Test
    @DisplayName("list: không lọc -> trả cả kho ACTIVE lẫn HIDDEN")
    void list_noFilter() {
        Warehouse hidden = Warehouse.builder().id(UUID.randomUUID()).name("Kho cũ")
                .status(CatalogStatus.HIDDEN).build();
        when(warehouseRepository.findAllByOrderByNameAsc()).thenReturn(List.of(warehouse, hidden));

        List<WarehouseAdminResponse> result = service.list(null);

        assertThat(result).extracting(WarehouseAdminResponse::getStatus)
                .containsExactly(CatalogStatus.ACTIVE, CatalogStatus.HIDDEN);
    }

    @Test
    @DisplayName("list: lọc theo status")
    void list_withStatus() {
        when(warehouseRepository.findByStatusOrderByNameAsc(CatalogStatus.ACTIVE)).thenReturn(List.of(warehouse));

        assertThat(service.list(CatalogStatus.ACTIVE)).hasSize(1);
        verify(warehouseRepository, never()).findAllByOrderByNameAsc();
    }

    // ==================== create (API 142) ====================

    @Test
    @DisplayName("create: thành công -> ACTIVE, mã kho tự sinh từ tên (bỏ tiền tố Kho), trim dữ liệu, ghi audit")
    void create_success() {
        when(warehouseRepository.existsByNameIgnoreCase("Kho Quận 10")).thenReturn(false);
        when(warehouseRepository.existsByCode(anyString())).thenReturn(false);
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> {
            Warehouse w = inv.getArgument(0);
            w.setId(UUID.randomUUID());
            return w;
        });

        WarehouseAdminResponse response = service.create(admin, createRequest("  Kho Quận 10 "));

        ArgumentCaptor<Warehouse> captor = ArgumentCaptor.forClass(Warehouse.class);
        verify(warehouseRepository).save(captor.capture());
        Warehouse saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Kho Quận 10");
        assertThat(saved.getCode()).isEqualTo("Q10");
        assertThat(saved.getAddress()).isEqualTo("12 Lê Lợi, Q.1");
        assertThat(saved.getStatus()).isEqualTo(CatalogStatus.ACTIVE);

        verify(auditLogger).log(eq(admin), eq("CREATE_WAREHOUSE"), eq("WAREHOUSE"),
                eq(saved.getId().toString()), any(JsonNode.class));
        assertThat(response.getCode()).isEqualTo("Q10");
    }

    @Test
    @DisplayName("create: mã kho trùng -> thêm hậu tố số")
    void create_codeCollision_addsSuffix() {
        when(warehouseRepository.existsByNameIgnoreCase("Kho Quận 10")).thenReturn(false);
        when(warehouseRepository.existsByCode("Q10")).thenReturn(true);
        when(warehouseRepository.existsByCode("Q102")).thenReturn(false);
        when(warehouseRepository.save(any(Warehouse.class))).thenAnswer(inv -> {
            Warehouse w = inv.getArgument(0);
            if (w.getId() == null) {
                w.setId(UUID.randomUUID());
            }
            return w;
        });

        WarehouseAdminResponse response = service.create(admin, createRequest("Kho Quận 10"));

        assertThat(response.getCode()).isEqualTo("Q102");
    }

    @Test
    @DisplayName("create: tên kho đã tồn tại -> 409 WAREHOUSE_NAME_ALREADY_EXISTS")
    void create_duplicateName() {
        when(warehouseRepository.existsByNameIgnoreCase("Kho Quận 7")).thenReturn(true);

        assertAppError(() -> service.create(admin, createRequest("Kho Quận 7")),
                ErrorCode.WAREHOUSE_NAME_ALREADY_EXISTS);
        verify(warehouseRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    // ==================== update (API 143) ====================

    @Test
    @DisplayName("update: thành công -> đổi 5 field, giữ nguyên code và status, audit có before/after")
    void update_success() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.existsByNameIgnoreCaseAndIdNot("Kho Điện Biên Phủ", warehouseId)).thenReturn(false);
        when(warehouseRepository.save(warehouse)).thenReturn(warehouse);

        WarehouseAdminResponse response = service.update(admin, warehouseId, updateRequest("Kho Điện Biên Phủ"));

        assertThat(warehouse.getName()).isEqualTo("Kho Điện Biên Phủ");
        assertThat(warehouse.getAddress()).isEqualTo("200 Điện Biên Phủ");
        assertThat(warehouse.getOpeningHours()).isEqualTo("09:00 - 18:00");
        assertThat(warehouse.getCode()).isEqualTo("Q7");
        assertThat(warehouse.getStatus()).isEqualTo(CatalogStatus.ACTIVE);

        ArgumentCaptor<JsonNode> detail = ArgumentCaptor.forClass(JsonNode.class);
        verify(auditLogger).log(eq(admin), eq("UPDATE_WAREHOUSE"), eq("WAREHOUSE"),
                eq(warehouseId.toString()), detail.capture());
        assertThat(detail.getValue().get("before").get("name").asText()).isEqualTo("Kho Quận 7");
        assertThat(detail.getValue().get("after").get("name").asText()).isEqualTo("Kho Điện Biên Phủ");
        assertThat(response.getName()).isEqualTo("Kho Điện Biên Phủ");
    }

    @Test
    @DisplayName("update: không tìm thấy kho -> WAREHOUSE_NOT_FOUND")
    void update_notFound() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        assertAppError(() -> service.update(admin, warehouseId, updateRequest("X")),
                ErrorCode.WAREHOUSE_NOT_FOUND);
    }

    @Test
    @DisplayName("update: trùng tên kho khác -> 409 WAREHOUSE_NAME_ALREADY_EXISTS, không lưu")
    void update_duplicateName() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(warehouseRepository.existsByNameIgnoreCaseAndIdNot("Kho Quận 1", warehouseId)).thenReturn(true);

        assertAppError(() -> service.update(admin, warehouseId, updateRequest("Kho Quận 1")),
                ErrorCode.WAREHOUSE_NAME_ALREADY_EXISTS);
        verify(warehouseRepository, never()).save(any());
        assertThat(warehouse.getName()).isEqualTo("Kho Quận 7");
    }

    // ==================== hide (API 144) ====================

    @Test
    @DisplayName("hide: kho trống -> HIDDEN, ghi audit")
    void hide_success() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.countByWarehouseAndStatuses(eq(warehouseId), anyCollection())).thenReturn(0L);
        when(orderItemRepository.countSoldItemsInOpenOrders(eq(warehouseId), anyCollection())).thenReturn(0L);
        when(consignmentFormRepository.existsByWarehouse_IdAndStatusIn(eq(warehouseId), anyCollection()))
                .thenReturn(false);
        when(warehouseRepository.save(warehouse)).thenReturn(warehouse);

        WarehouseAdminResponse response = service.hide(admin, warehouseId);

        assertThat(warehouse.getStatus()).isEqualTo(CatalogStatus.HIDDEN);
        assertThat(response.getStatus()).isEqualTo(CatalogStatus.HIDDEN);
        verify(auditLogger).log(eq(admin), eq("HIDE_WAREHOUSE"), eq("WAREHOUSE"),
                eq(warehouseId.toString()), any(JsonNode.class));
    }

    @Test
    @DisplayName("hide: còn sản phẩm ON_SALE/RESERVED/UNCLAIMED/PLATFORM_OWNED/RETURNED -> 409 WAREHOUSE_HAS_STOCK")
    void hide_hasProducts() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.countByWarehouseAndStatuses(eq(warehouseId), anyCollection())).thenReturn(3L);

        assertAppError(() -> service.hide(admin, warehouseId), ErrorCode.WAREHOUSE_HAS_STOCK);
        assertThat(warehouse.getStatus()).isEqualTo(CatalogStatus.ACTIVE);
        verify(warehouseRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    @Test
    @DisplayName("hide: hàng SOLD nhưng đơn còn PAID/PACKING -> 409 WAREHOUSE_HAS_STOCK")
    void hide_soldItemInOpenOrder() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.countByWarehouseAndStatuses(eq(warehouseId), anyCollection())).thenReturn(0L);
        when(orderItemRepository.countSoldItemsInOpenOrders(eq(warehouseId), anyCollection())).thenReturn(1L);

        assertAppError(() -> service.hide(admin, warehouseId), ErrorCode.WAREHOUSE_HAS_STOCK);
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    @DisplayName("hide: còn phiếu ký gửi PENDING/APPROVED/INSPECTING -> 409 WAREHOUSE_HAS_STOCK")
    void hide_openConsignmentForm() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(productRepository.countByWarehouseAndStatuses(eq(warehouseId), anyCollection())).thenReturn(0L);
        when(orderItemRepository.countSoldItemsInOpenOrders(eq(warehouseId), anyCollection())).thenReturn(0L);
        when(consignmentFormRepository.existsByWarehouse_IdAndStatusIn(eq(warehouseId), anyCollection()))
                .thenReturn(true);

        assertAppError(() -> service.hide(admin, warehouseId), ErrorCode.WAREHOUSE_HAS_STOCK);
        verify(warehouseRepository, never()).save(any());
    }

    @Test
    @DisplayName("hide: kho đã ẩn -> 409 WAREHOUSE_ALREADY_HIDDEN, không kiểm tra tồn kho")
    void hide_alreadyHidden() {
        warehouse.setStatus(CatalogStatus.HIDDEN);
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));

        assertAppError(() -> service.hide(admin, warehouseId), ErrorCode.WAREHOUSE_ALREADY_HIDDEN);
        verifyNoInteractions(productRepository, orderItemRepository, consignmentFormRepository);
    }

    @Test
    @DisplayName("hide: không tìm thấy kho -> WAREHOUSE_NOT_FOUND")
    void hide_notFound() {
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        assertAppError(() -> service.hide(admin, warehouseId), ErrorCode.WAREHOUSE_NOT_FOUND);
    }
}
