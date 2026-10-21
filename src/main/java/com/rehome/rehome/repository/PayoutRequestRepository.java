package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.PayoutRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, UUID> {
    long countByWallet_IdAndCreatedAtBetween(UUID walletId, Instant from, Instant to);

    long countByPayoutNoStartingWith(String prefix);

    Page<PayoutRequest> findByWallet_Id(UUID walletId, Pageable pageable);
}
