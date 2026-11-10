package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.repository.EndOfTermDecisionRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.EndOfTermDecisionService;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EndOfTermLifecycleServiceImplTest {

    @Mock ProductRepository productRepository;
    @Mock EndOfTermDecisionRepository endOfTermDecisionRepository;
    @Mock EndOfTermDecisionService endOfTermDecisionService;

    @InjectMocks
    EndOfTermLifecycleServiceImpl service;

    static final long TAKE_BACK_PICKUP_DEADLINE_DAYS = 30;
    static final String PRODUCT_ID = "Q7-AK-000845";

    ConsignmentReceipt receipt;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "takeBackPickupDeadlineDays", TAKE_BACK_PICKUP_DEADLINE_DAYS);

        receipt = ConsignmentReceipt.builder().termLengthDays(30).maxRenewals(2).build();

        when(productRepository.findOnSaleNotYetFinalTerm()).thenReturn(List.of());
        when(productRepository.findAllByStatusAndEndOfTermDeadlineBefore(eq(ProductStatus.ON_SALE), any()))
                .thenReturn(List.of());
        when(endOfTermDecisionRepository.findAllByDecisionAndTakenBackAtIsNullAndDecidedAtBefore(
                eq(EndOfTermChoice.TAKE_BACK), any())).thenReturn(List.of());
    }

    private Product productAtTerm(short term, Instant termStartedAt) {
        return Product.builder().id(PRODUCT_ID).status(ProductStatus.ON_SALE)
                .consignmentReceipt(receipt).currentTerm(term).termStartedAt(termStartedAt).build();
    }

    // ==================== sang kỳ (currentTerm++) ====================

    @Test
    @DisplayName("hết kỳ (đã qua termLengthDays) -> sang kỳ mới, dời lại termStartedAt")
    void advanceTerms_dueTerm_incrementsAndResetsStart() {
        Product product = productAtTerm((short) 1, Instant.now().minus(31, ChronoUnit.DAYS));
        when(productRepository.findOnSaleNotYetFinalTerm()).thenReturn(List.of(product));

        Instant before = Instant.now();
        service.processTermsAndDeadlines();
        Instant after = Instant.now();

        assertThat(product.getCurrentTerm()).isEqualTo((short) 2);
        assertThat(product.getTermStartedAt()).isBetween(before, after);
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("chưa hết kỳ -> không đổi gì")
    void advanceTerms_notDueYet_doesNothing() {
        Product product = productAtTerm((short) 1, Instant.now().minus(10, ChronoUnit.DAYS));
        when(productRepository.findOnSaleNotYetFinalTerm()).thenReturn(List.of(product));

        service.processTermsAndDeadlines();

        assertThat(product.getCurrentTerm()).isEqualTo((short) 1);
        verify(productRepository, never()).save(product);
    }

    @Test
    @DisplayName("sản phẩm kỳ 2 hết hạn -> lên kỳ 3 (vẫn do job này xử lý, kỳ cuối do job kia lo)")
    void advanceTerms_term2Due_incrementsToTerm3() {
        Product product = productAtTerm((short) 2, Instant.now().minus(30, ChronoUnit.DAYS));
        when(productRepository.findOnSaleNotYetFinalTerm()).thenReturn(List.of(product));

        service.processTermsAndDeadlines();

        assertThat(product.getCurrentTerm()).isEqualTo((short) 3);
        verify(productRepository).save(product);
    }

    // ==================== hết 90 ngày -> mở EndOfTermDecision ====================

    @Test
    @DisplayName("hết 90 ngày (endOfTermDeadline đã qua) -> gỡ khỏi sàn (WAREHOUSE), mở quyết định cuối kỳ")
    void openEndOfTermDecisions_pastDeadline_delistsAndCreatesDecision() {
        Product product = Product.builder().id(PRODUCT_ID).status(ProductStatus.ON_SALE)
                .endOfTermDeadline(Instant.now().minus(1, ChronoUnit.HOURS)).build();
        when(productRepository.findAllByStatusAndEndOfTermDeadlineBefore(eq(ProductStatus.ON_SALE), any()))
                .thenReturn(List.of(product));

        service.processTermsAndDeadlines();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.WAREHOUSE);
        verify(productRepository).save(product);
        verify(endOfTermDecisionService).createDecision(eq(product), any(Instant.class));
    }

    @Test
    @DisplayName("không có sản phẩm nào hết hạn -> không mở quyết định nào")
    void openEndOfTermDecisions_none_doesNothing() {
        service.processTermsAndDeadlines();

        verify(endOfTermDecisionService, never()).createDecision(any(), any());
    }

    // ==================== TAKE_BACK quá hạn lấy hàng -> UNCLAIMED ====================

    @Test
    @DisplayName("TAKE_BACK quá 30 ngày chưa đến kho nhận hàng -> Product.status = UNCLAIMED")
    void markUnclaimed_overdueTakeBack_setsProductUnclaimed() {
        Product product = Product.builder().id(PRODUCT_ID).status(ProductStatus.WAREHOUSE).build();
        EndOfTermDecision decision = EndOfTermDecision.builder().id(UUID.randomUUID())
                .product(product).decision(EndOfTermChoice.TAKE_BACK)
                .decidedAt(Instant.now().minus(31, ChronoUnit.DAYS)).build();
        when(endOfTermDecisionRepository.findAllByDecisionAndTakenBackAtIsNullAndDecidedAtBefore(
                eq(EndOfTermChoice.TAKE_BACK), any())).thenReturn(List.of(decision));

        service.processTermsAndDeadlines();

        assertThat(product.getStatus()).isEqualTo(ProductStatus.UNCLAIMED);
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("cutoff truyền cho query = now - takeBackPickupDeadlineDays")
    void markUnclaimed_usesConfiguredCutoff() {
        Instant before = Instant.now();
        service.processTermsAndDeadlines();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(endOfTermDecisionRepository).findAllByDecisionAndTakenBackAtIsNullAndDecidedAtBefore(
                eq(EndOfTermChoice.TAKE_BACK), cutoffCaptor.capture());

        assertThat(cutoffCaptor.getValue())
                .isBetween(before.minus(TAKE_BACK_PICKUP_DEADLINE_DAYS, ChronoUnit.DAYS).minusSeconds(2),
                        after.minus(TAKE_BACK_PICKUP_DEADLINE_DAYS, ChronoUnit.DAYS).plusSeconds(2));
    }

    @Test
    @DisplayName("không có TAKE_BACK nào quá hạn -> không đổi gì")
    void markUnclaimed_none_doesNothing() {
        service.processTermsAndDeadlines();

        verify(productRepository, never()).save(any());
    }
}
