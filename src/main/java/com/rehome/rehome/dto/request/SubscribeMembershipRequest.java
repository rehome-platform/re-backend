package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.BillingCycle;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// POST /memberships/subscribe. Danh tính người mua lấy từ Authentication, không có field memberId.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeMembershipRequest {

    private UUID planId;

    // MONTHLY / YEARLY. Chọn YEARLY mà gói không có pricePerYear -> 400 MEMBERSHIP_PLAN_YEARLY_NOT_AVAILABLE
    private BillingCycle billingCycle;

    // WALLET / MOMO / VNPAY
    private OrderPaymentMethod paymentMethod;

    // Chỉ bắt buộc khi paymentMethod là MOMO/VNPAY - nơi cổng redirect về sau khi trả tiền
    private String redirectUrl;
}
