package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsignmentReceiptRepository extends JpaRepository<ConsignmentReceipt, UUID> {
    Optional<ConsignmentReceipt> findByConsignmentForm_Id(UUID consignmentFormId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ConsignmentReceipt r where r.id = :id")
    Optional<ConsignmentReceipt> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByConsignmentFormId(UUID consignmentFormId);

    @Query(value = "SELECT nextval('receipt_no_seq')", nativeQuery = true)
    long nextReceiptSequence();
}
