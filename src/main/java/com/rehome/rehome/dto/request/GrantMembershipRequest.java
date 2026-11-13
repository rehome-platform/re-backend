package com.rehome.rehome.dto.request;

import com.rehome.rehome.entity.enums.MembershipTier;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST /api/admin/membership-subscriptions/grant - Admin tặng/gia hạn tay (đền bù khiếu nại...).
 * Không thu tiền nên không ghi {@code membership_payments} và {@code price_paid = 0}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrantMembershipRequest {

    private UUID memberId;
    // Không nhận FREE: tặng hạng FREE là vô nghĩa (FREE là mặc định của mọi Member)
    private MembershipTier tier;
    // Số tháng tặng, 1..24
    private Integer months;
    // Bắt buộc - ghi vào audit log GRANT_MEMBERSHIP
    private String reason;
}
