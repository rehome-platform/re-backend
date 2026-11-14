package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.membership.MembershipPayment;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Một dòng giao dịch mua gói trong màn thanh toán gói của Admin. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminMembershipPaymentResponse {
    private String id;
    private String memberName;
    private String memberEmail;
    private String subscriptionId;
    private String tier;
    private BigDecimal amount;
    private String method;
    // PENDING / SUCCESS / FAILED (giá trị của PaymentStatus)
    private String status;
    private Instant createdAt;
    // null khi chưa thanh toán xong
    private Instant paidAt;

    public static AdminMembershipPaymentResponse from(MembershipPayment p, String memberName) {
        return AdminMembershipPaymentResponse.builder()
                .id(p.getId().toString())
                .memberName(memberName)
                .memberEmail(p.getMember().getEmail())
                .subscriptionId(p.getSubscription().getId().toString())
                .tier(p.getSubscription().getTier().name())
                .amount(p.getAmount())
                .method(p.getMethod().name())
                .status(p.getStatus().name())
                .createdAt(p.getCreatedAt())
                .paidAt(p.getPaidAt())
                .build();
    }
}
