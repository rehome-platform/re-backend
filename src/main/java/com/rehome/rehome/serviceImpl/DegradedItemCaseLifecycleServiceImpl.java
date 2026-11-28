package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.util.NotificationFormatting;

import com.rehome.rehome.entity.enums.DegradedItemChoice;
import com.rehome.rehome.entity.enums.NotificationTargetType;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.DegradedItemCase;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.repository.DegradedItemCaseRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.DegradedItemCaseLifecycleService;
import com.rehome.rehome.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class DegradedItemCaseLifecycleServiceImpl implements DegradedItemCaseLifecycleService {

    private final DegradedItemCaseRepository degradedItemCaseRepository;
    private final ProductRepository productRepository;
    private final NotificationService notificationService;

    @Value("${app.degraded-item.reminder-interval-days}")
    private long reminderIntervalDays;

    @Override
    @Scheduled(fixedRateString = "${app.degraded-item.auto-process-check-interval-ms}")
    @Transactional
    public void processRemindersAndDeadlines() {
        Instant now = Instant.now();
        sendReminders(now);
        markUnclaimedForOverdueDecisions(now);
    }

    private void sendReminders(Instant now) {
        Instant reminderCutoff = now.minus(reminderIntervalDays, ChronoUnit.DAYS);

        for (DegradedItemCase degradedCase : degradedItemCaseRepository.findDueForReminder(now, reminderCutoff)) {
            Product product = degradedCase.getProduct();

            notificationService.notify(
                    product.getMember(),
                    NotificationType.TERM_EXPIRY,
                    "Nhắc chốt phương án đền bù hàng xuống cấp",
                    "Sản phẩm \"" + product.getName() + "\" đang chờ bạn chọn phương án đền bù, "
                            + "hạn đến " + NotificationFormatting.deadline(degradedCase.getDecisionDeadline()) + ".",
                    NotificationTargetType.DEGRADED_ITEM_CASE,
                    degradedCase.getId(),
                    null);

            degradedCase.setLastReminderSentAt(now);
            degradedItemCaseRepository.save(degradedCase);

            log.info(">>>> Da nhac Member chot phuong an den bu: degradedItemCaseId={}", degradedCase.getId());
        }
    }

    private void markUnclaimedForOverdueDecisions(Instant now) {
        for (DegradedItemCase degradedCase : degradedItemCaseRepository
                .findAllByChoiceIsNullAndDecisionDeadlineBefore(now)) {
            degradedCase.setChoice(DegradedItemChoice.UNCLAIMED);
            degradedCase.setDecidedAt(now);
            degradedItemCaseRepository.save(degradedCase);

            Product product = degradedCase.getProduct();
            product.setStatus(ProductStatus.UNCLAIMED);
            productRepository.save(product);

            log.info(">>>> Qua han chon phuong an den bu, danh dau vo thua nhan: degradedItemCaseId={}, productId={}",
                    degradedCase.getId(), product.getId());
        }
    }
}
