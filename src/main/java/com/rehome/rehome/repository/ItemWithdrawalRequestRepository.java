package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemWithdrawalRequestRepository extends JpaRepository<ItemWithdrawalRequest, UUID> {

    List<ItemWithdrawalRequest> findByMember_IdAndStatusOrderByCreatedAtAsc(
            UUID memberId, ItemWithdrawalStatus status);
}