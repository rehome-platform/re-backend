package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemWithdrawalRequestRepository extends JpaRepository<ItemWithdrawalRequest, UUID> {
    List<ItemWithdrawalRequest> findByMember_IdAndStatusOrderByCreatedAtAsc(
            UUID memberId, ItemWithdrawalStatus status);

    boolean existsByProduct_IdAndStatusIn(String productId, Collection<ItemWithdrawalStatus> statuses);

    Optional<ItemWithdrawalRequest> findFirstByProduct_IdAndStatusIn(
            String productId, Collection<ItemWithdrawalStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ItemWithdrawalRequest r where r.id = :id")
    Optional<ItemWithdrawalRequest> findByIdForUpdate(@Param("id") UUID id);


    Optional<ItemWithdrawalRequest> findByVnpTxnRef(String vnpTxnRef);

    List<ItemWithdrawalRequest> findAllByStatusAndPickupDeadlineBefore(
            ItemWithdrawalStatus status, Instant deadline);

    @Query("select r from ItemWithdrawalRequest r "
            + "where r.status = :status and r.feePaidAt is null "
            + "and r.feePaymentDeadline is not null and r.feePaymentDeadline < :now")
    List<ItemWithdrawalRequest> findAllByStatusAndFeePaymentOverdue(
            @Param("status") ItemWithdrawalStatus status, @Param("now") Instant now);

    @Query("select r from ItemWithdrawalRequest r "
            + "join fetch r.product p join fetch p.warehouse "
            + "where r.id = :id")
    Optional<ItemWithdrawalRequest> findDetailById(@Param("id") UUID id);

    @Query("select r from ItemWithdrawalRequest r "
            + "join fetch r.product p join fetch p.warehouse "
            + "where r.member.id = :memberId and (:status is null or r.status = :status) "
            + "order by r.createdAt desc")
    List<ItemWithdrawalRequest> findDetailsByMember(
            @Param("memberId") UUID memberId, @Param("status") ItemWithdrawalStatus status);

    @Query("select r from ItemWithdrawalRequest r "
            + "where r.product.id in :productIds and r.status in :statuses")
    List<ItemWithdrawalRequest> findByProductIdsAndStatusIn(
            @Param("productIds") List<String> productIds, @Param("statuses") Collection<ItemWithdrawalStatus> statuses);

    @Query("select r from ItemWithdrawalRequest r join fetch r.product where r.id in :ids")
    List<ItemWithdrawalRequest> findAllWithProductByIdIn(@Param("ids") Collection<UUID> ids);
}
