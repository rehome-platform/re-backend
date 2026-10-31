package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemWithdrawalRequestRepository extends JpaRepository<ItemWithdrawalRequest, UUID> {
    List<ItemWithdrawalRequest> findByMember_IdAndStatusOrderByCreatedAtAsc(
            UUID memberId, ItemWithdrawalStatus status);

    boolean existsByProduct_IdAndStatus(String productId, ItemWithdrawalStatus status);

    // API 91 (F-033) - map IPN MoMo/VNPay về đúng yêu cầu rút hàng khi không khớp Payment nào
    Optional<ItemWithdrawalRequest> findByMomoOrderId(String momoOrderId);

    Optional<ItemWithdrawalRequest> findByVnpTxnRef(String vnpTxnRef);

    // API 90/F-035 - job quét yêu cầu quá hạn đến kho nhận hàng
    List<ItemWithdrawalRequest> findAllByStatusAndPickupDeadlineBefore(
            ItemWithdrawalStatus status, Instant deadline);
}
