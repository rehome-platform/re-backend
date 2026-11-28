package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscribeMembershipResponse {

    private String subscriptionId;
    private java.util.UUID paymentId;
    private SubscriptionStatus status;
    private MembershipTier tier;
    private String tierName;
    private Instant expiresAt;
    private String payUrl;
    private int vouchersIssued;
    private MembershipTier replacedTier;
}
