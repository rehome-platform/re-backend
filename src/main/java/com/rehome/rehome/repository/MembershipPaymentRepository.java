package com.rehome.rehome.repository;

import com.rehome.rehome.entity.membership.MembershipPayment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MembershipPaymentRepository extends JpaRepository<MembershipPayment, UUID> {

    List<MembershipPayment> findAllBySubscription_IdOrderByCreatedAtAsc(UUID subscriptionId);

    // IPN MoMo route theo tiền tố SUB- rồi tra dòng này (xem MembershipGatewayRouter)
    Optional<MembershipPayment> findByMomoOrderId(String momoOrderId);

    Optional<MembershipPayment> findByVnpTxnRef(String vnpTxnRef);

    // Lịch sử mua gói của Member: mỗi lần mua là một dòng, mới nhất trước
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"subscription", "subscription.plan"})
    org.springframework.data.domain.Page<MembershipPayment> findAllByMember_IdOrderByCreatedAtDesc(
            UUID memberId, org.springframework.data.domain.Pageable pageable);
}
