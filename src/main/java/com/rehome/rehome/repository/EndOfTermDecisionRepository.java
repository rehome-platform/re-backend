package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.ReConsignStatus;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EndOfTermDecisionRepository extends JpaRepository<EndOfTermDecision, UUID> {

    Optional<EndOfTermDecision> findFirstByProduct_IdAndDecisionIsNullOrderByDeadlineDesc(String productId);

    Optional<EndOfTermDecision> findByNewReceipt_Id(UUID receiptId);

    @Query("select d from EndOfTermDecision d join fetch d.product p "
            + "where d.decision = com.rehome.rehome.entity.enums.EndOfTermChoice.RE_CONSIGN "
            + "and d.newReceipt is null and p.warehouse.id = :warehouseId "
            + "order by d.decidedAt asc")
    List<EndOfTermDecision> findPendingPricingByWarehouse(@Param("warehouseId") UUID warehouseId);

    @Query("select d from EndOfTermDecision d join fetch d.product p join fetch d.newReceipt "
            + "where d.reConsignStatus = com.rehome.rehome.entity.enums.ReConsignStatus.COUNTER_OFFERED "
            + "and p.warehouse.id = :warehouseId order by d.counteredAt asc")
    List<EndOfTermDecision> findCounterOfferedByWarehouse(@Param("warehouseId") UUID warehouseId);

    List<EndOfTermDecision> findAllByReConsignStatusAndProposalDeadlineBefore(
            ReConsignStatus reConsignStatus, Instant proposalDeadline);

    @Query("select d from EndOfTermDecision d join fetch d.product p "
            + "where d.reConsignStatus = com.rehome.rehome.entity.enums.ReConsignStatus.PROPOSED "
            + "and d.proposalDeadline is not null and d.proposalDeadline <= :reminderBefore "
            + "and d.proposalDeadline > :now and d.proposalReminderSentAt is null")
    List<EndOfTermDecision> findDueForProposalReminder(
            @Param("now") Instant now, @Param("reminderBefore") Instant reminderBefore);

    List<EndOfTermDecision> findAllByReConsignStatusAndCounteredAtBefore(
            ReConsignStatus reConsignStatus, Instant counteredAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from EndOfTermDecision d where d.id = :id")
    Optional<EndOfTermDecision> findByIdForUpdate(@Param("id") UUID id);

    @Query("select d from EndOfTermDecision d join fetch d.product p "
            + "where p.id in :ids order by d.deadline desc")
    List<EndOfTermDecision> findByProductIds(@Param("ids") Collection<String> ids);

    @Query("select d from EndOfTermDecision d join fetch d.product p "
            + "where p.member.id = :memberId "
            + "and d.decision = com.rehome.rehome.entity.enums.EndOfTermChoice.TAKE_BACK "
            + "and d.takenBackAt is null order by d.deadline asc")
    List<EndOfTermDecision> findPendingTakeBackByMemberId(@Param("memberId") UUID memberId);

    @Query("select d from EndOfTermDecision d join fetch d.product p "
            + "where d.decision = :decision and d.takenBackAt is null and d.decidedAt < :cutoff")
    List<EndOfTermDecision> findAllByDecisionAndTakenBackAtIsNullAndDecidedAtBefore(
            @Param("decision") EndOfTermChoice decision, @Param("cutoff") Instant cutoff);
}
