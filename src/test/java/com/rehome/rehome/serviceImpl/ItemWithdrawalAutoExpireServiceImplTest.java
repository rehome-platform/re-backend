package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.repository.ItemWithdrawalRequestRepository;
import com.rehome.rehome.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ItemWithdrawalAutoExpireServiceImplTest {

    @Mock ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    @Mock ProductRepository productRepository;

    @InjectMocks
    ItemWithdrawalAutoExpireServiceImpl service;

    static final String PRODUCT_ID = "Q7-AK-000845";
    static final String PRODUCT_ID_2 = "Q7-AK-000912";

    Product product;
    Product product2;
    ItemWithdrawalRequest expired1;
    ItemWithdrawalRequest expired2;

    @BeforeEach
    void setup() {
        product = Product.builder().id(PRODUCT_ID).status(ProductStatus.WITHDRAWN).build();
        product2 = Product.builder().id(PRODUCT_ID_2).status(ProductStatus.WITHDRAWN).build();

        expired1 = ItemWithdrawalRequest.builder().id(UUID.randomUUID())
                .product(product)
                .status(ItemWithdrawalStatus.PENDING_PICKUP)
                .pickupDeadline(Instant.now().minus(1, ChronoUnit.HOURS))
                .build();
        expired2 = ItemWithdrawalRequest.builder().id(UUID.randomUUID())
                .product(product2)
                .status(ItemWithdrawalStatus.PENDING_PICKUP)
                .pickupDeadline(Instant.now().minus(3, ChronoUnit.DAYS))
                .build();

        when(productRepository.findByIdForUpdate(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productRepository.findByIdForUpdate(PRODUCT_ID_2)).thenReturn(Optional.of(product2));
    }

    @Test
    @DisplayName("không có yêu cầu quá hạn -> không lưu gì")
    void noneExpired_doesNothing() {
        when(itemWithdrawalRequestRepository.findAllByStatusAndPickupDeadlineBefore(
                eq(ItemWithdrawalStatus.PENDING_PICKUP), any(Instant.class))).thenReturn(List.of());

        service.expireUncollectedWithdrawals();

        verify(itemWithdrawalRequestRepository, never()).save(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("quá hạn -> EXPIRED, sản phẩm WITHDRAWN -> ON_SALE")
    void expired_areClosedAndProductRelisted() {
        when(itemWithdrawalRequestRepository.findAllByStatusAndPickupDeadlineBefore(
                eq(ItemWithdrawalStatus.PENDING_PICKUP), any(Instant.class)))
                .thenReturn(List.of(expired1, expired2));

        service.expireUncollectedWithdrawals();

        ArgumentCaptor<ItemWithdrawalRequest> captor = ArgumentCaptor.forClass(ItemWithdrawalRequest.class);
        verify(itemWithdrawalRequestRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).containsExactlyInAnyOrder(expired1, expired2);

        for (ItemWithdrawalRequest r : List.of(expired1, expired2)) {
            assertThat(r.getStatus()).isEqualTo(ItemWithdrawalStatus.EXPIRED);
        }

        assertThat(product.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        assertThat(product2.getStatus()).isEqualTo(ProductStatus.ON_SALE);
        verify(productRepository).save(product);
        verify(productRepository).save(product2);
    }

    @Test
    @DisplayName("sản phẩm không còn WITHDRAWN (vd đã bị xử lý khác) -> chỉ đóng yêu cầu, không đổi sản phẩm")
    void productNotWithdrawn_skipsProductUpdate() {
        product.setStatus(ProductStatus.DESTROYED);
        when(itemWithdrawalRequestRepository.findAllByStatusAndPickupDeadlineBefore(
                eq(ItemWithdrawalStatus.PENDING_PICKUP), any(Instant.class)))
                .thenReturn(List.of(expired1));

        service.expireUncollectedWithdrawals();

        assertThat(expired1.getStatus()).isEqualTo(ItemWithdrawalStatus.EXPIRED);
        verify(itemWithdrawalRequestRepository).save(expired1);
        verify(productRepository, never()).save(any());
    }

    @Test
    @DisplayName("chỉ truy vấn trạng thái PENDING_PICKUP với mốc là thời điểm hiện tại")
    void queriesPendingPickupBeforeNow() {
        when(itemWithdrawalRequestRepository.findAllByStatusAndPickupDeadlineBefore(any(), any()))
                .thenReturn(List.of());

        Instant before = Instant.now();
        service.expireUncollectedWithdrawals();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(itemWithdrawalRequestRepository).findAllByStatusAndPickupDeadlineBefore(
                eq(ItemWithdrawalStatus.PENDING_PICKUP), instantCaptor.capture());
        assertThat(instantCaptor.getValue()).isBetween(before, after);
    }
}
