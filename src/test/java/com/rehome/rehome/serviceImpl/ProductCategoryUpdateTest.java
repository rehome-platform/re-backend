package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.component.ProductListCache;
import com.rehome.rehome.dto.request.UpdateProductRequest;
import com.rehome.rehome.dto.response.ProductSummaryResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.TryOnSlot;
import com.rehome.rehome.entity.marketplace.*;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.*;
import com.rehome.rehome.service.ProductCatalogReader;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductCategoryUpdateTest {
    @Mock ProductRepository products;
    @Mock ProductPhotoRepository photos;
    @Mock CategoryRepository categories;
    @Mock OrderItemRepository orderItems;
    @Mock AuditLogger audit;
    @Mock ItemWithdrawalRequestRepository withdrawals;
    @InjectMocks ProductServiceImpl service;

    Product product;
    Account moderator;
    Category original;

    @BeforeEach void setup() {
        Warehouse warehouse = Warehouse.builder().id(UUID.randomUUID()).build();
        moderator = Account.builder().id(UUID.randomUUID()).role(Role.MODERATOR).warehouse(warehouse).build();
        original = Category.builder().id(UUID.randomUUID()).name("Bottom").tryonSlot(TryOnSlot.BOTTOM).build();
        product = Product.builder().id("Q7-BOTTOM-1").name("Old name").warehouse(warehouse).category(original)
                .brand(Brand.builder().name("Brand").build())
                .member(Account.builder().id(UUID.randomUUID()).build())
                .consignmentReceipt(ConsignmentReceipt.builder().agreedPrice(BigDecimal.TEN).build())
                .currentPrice(BigDecimal.TEN).status(ProductStatus.ON_SALE).build();
    }

    private void allowUpdate() {
        when(products.findByIdForUpdate(product.getId())).thenReturn(Optional.of(product));
    }

    private void allowDetailResponse() {
        when(products.findById(product.getId())).thenReturn(Optional.of(product));
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"ON_SALE", "WAREHOUSE"})
    void categoryChangeUpdatesSlotAndRetainsSkuAndReordersExistingPhotos(ProductStatus status) {
        product.setStatus(status);
        allowUpdate();
        allowDetailResponse();
        Category top = Category.builder().id(UUID.randomUUID()).name("Top").tryonSlot(TryOnSlot.TOP).build();
        when(categories.findById(top.getId())).thenReturn(Optional.of(top));
        ProductPhoto first = ProductPhoto.builder().id(UUID.randomUUID()).product(product)
                .imageUrl("first.jpg").displayOrder(0).isBackgroundRemoved(true).build();
        ProductPhoto second = ProductPhoto.builder().id(UUID.randomUUID()).product(product)
                .imageUrl("second.jpg").displayOrder(1).build();
        when(photos.findByProduct_IdOrderByDisplayOrderAscIdAsc(product.getId()))
                .thenReturn(List.of(first, second), List.of(second, first));

        var result = service.updateProduct(moderator, product.getId(),
                new UpdateProductRequest(" New name ", List.of("second.jpg", "first.jpg"), top.getId()));

        assertThat(product.getCategory()).isSameAs(top);
        assertThat(result.getCategoryId()).isEqualTo(top.getId());
        assertThat(result.getTryonSlot()).isEqualTo(TryOnSlot.TOP);
        assertThat(result.getId()).isEqualTo("Q7-BOTTOM-1");
        assertThat(result.getName()).isEqualTo("New name");
        assertThat(result.getPhotos()).containsExactly("second.jpg", "first.jpg");
        assertThat(second.getDisplayOrder()).isZero();
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        assertThat(first.getIsBackgroundRemoved()).isTrue();
        verify(products).save(product);
        verify(photos).saveAll(List.of(second, first));
        verify(audit).logOperation(moderator, "UPDATE_PRODUCT", "PRODUCT", product.getId(), "SUCCESS");
    }

    @Test void unchangedCategoryDoesNotQueryCategoryRepository() {
        allowUpdate();
        allowDetailResponse();
        service.updateProduct(moderator, product.getId(),
                new UpdateProductRequest("Name", List.of(), original.getId()));
        assertThat(product.getCategory()).isSameAs(original);
        verifyNoInteractions(categories);
    }

    @Test void legacyConstructorKeepsCategoryAndDoesNotQueryCategoryRepository() {
        allowUpdate();
        allowDetailResponse();
        var request = new UpdateProductRequest("Name", List.of());
        assertThat(request.categoryId()).isNull();
        var response = service.updateProduct(moderator, product.getId(), request);
        assertThat(response.getCategoryId()).isEqualTo(original.getId());
        assertThat(response.getTryonSlot()).isEqualTo(TryOnSlot.BOTTOM);
        verifyNoInteractions(categories);
    }

    @Test void invalidCategoryRejectsBeforeAnyMutationOrWrite() {
        allowUpdate();
        UUID missing = UUID.randomUUID();
        when(categories.findById(missing)).thenReturn(Optional.empty());

        assertError(ErrorCode.CATEGORY_NOT_FOUND,
                new UpdateProductRequest("Changed", List.of(), missing));

        assertThat(product.getName()).isEqualTo("Old name");
        assertThat(product.getCategory()).isSameAs(original);
        verifyNoInteractions(photos, audit);
    }

    @ParameterizedTest
    @EnumSource(value = ProductStatus.class, names = {"ON_SALE", "WAREHOUSE"}, mode = EnumSource.Mode.EXCLUDE)
    void forbiddenStatusStillBlocksCategoryChange(ProductStatus status) {
        product.setStatus(status);
        allowUpdate();
        assertError(ErrorCode.PRODUCT_UPDATE_CONFLICT,
                new UpdateProductRequest("Name", List.of(), UUID.randomUUID()));
        verifyNoInteractions(categories, photos, audit);
    }

    @Test void activeOrderStillBlocksCategoryChange() {
        allowUpdate();
        when(orderItems.findActiveByProductIds(List.of(product.getId())))
                .thenReturn(List.of(OrderItem.builder().build()));
        assertError(ErrorCode.PRODUCT_UPDATE_CONFLICT,
                new UpdateProductRequest("Name", List.of(), UUID.randomUUID()));
        verifyNoInteractions(categories, photos, audit);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "RETURNED"})
    void historicalOrderDoesNotBlockUpdatingARelistedProduct(OrderStatus status) {
        allowUpdate();
        allowDetailResponse();
        when(orderItems.findActiveByProductIds(List.of(product.getId())))
                .thenReturn(List.of(OrderItem.builder().order(Order.builder().status(status).build()).build()));

        var response = service.updateProduct(moderator, product.getId(), new UpdateProductRequest("Relisted", List.of()));

        assertThat(response.getName()).isEqualTo("Relisted");
        verify(products).save(product);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "RETURNED"}, mode = EnumSource.Mode.EXCLUDE)
    void nonReturnedAndNonCancelledOrderStillBlocksUpdatingProduct(OrderStatus status) {
        allowUpdate();
        when(orderItems.findActiveByProductIds(List.of(product.getId())))
                .thenReturn(List.of(OrderItem.builder().order(Order.builder().status(status).build()).build()));

        assertError(ErrorCode.PRODUCT_UPDATE_CONFLICT, new UpdateProductRequest("Name", List.of()));
        verifyNoInteractions(photos, audit);
    }

    @Test void oldReturnedOrderDoesNotHideANewActiveOrder() {
        allowUpdate();
        when(orderItems.findActiveByProductIds(List.of(product.getId())))
                .thenReturn(List.of(
                        OrderItem.builder().order(Order.builder().status(OrderStatus.RETURNED).build()).build(),
                        OrderItem.builder().order(Order.builder().status(OrderStatus.PAID).build()).build()));

        assertError(ErrorCode.PRODUCT_UPDATE_CONFLICT, new UpdateProductRequest("Name", List.of()));
    }

    @ParameterizedTest
    @EnumSource(value = ItemWithdrawalStatus.class, names = {"PENDING_APPROVAL", "PENDING_PICKUP"})
    void warehouseProductWithActiveWithdrawalCannotBeEdited(ItemWithdrawalStatus status) {
        product.setStatus(ProductStatus.WAREHOUSE);
        allowUpdate();
        when(withdrawals.existsByProduct_IdAndStatusIn(eq(product.getId()),
                argThat(statuses -> statuses.contains(status))))
                .thenReturn(true);

        assertError(ErrorCode.PRODUCT_UPDATE_CONFLICT, new UpdateProductRequest("Changed", List.of()));

        assertThat(product.getName()).isEqualTo("Old name");
        verifyNoInteractions(categories, orderItems, photos, audit);
    }

    @Test void differentWarehouseStillBlocksCategoryChange() {
        allowUpdate();
        moderator.setWarehouse(Warehouse.builder().id(UUID.randomUUID()).build());
        assertError(ErrorCode.PRODUCT_NOT_IN_YOUR_WAREHOUSE,
                new UpdateProductRequest("Name", List.of(), UUID.randomUUID()));
        verifyNoInteractions(categories, orderItems, photos, audit);
    }

    private void assertError(ErrorCode expected, UpdateProductRequest request) {
        assertThatThrownBy(() -> service.updateProduct(moderator, product.getId(), request))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(expected));
        verify(products, never()).save(any());
    }

    @Test void requestJsonSupportsBothOldPayloadAndOptionalCategory() throws Exception {
        var mapper = new ObjectMapper();
        assertThat(mapper.readValue("{\"name\":\"Name\",\"photoUrls\":[]}", UpdateProductRequest.class).categoryId())
                .isNull();
        var request = new UpdateProductRequest("Name", List.of(), original.getId());
        assertThat(mapper.readValue(mapper.writeValueAsString(request), UpdateProductRequest.class)).isEqualTo(request);
    }

    @ParameterizedTest
    @EnumSource(value = TryOnSlot.class, names = {"TOP", "BOTTOM", "DRESS"})
    @SuppressWarnings("unchecked")
    void catalogExposesActualCategoryAndSlotAndPreservesThemInCachedJson(TryOnSlot slot) throws Exception {
        original.setTryonSlot(slot);
        when(products.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));
        var key = new ProductListCache.Key(null, null, null, null, null, null, null, 0, 20, null);

        var item = new ProductCatalogReader(products, photos).load(key).getContent().get(0);

        assertThat(item.getCategoryId()).isEqualTo(original.getId());
        assertThat(item.getCategoryName()).isEqualTo(original.getName());
        assertThat(item.getTryonSlot()).isEqualTo(slot);
        var mapper = new ObjectMapper();
        var cached = mapper.readValue(mapper.writeValueAsString(item), ProductSummaryResponse.class);
        assertThat(cached.getCategoryId()).isEqualTo(original.getId());
        assertThat(cached.getTryonSlot()).isEqualTo(slot);
    }
}
