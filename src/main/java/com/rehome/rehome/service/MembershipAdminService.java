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

    // §9.1 - cả 4 gói kèm activeSubscriberCount (màn W-01). Không có DELETE: ngừng bán = active false.
    List<AdminMembershipPlanResponse> getPlans();

    AdminMembershipPlanResponse getPlan(UUID id);

    /**
     * §9.1 - sửa giá + hạn mức + mô tả + active. Ghi audit {@code UPDATE_MEMBERSHIP_PLAN}.
     *
     * <p>Quy tắc áp dụng (§17 mục 15):
     * <ul>
     *   <li><b>Đổi giá</b>: chỉ áp cho lần mua/gia hạn sau, người đã mua giữ {@code price_paid} cũ.</li>
     *   <li><b>Tăng</b> hạn mức: áp ngay cho mọi subscription ACTIVE - service cập nhật snapshot.</li>
     *   <li><b>Giảm</b> hạn mức: chỉ áp từ kỳ sau, KHÔNG đụng snapshot của subscription ACTIVE.</li>
     * </ul>
     */
    AdminMembershipPlanResponse updatePlan(Account admin, UUID id, UpdateMembershipPlanRequest request);

    // §9.2 - danh sách có lọc + phân trang (màn W-02)
    PageResponse<AdminMembershipSubscriptionResponse> searchSubscriptions(
            MembershipTier tier, SubscriptionStatus status, String q, Integer expiringInDays,
            int page, int size);

    // §9.2 - chi tiết kèm hạn mức đã dùng, lịch sử thanh toán, voucher đã nhận (màn W-03)
    AdminMembershipSubscriptionResponse getSubscription(UUID id);

    // §9.2 - Admin tặng/gia hạn tay. Ghi audit GRANT_MEMBERSHIP.
    AdminMembershipSubscriptionResponse grant(Account admin, GrantMembershipRequest request);

    // §9.2 - Admin hủy kèm lý do. Ghi audit CANCEL_MEMBERSHIP.
    AdminMembershipSubscriptionResponse cancel(Account admin, UUID id, CancelMembershipRequest request);
}
