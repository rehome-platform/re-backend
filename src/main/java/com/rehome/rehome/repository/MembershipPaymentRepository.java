package com.rehome.rehome.repository;

import com.rehome.rehome.entity.membership.MembershipPayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MembershipPaymentRepository extends JpaRepository<MembershipPayment, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<MembershipPayment> {

    List<MembershipPayment> findAllBySubscription_IdOrderByCreatedAtAsc(UUID subscriptionId);


    @org.springframework.data.jpa.repository.Query("""
            select sum(p.amount) from MembershipPayment p
            where p.subscription.id = :subscriptionId
              and p.status = com.rehome.rehome.entity.enums.PaymentStatus.SUCCESS
            """)
    Optional<java.math.BigDecimal> sumSuccessfulPayments(
            @org.springframework.data.repository.query.Param("subscriptionId") UUID subscriptionId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<MembershipPayment> findByVnpTxnRef(String vnpTxnRef);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "subscription")
    Optional<MembershipPayment> findByIdAndMember_Id(UUID id, UUID memberId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"subscription", "subscription.plan"})
    org.springframework.data.domain.Page<MembershipPayment> findAllByMember_IdOrderByCreatedAtDesc(
            UUID memberId, org.springframework.data.domain.Pageable pageable);
}
