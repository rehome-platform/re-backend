package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.repository.ItemWithdrawalRequestRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.service.ItemWithdrawalAutoExpireService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

// F-035: Member không đến kho nhận lại hàng trong hạn -> hủy yêu cầu, đăng bán lại sản phẩm
@Service
@RequiredArgsConstructor
@Slf4j
public class ItemWithdrawalAutoExpireServiceImpl implements ItemWithdrawalAutoExpireService {

    private final ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    private final ProductRepository productRepository;

    @Override
    @Scheduled(fixedRateString = "${app.item-withdrawal.auto-expire-check-interval-ms}")
    @Transactional
    public void expireUncollectedWithdrawals() {

        List<ItemWithdrawalRequest> expired = itemWithdrawalRequestRepository
                .findAllByStatusAndPickupDeadlineBefore(ItemWithdrawalStatus.PENDING_PICKUP, Instant.now());

        for (ItemWithdrawalRequest withdrawal : expired) {
            withdrawal.setStatus(ItemWithdrawalStatus.EXPIRED);
            itemWithdrawalRequestRepository.save(withdrawal);

            // Hàng chưa rời kho (F-034 mới xóa shelfLocation lúc bàn giao thật), nên chỉ cần đổi
            // trạng thái niêm yết về lại ON_SALE - không có gì để ghi StockMovementLog ở đây
            Product product = productRepository.findByIdForUpdate(withdrawal.getProduct().getId()).orElse(null);
            if (product != null && product.getStatus() == ProductStatus.WITHDRAWN) {
                product.setStatus(ProductStatus.ON_SALE);
                productRepository.save(product);
            }

            log.info(">>>> Het han nhan lai hang rut: itemWithdrawalRequestId={}, productId={}",
                    withdrawal.getId(), withdrawal.getProduct().getId());
        }
    }
}
