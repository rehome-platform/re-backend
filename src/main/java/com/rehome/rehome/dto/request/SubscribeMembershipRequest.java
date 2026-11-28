package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.BillingCycle;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeMembershipRequest {

    private UUID planId;

    private BillingCycle billingCycle;

    private OrderPaymentMethod paymentMethod;

    private String redirectUrl;
}
