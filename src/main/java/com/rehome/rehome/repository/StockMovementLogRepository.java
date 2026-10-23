package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.StockMovementLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StockMovementLogRepository extends JpaRepository<StockMovementLog, UUID> {
    List<StockMovementLog> findByProduct_IdOrderByCreatedAtDesc(String productId);
}
