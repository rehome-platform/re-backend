package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ReConsignNegotiationHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReConsignNegotiationHistoryRepository extends JpaRepository<ReConsignNegotiationHistory, UUID> {

    List<ReConsignNegotiationHistory> findByDecision_IdOrderByCreatedAtAsc(UUID decisionId);
}
