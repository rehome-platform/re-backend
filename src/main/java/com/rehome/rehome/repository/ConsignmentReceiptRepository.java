package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsignmentReceiptRepository extends JpaRepository<ConsignmentReceipt, UUID> {
    Optional<ConsignmentReceipt> findByConsignmentForm_Id(UUID consignmentFormId);
}
