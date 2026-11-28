package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.BillingCycle;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipPurchaseResponse {

    private String id;
    private String subscriptionId;
    private MembershipTier tier;
    private String tierName;
    private BillingCycle billingCycle;
    private BigDecimal amount;
    private OrderPaymentMethod method;
    private PaymentStatus status;
    private Instant paidAt;
    private Instant startedAt;
    private Instant expiresAt;
}
