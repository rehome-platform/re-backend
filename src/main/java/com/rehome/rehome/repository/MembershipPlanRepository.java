package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.membership.MembershipPlan;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MembershipPlanRepository extends JpaRepository<MembershipPlan, UUID> {

    Optional<MembershipPlan> findByTier(MembershipTier tier);

    // GET /memberships/plans: chỉ gói đang bán, FREE bị loại vì purchasable = false
    List<MembershipPlan> findAllByActiveTrueAndPurchasableTrueOrderBySortOrderAsc();

    // Màn Admin W-01: cả 4 dòng kể cả FREE
    List<MembershipPlan> findAllByOrderBySortOrderAsc();
}
