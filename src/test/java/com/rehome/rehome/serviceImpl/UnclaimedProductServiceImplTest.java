package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.dto.request.ConfirmTransferRequest;
import com.rehome.rehome.dto.response.ProductStatusResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ProductRepository;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UnclaimedProductServiceImplTest {

    private static final String SKU = "Q7-AK-000845";

    @Mock private ProductRepository productRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

    @Mock private com.rehome.rehome.component.AuditLogger auditLogger;
    @InjectMocks private UnclaimedProductServiceImpl service;

    private Warehouse warehouse;
    private Account moderator;
    private Product product;

    @BeforeEach
    void setUp() {
        warehouse = warehouse(1);
        moderator = Account.builder().id(UUID.randomUUID()).warehouse(warehouse).build();
        product = Product.builder()
                .id(SKU)
                .name("Áo khoác denim")
                .warehouse(warehouse)
                .status(ProductStatus.UNCLAIMED)
                .build();
    }

    // ---------- helpers ----------

    private static void assertAppError(ThrowingCallable callable, ErrorCode expected) {
        assertThatThrownBy(callable)
                .isInstanceOfSatisfying(
                        AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
    }

    // Tạo Warehouse với id xác định theo key; tự nhận biết kiểu id (UUID/Long/Integer/String)
    private static Warehouse warehouse(int key) {
        Warehouse w = new Warehouse();
        Class<?> idType;
        try {
            idType = Warehouse.class.getDeclaredField("id").getType();
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Warehouse không có field id", e);
        }
        Object id;
        if (idType == UUID.class) id = new UUID(0L, key);
        else if (idType == Long.class || idType == long.class) id = (long) key;
        else if (idType == Integer.class || idType == int.class) id = key;
        else id = "wh-" + key;
        ReflectionTestUtils.setField(w, "id", id);
        return w;
    }

    private static ConfirmTransferRequest request(String partnerName) {
        ConfirmTransferRequest r = new ConfirmTransferRequest();
        r.setPartnerOrganizationName(partnerName);
        return r;
    }

    private void stubProductFound() {
        when(productRepository.findByIdForUpdate(SKU)).thenReturn(Optional.of(product));
    }

    // ---------- tests ----------

    @Test
    @DisplayName("Thành công: sản phẩm chuyển DONATED và ghi audit log")
    void success_marksDonatedAndWritesAudit() {
        stubProductFound();

        ProductStatusResponse response = service.confirmTransfer(SKU, request("  Quỹ Từ Thiện Ánh Dương  "), moderator);

        assertThat(response.getId()).isEqualTo(SKU);
        assertThat(response.getStatus()).isEqualTo(ProductStatus.DONATED);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DONATED);
        verify(productRepository).save(product);

        ArgumentCaptor<com.fasterxml.jackson.databind.JsonNode> captor =
                ArgumentCaptor.forClass(com.fasterxml.jackson.databind.JsonNode.class);
        verify(auditLogger).logWarehouse(org.mockito.ArgumentMatchers.eq(moderator),
                org.mockito.ArgumentMatchers.eq(product.getWarehouse()),
                org.mockito.ArgumentMatchers.eq(com.rehome.rehome.entity.enums.WarehouseAuditGroup.INVENTORY),
                org.mockito.ArgumentMatchers.eq("CONFIRM_UNCLAIMED_TRANSFER"),
                org.mockito.ArgumentMatchers.eq("PRODUCT"), org.mockito.ArgumentMatchers.eq(SKU),
                org.mockito.ArgumentMatchers.eq("TRANSFERRED"), captor.capture());
        com.fasterxml.jackson.databind.JsonNode detail = captor.getValue();
        assertThat(detail.get("partnerOrganizationName").asText()).isEqualTo("Quỹ Từ Thiện Ánh Dương");
        assertThat(detail.get("previousStatus").asText()).isEqualTo("UNCLAIMED");
        assertThat(detail.get("newStatus").asText()).isEqualTo("DONATED");
        assertThat(detail.get("label").asText()).isEqualTo(SKU);
    }

    @ParameterizedTest(name = "partnerOrganizationName = \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("Thiếu hoặc rỗng partnerOrganizationName: 400, chưa chạm DB")
    void partnerNameMissing_throws(String partnerName) {
        assertAppError(
                () -> service.confirmTransfer(SKU, request(partnerName), moderator),
                ErrorCode.PARTNER_ORGANIZATION_NAME_REQUIRED);
        verifyNoInteractions(productRepository, auditLogger);
    }

    @Test
    @DisplayName("Kiểm tra partnerOrganizationName trước kiểm tra kho Moderator")
    void partnerNameValidatedBeforeWarehouse() {
        Account noWh = Account.builder().id(UUID.randomUUID()).build();

        assertAppError(
                () -> service.confirmTransfer(SKU, request(""), noWh),
                ErrorCode.PARTNER_ORGANIZATION_NAME_REQUIRED);
    }

    @Test
    @DisplayName("Moderator chưa gán kho: 403 MODERATOR_NO_WAREHOUSE")
    void noWarehouse_throws() {
        Account noWh = Account.builder().id(UUID.randomUUID()).build();

        assertAppError(
                () -> service.confirmTransfer(SKU, request("Quỹ A"), noWh), ErrorCode.MODERATOR_NO_WAREHOUSE);
        verifyNoInteractions(productRepository, auditLogger);
    }

    @Test
    @DisplayName("Không có sản phẩm: 404 PRODUCT_NOT_FOUND")
    void productNotFound_throws() {
        when(productRepository.findByIdForUpdate(SKU)).thenReturn(Optional.empty());

        assertAppError(
                () -> service.confirmTransfer(SKU, request("Quỹ A"), moderator), ErrorCode.PRODUCT_NOT_FOUND);
        verify(productRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    @Test
    @DisplayName("Sản phẩm thuộc kho khác: 403 PRODUCT_NOT_IN_YOUR_WAREHOUSE, không đổi trạng thái")
    void productOfOtherWarehouse_throws() {
        product.setWarehouse(warehouse(2));
        stubProductFound();

        assertAppError(
                () -> service.confirmTransfer(SKU, request("Quỹ A"), moderator),
                ErrorCode.PRODUCT_NOT_IN_YOUR_WAREHOUSE);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.UNCLAIMED);
        verify(productRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    @ParameterizedTest(name = "status = {0}")
    @EnumSource(value = ProductStatus.class, names = "UNCLAIMED", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Sản phẩm không ở UNCLAIMED: 409 PRODUCT_NOT_UNCLAIMED, không đổi gì")
    void productNotUnclaimed_throws(ProductStatus status) {
        product.setStatus(status);
        stubProductFound();

        assertAppError(
                () -> service.confirmTransfer(SKU, request("Quỹ A"), moderator), ErrorCode.PRODUCT_NOT_UNCLAIMED);
        assertThat(product.getStatus()).isEqualTo(status);
        verify(productRepository, never()).save(any());
        verifyNoInteractions(auditLogger);
    }

    @Test
    @DisplayName("Xác nhận lần 2 trên sản phẩm đã DONATED: 409, không ghi audit lần nữa")
    void confirmTwice_secondCallRejected() {
        stubProductFound();

        service.confirmTransfer(SKU, request("Quỹ A"), moderator);

        assertAppError(
                () -> service.confirmTransfer(SKU, request("Quỹ A"), moderator), ErrorCode.PRODUCT_NOT_UNCLAIMED);
        verify(auditLogger, org.mockito.Mockito.times(1)).logWarehouse(any(), any(com.rehome.rehome.entity.marketplace.Warehouse.class),
                any(), any(), any(), any(), any(), any()); // đúng 1 lần, của lần gọi đầu
    }
}