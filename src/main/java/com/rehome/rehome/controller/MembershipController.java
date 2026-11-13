package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.SubscribeMembershipRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.MembershipPlanResponse;
import com.rehome.rehome.dto.response.MembershipPurchaseResponse;
import com.rehome.rehome.dto.response.MyMembershipResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.SubscribeMembershipResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Gói thành viên VIP - màn M-01 (chọn gói), M-02 (xác nhận mua), M-03 (gói của tôi). */
@RestController
@RequestMapping("/memberships")
@RequiredArgsConstructor
@Tag(name = "Membership")
public class MembershipController {

    private final MembershipService membershipService;

    // §8.1 - PUBLIC: khách chưa đăng nhập cũng xem được để quyết định đăng ký.
    // Đường dẫn này phải nằm trong SecurityConfig.PUBLIC_ENDPOINTS.
    @GetMapping("/plans")
    @Operation(summary = "Danh sách gói thành viên đang bán (public). Hạn mức null = không giới hạn")
    public ResponseEntity<ApiResponse<List<MembershipPlanResponse>>> getPlans() {
        return ResponseEntity.ok(ApiResponse.success(membershipService.getPlans()));
    }

    // §8.2 - LUÔN 200: chưa mua gói thì trả tier FREE, không trả 404
    @GetMapping("/me")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Gói hiện tại + hạn mức đã dùng. Luôn trả 200, chưa mua gói thì tier = FREE")
    public ResponseEntity<ApiResponse<MyMembershipResponse>> getMyMembership(
            @AuthenticationPrincipal Account account) {
        requireAccount(account);
        return ResponseEntity.ok(ApiResponse.success(membershipService.getMyMembership(account)));
    }

    @PostMapping("/subscribe")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Mua / gia hạn gói. Ví kích hoạt ngay; MoMo/VNPay trả payUrl và chờ IPN")
    public ResponseEntity<ApiResponse<SubscribeMembershipResponse>> subscribe(
            @AuthenticationPrincipal Account account,
            @RequestBody SubscribeMembershipRequest request) {
        requireAccount(account);
        SubscribeMembershipResponse result = membershipService.subscribe(account, request);

        String message = result.getStatus() == SubscriptionStatus.ACTIVE
                ? "Đăng ký gói thành công"
                : "Vui lòng hoàn tất thanh toán";
        return ResponseEntity.status(201).body(ApiResponse.success(result, 201, message));
    }

    @GetMapping("/me/history")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Lịch sử mua gói của chính mình")
    public ResponseEntity<ApiResponse<PageResponse<MembershipPurchaseResponse>>> getHistory(
            @AuthenticationPrincipal Account account,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAccount(account);
        return ResponseEntity.ok(ApiResponse.success(
                membershipService.getPurchaseHistory(account, page, size)));
    }

    private static void requireAccount(Account account) {
        if (account == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }
}
