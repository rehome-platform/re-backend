package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.*;
import com.rehome.rehome.entity.marketplace.*;
import com.rehome.rehome.repository.*;
import com.rehome.rehome.service.MlOutfitSuggestionService;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class MlOutfitSuggestionServiceTest {
    @Mock ProductRepository productRepository;
    @Mock OrderItemRepository orderItemRepository;
    @Mock ProductPhotoRepository productPhotoRepository;
    @InjectMocks MlOutfitSuggestionService service;
    Account member = Account.builder().id(UUID.randomUUID()).build();
    Warehouse warehouse = Warehouse.builder().id(UUID.randomUUID()).build();

    private Product product(String id, String name, String category, TryOnSlot slot) {
        return Product.builder().id(id).name(name).category(Category.builder().name(category).tryonSlot(slot).build())
                .brand(Brand.builder().name("Brand").build()).warehouse(warehouse).status(ProductStatus.ON_SALE)
                .size("M").currentPrice(new BigDecimal("240000")).createdAt(Instant.parse("2026-11-01T00:00:00Z"))
                .consignmentReceipt(ConsignmentReceipt.builder().agreedPrice(new BigDecimal("300000")).build()).build();
    }

    private void candidates(Product formal, Product sport) {
        when(productRepository.findWithCategoryByIdIn(List.of("top")))
                .thenReturn(List.of(product("top", "Ao trang", "Ao", TryOnSlot.TOP)));
        when(productRepository.findMlOutfitCandidates(eq(TryOnSlot.BOTTOM), eq("top"), any(), any()))
                .thenReturn(List.of(formal, sport));
        when(productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(anyCollection())).thenReturn(List.of());
    }

    @Test void sameSelectedTopHasDifferentResultsForDifferentBuyers() {
        Product formal = product("formal", "Quan tay den", "Quan tay", TryOnSlot.BOTTOM);
        Product sport = product("sport", "Quan jogger den", "Quan the thao", TryOnSlot.BOTTOM);
        candidates(formal, sport);
        Account second = Account.builder().id(UUID.randomUUID()).build();
        when(orderItemRepository.findOutfitPreferenceHistory(eq(member.getId()), any()))
                .thenReturn(Collections.nCopies(10, OrderItem.builder().product(formal).priceAtOrder(formal.getCurrentPrice()).build()));
        when(orderItemRepository.findOutfitPreferenceHistory(eq(second.getId()), any()))
                .thenReturn(Collections.nCopies(10, OrderItem.builder().product(sport).priceAtOrder(sport.getCurrentPrice()).build()));
        var first = service.suggest(member, "top", null, null);
        var other = service.suggest(second, "top", null, null);
        assertThat(first.isPersonalized()).isTrue();
        assertThat(first.getHistoryItemsUsed()).isEqualTo(10);
        assertThat(first.getItems().get(0).getId()).isEqualTo("formal");
        assertThat(other.getItems().get(0).getId()).isEqualTo("sport");
    }

    @Test void coldStartIsExplicitAndDoesNotPretendToLearnPreferences() {
        candidates(product("formal", "Quan tay den", "Quan tay", TryOnSlot.BOTTOM),
                product("sport", "Quan jogger den", "Quan the thao", TryOnSlot.BOTTOM));
        when(orderItemRepository.findOutfitPreferenceHistory(eq(member.getId()), any())).thenReturn(List.of());
        var response = service.suggest(member, "top", null, null);
        assertThat(response.isPersonalized()).isFalse();
        assertThat(response.getHistoryItemsUsed()).isZero();
        assertThat(response.getItems()).hasSize(2);
    }

    @Test void invalidCoordinatesAndMissingIdentityAreRejectedBeforeDatabaseAccess() {
        assertThatThrownBy(() -> service.suggest(null, "top", null, null))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
        assertThatThrownBy(() -> service.suggest(member, "top", Double.NaN, 100.0))
                .isInstanceOfSatisfying(AppException.class, ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_COORDINATES));
        verifyNoInteractions(productRepository, orderItemRepository, productPhotoRepository);
    }
}
