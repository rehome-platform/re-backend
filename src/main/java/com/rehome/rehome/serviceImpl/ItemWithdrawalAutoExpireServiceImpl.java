package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.repository.ItemWithdrawalRequestRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.ItemWithdrawalAutoExpireService;
import com.rehome.rehome.service.QueueService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ItemWithdrawalAutoExpireServiceImpl implements ItemWithdrawalAutoExpireService {

    private final ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    private final ProductRepository productRepository;
    private final QueueService queueService;

    @Override
    @Scheduled(fixedRateString = "${app.item-withdrawal.auto-expire-check-interval-ms}")
    @Transactional
    public void expireUncollectedWithdrawals() {
        Instant now = Instant.now();

        List<ItemWithdrawalRequest> pickupExpired = itemWithdrawalRequestRepository
                .findAllByStatusAndPickupDeadlineBefore(ItemWithdrawalStatus.PENDING_PICKUP, now);
        expireAndRelist(pickupExpired, "Het han nhan lai hang rut");

        List<ItemWithdrawalRequest> feeOverdue = itemWithdrawalRequestRepository
                .findAllByStatusAndFeePaymentOverdue(ItemWithdrawalStatus.PENDING_PICKUP, now);
        expireAndRelist(feeOverdue, "Qua han tra phi rut hang");
    }

    private void expireAndRelist(List<ItemWithdrawalRequest> withdrawals, String logReason) {
        for (ItemWithdrawalRequest withdrawal : withdrawals) {
            withdrawal.setStatus(ItemWithdrawalStatus.EXPIRED);
            itemWithdrawalRequestRepository.save(withdrawal);

            queueService.dequeue(QueueType.WITHDRAWAL, withdrawal.getId().toString());

            Product product = productRepository.findByIdForUpdate(withdrawal.getProduct().getId()).orElse(null);
            if (product != null && (product.getStatus() == ProductStatus.WAREHOUSE
                    || product.getStatus() == ProductStatus.WITHDRAWN)) {
                product.setStatus(ProductStatus.ON_SALE);
                productRepository.save(product);
            }

            log.info(">>>> {}: itemWithdrawalRequestId={}, productId={}",
                    logReason, withdrawal.getId(), withdrawal.getProduct().getId());
        }
    }
}
