package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsignmentReceiptRepository extends JpaRepository<ConsignmentReceipt, UUID> {
    Optional<ConsignmentReceipt> findFirstByConsignmentForm_IdOrderByCreatedAtDesc(UUID consignmentFormId);

    default Optional<ConsignmentReceipt> findByConsignmentForm_Id(UUID consignmentFormId) {
        return findFirstByConsignmentForm_IdOrderByCreatedAtDesc(consignmentFormId);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ConsignmentReceipt r where r.id = :id")
    Optional<ConsignmentReceipt> findByIdForUpdate(@Param("id") UUID id);

    Optional<ConsignmentReceipt> findByPreviousReceipt_Id(UUID id);

    @Query(value = "SELECT nextval('receipt_no_seq')", nativeQuery = true)
    long nextReceiptSequence();

    @Query("select r from ConsignmentReceipt r "
            + "where r.consignmentForm.id in :formIds "
            + "and (r.status is null or r.status <> com.rehome.rehome.entity.enums.ReceiptStatus.VOIDED) "
            + "and not exists (select 1 from ConsignmentReceipt r2 "
            + "where r2.consignmentForm = r.consignmentForm and r2.createdAt > r.createdAt and (r2.status is null or r2.status <> com.rehome.rehome.entity.enums.ReceiptStatus.VOIDED))")
    List<ConsignmentReceipt> findLatestByFormIds(@Param("formIds") Collection<UUID> formIds);
}