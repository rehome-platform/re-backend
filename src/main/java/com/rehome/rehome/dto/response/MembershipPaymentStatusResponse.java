package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.PaymentStatus;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import java.util.UUID;

public record MembershipPaymentStatusResponse(UUID paymentId, PaymentStatus paymentStatus,
                                              UUID subscriptionId, SubscriptionStatus subscriptionStatus) {
}
