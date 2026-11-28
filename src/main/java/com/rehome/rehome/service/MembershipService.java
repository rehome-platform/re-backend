package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.SubscribeMembershipRequest;
import com.rehome.rehome.dto.response.AiTryOnQuotaResponse;
import com.rehome.rehome.dto.response.MembershipPlanResponse;
import com.rehome.rehome.dto.response.MembershipPurchaseResponse;
import com.rehome.rehome.dto.response.MyMembershipResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.SubscribeMembershipResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import java.util.List;
import java.util.UUID;

public interface MembershipService {

    List<MembershipPlanResponse> getPlans();

    MyMembershipResponse getMyMembership(Account account);

    SubscribeMembershipResponse subscribe(Account account, SubscribeMembershipRequest request);

    PageResponse<MembershipPurchaseResponse> getPurchaseHistory(Account account, int page, int size);

    AiTryOnQuotaResponse getAiTryOnQuota(Account account);

    void applyGatewayPaymentSuccess(UUID subscriptionId, OrderPaymentMethod method, String note);

    boolean applyVnPayResult(String vnpTxnRef, String responseCode, String transactionNo);
}
