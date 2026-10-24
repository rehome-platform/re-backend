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
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AuditLogRepository;
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
    @Mock private AuditLogRepository auditLogRepository;
    @Spy private ObjectMapper objectMapper = new ObjectMapper();

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

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog audit = captor.getValue();
        assertThat(audit.getActorAccount()).isSameAs(moderator);
        assertThat(audit.getActionType()).isEqualTo("CONFIRM_UNCLAIMED_TRANSFER");
        assertThat(audit.getTargetType()).isEqualTo("PRODUCT");
        assertThat(audit.getTargetId()).isEqualTo(SKU);
        assertThat(audit.getDetail().get("partnerOrganizationName").asText()).isEqualTo("Quỹ Từ Thiện Ánh Dương");
        assertThat(audit.getDetail().get("previousStatus").asText()).isEqualTo("UNCLAIMED");
        assertThat(audit.getDetail().get("newStatus").asText()).isEqualTo("DONATED");
    }

    @ParameterizedTest(name = "partnerOrganizationName = \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("Thiếu hoặc rỗng partnerOrganizationName: 400, chưa chạm DB")
    void partnerNameMissing_throws(String partnerName) {
        assertAppError(
                () -> service.confirmTransfer(SKU, request(partnerName), moderator),
                ErrorCode.PARTNER_ORGANIZATION_NAME_REQUIRED);
        verifyNoInteractions(productRepository, auditLogRepository);
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
        verifyNoInteractions(productRepository, auditLogRepository);
    }

    @Test
    @DisplayName("Không có sản phẩm: 404 PRODUCT_NOT_FOUND")
    void productNotFound_throws() {
        when(productRepository.findByIdForUpdate(SKU)).thenReturn(Optional.empty());

        assertAppError(
                () -> service.confirmTransfer(SKU, request("Quỹ A"), moderator), ErrorCode.PRODUCT_NOT_FOUND);
        verify(productRepository, never()).save(any());
        verifyNoInteractions(auditLogRepository);
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
        verifyNoInteractions(auditLogRepository);
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
        verifyNoInteractions(auditLogRepository);
    }

    @Test
    @DisplayName("Xác nhận lần 2 trên sản phẩm đã DONATED: 409, không ghi audit lần nữa")
    void confirmTwice_secondCallRejected() {
        stubProductFound();

        service.confirmTransfer(SKU, request("Quỹ A"), moderator);

        assertAppError(
                () -> service.confirmTransfer(SKU, request("Quỹ A"), moderator), ErrorCode.PRODUCT_NOT_UNCLAIMED);
        verify(auditLogRepository).save(any(AuditLog.class)); // đúng 1 lần, của lần gọi đầu
    }
}