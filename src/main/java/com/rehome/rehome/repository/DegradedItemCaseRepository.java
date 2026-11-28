package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.DegradedItemCase;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DegradedItemCaseRepository extends JpaRepository<DegradedItemCase, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DegradedItemCase c where c.id = :id")
    Optional<DegradedItemCase> findByIdForUpdate(@Param("id") UUID id);

    @Query("select c from DegradedItemCase c "
            + "join fetch c.product p join fetch p.member "
            + "left join fetch c.returnRequest "
            + "where c.id = :id")
    Optional<DegradedItemCase> findDetailById(@Param("id") UUID id);

    @Query("select c from DegradedItemCase c "
            + "join fetch c.product p join fetch p.member "
            + "left join fetch c.returnRequest "
            + "where p.member.id = :memberId "
            + "and (:pendingOnly = false or c.choice is null) "
            + "and (:decidedOnly = false or c.choice is not null) "
            + "order by c.createdAt desc")
    List<DegradedItemCase> findDetailsByMember(@Param("memberId") UUID memberId,
                                               @Param("pendingOnly") boolean pendingOnly,
                                               @Param("decidedOnly") boolean decidedOnly);

    @Query("select c from DegradedItemCase c "
            + "join fetch c.product p join fetch p.member "
            + "left join fetch c.returnRequest "
            + "where p.warehouse.id = :warehouseId "
            + "and (:pendingOnly = false or c.choice is null) "
            + "and (:decidedOnly = false or c.choice is not null) "
            + "order by c.createdAt desc")
    List<DegradedItemCase> findDetailsByWarehouse(@Param("warehouseId") UUID warehouseId,
                                                   @Param("pendingOnly") boolean pendingOnly,
                                                   @Param("decidedOnly") boolean decidedOnly);

    @Query("select c from DegradedItemCase c "
            + "where c.product.id in :productIds and c.choice is null")
    List<DegradedItemCase> findPendingByProductIds(@Param("productIds") List<String> productIds);

    @Query("select c from DegradedItemCase c where c.product.id in :productIds and "
            + "(c.choice is null or (c.choice = com.rehome.rehome.entity.enums.DegradedItemChoice.PARTIAL_WITH_ITEM_BACK "
            + "and c.pickedUpAt is null and c.itemDonated = false)) order by c.createdAt desc, c.id desc")
    List<DegradedItemCase> findOpenByProductIds(@Param("productIds") List<String> productIds);

    @Query("select c from DegradedItemCase c join fetch c.product p "
            + "where p.member.id = :memberId "
            + "and c.choice = com.rehome.rehome.entity.enums.DegradedItemChoice.PARTIAL_WITH_ITEM_BACK "
            + "and c.pickedUpAt is null and c.itemDonated = false order by c.decidedAt asc")
    List<DegradedItemCase> findPendingPickupByMemberId(@Param("memberId") UUID memberId);

    Optional<DegradedItemCase> findFirstByProduct_IdOrderByCreatedAtDesc(String productId);

    boolean existsByProduct_IdAndChoiceIsNull(String productId);

    List<DegradedItemCase> findAllByChoiceIsNullAndDecisionDeadlineBefore(Instant deadline);

    @Query("select c from DegradedItemCase c join fetch c.product p "
            + "where c.choice is null and c.decisionDeadline is not null and c.decisionDeadline > :now "
            + "and ((c.lastReminderSentAt is null and c.createdAt <= :reminderCutoff) "
            + "or (c.lastReminderSentAt is not null and c.lastReminderSentAt <= :reminderCutoff))")
    List<DegradedItemCase> findDueForReminder(
            @Param("now") Instant now, @Param("reminderCutoff") Instant reminderCutoff);
}
