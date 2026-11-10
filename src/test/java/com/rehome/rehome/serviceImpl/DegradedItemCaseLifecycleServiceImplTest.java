package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.DegradedItemChoice;
import com.rehome.rehome.entity.enums.NotificationTargetType;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.DegradedItemCase;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.repository.DegradedItemCaseRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.NotificationService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DegradedItemCaseLifecycleServiceImplTest {

    @Mock DegradedItemCaseRepository degradedItemCaseRepository;
    @Mock ProductRepository productRepository;
    @Mock NotificationService notificationService;

    @InjectMocks
    DegradedItemCaseLifecycleServiceImpl service;

    static final long REMINDER_INTERVAL_DAYS = 7;
    static final String PRODUCT_ID = "Q7-AK-000845";

    com.rehome.rehome.entity.auth.Account seller;
    Product product;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(service, "reminderIntervalDays", REMINDER_INTERVAL_DAYS);

        seller = com.rehome.rehome.entity.auth.Account.builder().id(UUID.randomUUID()).build();
        product = Product.builder().id(PRODUCT_ID).name("Áo khoác Uniqlo")
                .status(ProductStatus.WAREHOUSE).member(seller).build();

        when(degradedItemCaseRepository.findDueForReminder(any(), any())).thenReturn(List.of());
        when(degradedItemCaseRepository.findAllByChoiceIsNullAndDecisionDeadlineBefore(any()))
                .thenReturn(List.of());
    }

    private DegradedItemCase caseFor(Instant decisionDeadline, Instant lastReminderSentAt) {
        return DegradedItemCase.builder().id(UUID.randomUUID()).product(product)
                .decisionDeadline(decisionDeadline).lastReminderSentAt(lastReminderSentAt).build();
    }

    // ==================== nhắc nhở cách 7 ngày ====================

    @Test
    @DisplayName("ca đến hạn nhắc -> gửi thông báo TERM_EXPIRY, deep-link DEGRADED_ITEM_CASE, cập nhật lastReminderSentAt")
    void sendReminders_dueCase_notifiesAndUpdatesLastReminder() {
        DegradedItemCase degradedCase = caseFor(Instant.now().plus(10, ChronoUnit.DAYS), null);
        when(degradedItemCaseRepository.findDueForReminder(any(), any())).thenReturn(List.of(degradedCase));

        Instant before = Instant.now();
        service.processRemindersAndDeadlines();
        Instant after = Instant.now();

        verify(notificationService).notify(
                eq(seller), eq(NotificationType.TERM_EXPIRY), any(), any(),
                eq(NotificationTargetType.DEGRADED_ITEM_CASE), eq(degradedCase.getId()), isNull());

        assertThat(degradedCase.getLastReminderSentAt()).isBetween(before, after);
        verify(degradedItemCaseRepository).save(degradedCase);
    }

    @Test
    @DisplayName("không có ca nào đến hạn nhắc -> không gửi gì")
    void sendReminders_none_doesNothing() {
        service.processRemindersAndDeadlines();

        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("cutoff truyền cho query = now - reminderIntervalDays")
    void sendReminders_usesConfiguredCutoff() {
        Instant before = Instant.now();
        service.processRemindersAndDeadlines();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> nowCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> cutoffCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(degradedItemCaseRepository).findDueForReminder(nowCaptor.capture(), cutoffCaptor.capture());

        assertThat(nowCaptor.getValue()).isBetween(before, after);
        assertThat(cutoffCaptor.getValue())
                .isBetween(before.minus(REMINDER_INTERVAL_DAYS, ChronoUnit.DAYS).minusSeconds(2),
                        after.minus(REMINDER_INTERVAL_DAYS, ChronoUnit.DAYS).plusSeconds(2));
    }

    // ==================== quá hạn chọn phương án -> vô thừa nhận ====================

    @Test
    @DisplayName("quá decisionDeadline mà choice vẫn null -> choice=UNCLAIMED, product.status=UNCLAIMED")
    void markUnclaimed_overdueCase_setsChoiceAndProductStatus() {
        DegradedItemCase degradedCase = caseFor(Instant.now().minus(1, ChronoUnit.HOURS), null);
        when(degradedItemCaseRepository.findAllByChoiceIsNullAndDecisionDeadlineBefore(any()))
                .thenReturn(List.of(degradedCase));

        Instant before = Instant.now();
        service.processRemindersAndDeadlines();
        Instant after = Instant.now();

        assertThat(degradedCase.getChoice()).isEqualTo(DegradedItemChoice.UNCLAIMED);
        assertThat(degradedCase.getDecidedAt()).isBetween(before, after);
        verify(degradedItemCaseRepository).save(degradedCase);

        assertThat(product.getStatus()).isEqualTo(ProductStatus.UNCLAIMED);
        verify(productRepository).save(product);
    }

    @Test
    @DisplayName("không ca nào quá hạn -> không đổi gì")
    void markUnclaimed_none_doesNothing() {
        service.processRemindersAndDeadlines();

        verify(productRepository, never()).save(any());
    }
}
