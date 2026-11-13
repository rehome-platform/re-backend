package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CancelMembershipRequest;
import com.rehome.rehome.dto.request.GrantMembershipRequest;
import com.rehome.rehome.dto.request.UpdateMembershipPlanRequest;
import com.rehome.rehome.dto.response.AdminMembershipPlanResponse;
import com.rehome.rehome.dto.response.AdminMembershipSubscriptionResponse;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.MembershipStatsResponse;
import com.rehome.rehome.dto.response.VoucherStatsResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import com.rehome.rehome.service.MembershipAdminService;
import com.rehome.rehome.service.MembershipStatsService;
import com.rehome.rehome.util.AdminDateRange;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin - cấu hình gói thành viên (W-01) và quản lý thành viên VIP (W-02, W-03).
 *
 * <p>Không có DELETE cho gói: subscription cũ vẫn trỏ vào plan. Ngừng bán = {@code active: false}.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Membership")
public class MembershipAdminController {

    private final MembershipAdminService membershipAdminService;
    private final MembershipStatsService membershipStatsService;

    // ---------------------------------------------------------------- Gói (W-01)

    @GetMapping("/membership-plans")
    @Operation(summary = "4 gói kèm activeSubscriberCount. Mọi ô đều sửa được, kể cả giá tháng/năm")
    public ResponseEntity<ApiResponse<List<AdminMembershipPlanResponse>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.success(membershipAdminService.getPlans(), "OK"));
    }

    @GetMapping("/membership-plans/{id}")
    @Operation(summary = "Chi tiết một gói")
    public ResponseEntity<ApiResponse<AdminMembershipPlanResponse>> getPlan(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(membershipAdminService.getPlan(id), "OK"));
    }

    @PutMapping("/membership-plans/{id}")
    @Operation(summary = "Sửa giá + hạn mức + mô tả + active. Tăng hạn mức áp ngay cho gói ACTIVE, "
            + "giảm chỉ áp từ kỳ sau. Ghi audit UPDATE_MEMBERSHIP_PLAN")
    public ResponseEntity<ApiResponse<AdminMembershipPlanResponse>> updatePlan(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID id,
            @RequestBody UpdateMembershipPlanRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                membershipAdminService.updatePlan(admin, id, request), "OK"));
    }

    // ---------------------------------------------------------------- Thành viên VIP (W-02, W-03)

    @GetMapping("/membership-subscriptions")
    @Operation(summary = "Danh sách thành viên VIP, lọc theo hạng/trạng thái/email-tên/sắp hết hạn")
    public ResponseEntity<ApiResponse<PageResponse<AdminMembershipSubscriptionResponse>>> search(
            @RequestParam(required = false) MembershipTier tier,
            @RequestParam(required = false) SubscriptionStatus status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer expiringInDays,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(membershipAdminService.searchSubscriptions(
                tier, status, q, expiringInDays, page, size), "OK"));
    }

    @GetMapping("/membership-subscriptions/{id}")
    @Operation(summary = "Chi tiết kèm hạn mức đã dùng, lịch sử thanh toán và voucher đã nhận")
    public ResponseEntity<ApiResponse<AdminMembershipSubscriptionResponse>> getSubscription(
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(membershipAdminService.getSubscription(id), "OK"));
    }

    @PostMapping("/membership-subscriptions/grant")
    @Operation(summary = "Tặng / gia hạn gói thủ công (đền bù khiếu nại...). Ghi audit GRANT_MEMBERSHIP")
    public ResponseEntity<ApiResponse<AdminMembershipSubscriptionResponse>> grant(
            @AuthenticationPrincipal Account admin,
            @RequestBody GrantMembershipRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.success(
                membershipAdminService.grant(admin, request), 201, "Đã tặng gói thành viên"));
    }

    // ---------------------------------------------------------------- Thống kê (W-05)

    @GetMapping("/membership-stats")
    @Operation(summary = "Doanh thu gói theo tháng/hạng, số VIP đang hoạt động, mua mới và không gia hạn. "
            + "from/to là yyyy-MM-dd theo giờ VN, bỏ trống = từ đầu / đến hết")
    public ResponseEntity<ApiResponse<MembershipStatsResponse>> membershipStats(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(ApiResponse.success(
                membershipStatsService.getMembershipStats(AdminDateRange.optional(from, to)), "OK"));
    }

    @GetMapping("/voucher-stats")
    @Operation(summary = "Phát / dùng / hết hạn theo mẫu voucher + tổng tiền đã chi. "
            + "Tổng tiền lấy từ sổ ví (dòng VOUCHER_DISCOUNT), không phải mệnh giá mã đã phát")
    public ResponseEntity<ApiResponse<VoucherStatsResponse>> voucherStats(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return ResponseEntity.ok(ApiResponse.success(
                membershipStatsService.getVoucherStats(AdminDateRange.optional(from, to)), "OK"));
    }

    @PostMapping("/membership-subscriptions/{id}/cancel")
    @Operation(summary = "Hủy gói kèm lý do. KHÔNG tự hoàn tiền; voucher đã phát vẫn dùng được. "
            + "Ghi audit CANCEL_MEMBERSHIP")
    public ResponseEntity<ApiResponse<AdminMembershipSubscriptionResponse>> cancel(
            @AuthenticationPrincipal Account admin,
            @PathVariable UUID id,
            @RequestBody CancelMembershipRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                membershipAdminService.cancel(admin, id, request), "Đã hủy gói thành viên"));
    }
}
