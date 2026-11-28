package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.membership.MemberVoucher;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MemberVoucherRepository extends JpaRepository<MemberVoucher, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from MemberVoucher v where upper(v.code) = upper(:code)")
    Optional<MemberVoucher> findByCodeForUpdate(@Param("code") String code);

    @EntityGraph(attributePaths = {"voucherTemplate"})
    @Query("""
            select v from MemberVoucher v
            where v.member.id = :memberId
            order by case when v.status = com.rehome.rehome.entity.enums.MemberVoucherStatus.UNUSED
                          then 0 else 1 end,
                     v.expiresAt asc
            """)
    Page<MemberVoucher> findWallet(@Param("memberId") UUID memberId, Pageable pageable);

    @EntityGraph(attributePaths = {"voucherTemplate"})
    Page<MemberVoucher> findByMember_IdAndStatusOrderByExpiresAtAsc(
            UUID memberId, MemberVoucherStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"voucherTemplate"})
    @Query("""
            select v from MemberVoucher v
            where v.member.id = :memberId
              and v.status = com.rehome.rehome.entity.enums.MemberVoucherStatus.UNUSED
              and v.expiresAt > :now
            order by v.expiresAt asc
            """)
    List<MemberVoucher> findUsable(@Param("memberId") UUID memberId, @Param("now") Instant now);

    long countByMember_IdAndStatus(UUID memberId, MemberVoucherStatus status);

    long countByMember_IdAndStatusAndExpiresAtBefore(UUID memberId, MemberVoucherStatus status, Instant moment);

    List<MemberVoucher> findAllByStatusAndExpiresAtBefore(MemberVoucherStatus status, Instant moment);

    long countBySourceSubscription_IdAndVoucherTemplate_IdAndIssuedAtGreaterThanEqualAndIssuedAtLessThan(
            UUID subscriptionId, UUID templateId, Instant from, Instant to);

    @EntityGraph(attributePaths = {"voucherTemplate"})
    List<MemberVoucher> findAllBySourceSubscription_IdOrderByIssuedAtAsc(UUID subscriptionId);

    boolean existsByCode(String code);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("""
            select v from MemberVoucher v
            where v.status = com.rehome.rehome.entity.enums.MemberVoucherStatus.UNUSED
              and v.remindedAt is null and v.expiresAt > :now and v.expiresAt <= :until
            order by v.id
            """)
    java.util.List<MemberVoucher> findDueForReminder(
            @org.springframework.data.repository.query.Param("now") Instant now,
            @org.springframework.data.repository.query.Param("until") Instant until);
}
