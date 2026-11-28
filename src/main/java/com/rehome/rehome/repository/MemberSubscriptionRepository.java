package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import com.rehome.rehome.entity.membership.MemberSubscription;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberSubscriptionRepository extends JpaRepository<MemberSubscription, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MemberSubscription s where s.member.id = :memberId and s.status = "
            + "com.rehome.rehome.entity.enums.SubscriptionStatus.ACTIVE")
    Optional<MemberSubscription> findActiveByMemberIdForUpdate(@Param("memberId") UUID memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MemberSubscription s where s.id = :id")
    Optional<MemberSubscription> findByIdForUpdate(@Param("id") UUID id);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("""
            update MemberSubscription s
            set s.status = com.rehome.rehome.entity.enums.SubscriptionStatus.CANCELLED,
                s.cancelledAt = :now, s.cancelReason = 'VNPay payment window expired'
            where s.status = com.rehome.rehome.entity.enums.SubscriptionStatus.PENDING_PAYMENT
              and s.createdAt <= :cutoff
              and (:memberId is null or s.member.id = :memberId)
            """)
    int cancelExpiredPendingPayments(@Param("memberId") UUID memberId, @Param("cutoff") Instant cutoff,
                                     @Param("now") Instant now);

    @EntityGraph(attributePaths = {"plan"})
    @Query("select s from MemberSubscription s where s.member.id = :memberId and s.status = "
            + "com.rehome.rehome.entity.enums.SubscriptionStatus.ACTIVE")
    Optional<MemberSubscription> findActiveByMemberId(@Param("memberId") UUID memberId);

    List<MemberSubscription> findAllByMember_IdAndStatus(UUID memberId, SubscriptionStatus status);

    long countByPlan_IdAndStatus(UUID planId, SubscriptionStatus status);

    List<MemberSubscription> findAllByTierAndStatus(MembershipTier tier, SubscriptionStatus status);

    @EntityGraph(attributePaths = {"member"})
    List<MemberSubscription> findAllByStatusAndExpiresAtBefore(SubscriptionStatus status, Instant moment);

    @EntityGraph(attributePaths = {"member", "plan"})
    @Query("select s from MemberSubscription s where s.status = :status "
            + "and s.expiresAt > :now and s.expiresAt <= :cutoff and s.expiryReminderSentAt is null")
    List<MemberSubscription> findExpiringSoon(@Param("status") SubscriptionStatus status,
                                              @Param("now") Instant now,
                                              @Param("cutoff") Instant cutoff);

    @EntityGraph(attributePaths = {"member"})
    @Query("select s from MemberSubscription s where s.status = :status and s.expiresAt > :now "
            + "and (s.lastVoucherIssuedAt is null or s.lastVoucherIssuedAt <= :cutoff)")
    List<MemberSubscription> findDueForVoucherIssue(@Param("status") SubscriptionStatus status,
                                                     @Param("now") Instant now,
                                                     @Param("cutoff") Instant cutoff);

    @EntityGraph(attributePaths = {"member", "plan", "grantedByAdmin"})
    Optional<MemberSubscription> findWithDetailsById(UUID id);
}
