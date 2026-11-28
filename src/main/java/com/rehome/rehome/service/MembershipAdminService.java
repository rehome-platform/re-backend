package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CancelMembershipRequest;
import com.rehome.rehome.dto.request.GrantMembershipRequest;
import com.rehome.rehome.dto.request.UpdateMembershipPlanRequest;
import com.rehome.rehome.dto.response.AdminMembershipPlanResponse;
import com.rehome.rehome.dto.response.AdminMembershipSubscriptionResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import java.util.List;
import java.util.UUID;

public interface MembershipAdminService {

    List<AdminMembershipPlanResponse> getPlans();

    AdminMembershipPlanResponse getPlan(UUID id);

    AdminMembershipPlanResponse updatePlan(Account admin, UUID id, UpdateMembershipPlanRequest request);

    PageResponse<AdminMembershipSubscriptionResponse> searchSubscriptions(
            MembershipTier tier, SubscriptionStatus status, String q, Integer expiringInDays,
            int page, int size);

    AdminMembershipSubscriptionResponse getSubscription(UUID id);

    AdminMembershipSubscriptionResponse grant(Account admin, GrantMembershipRequest request);

    AdminMembershipSubscriptionResponse cancel(Account admin, UUID id, CancelMembershipRequest request);
}
