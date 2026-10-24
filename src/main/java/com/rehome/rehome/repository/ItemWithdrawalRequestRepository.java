package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ItemWithdrawalRequestRepository extends JpaRepository<ItemWithdrawalRequest, UUID> {
}
