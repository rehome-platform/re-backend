package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateReturnRequestRequest;
import com.rehome.rehome.dto.request.InvalidReturnChoiceRequest;
import com.rehome.rehome.dto.request.MarkLostRequest;
import com.rehome.rehome.dto.request.PayShipBackFeeRequest;
import com.rehome.rehome.dto.request.RejectReturnRequestRequest;
import com.rehome.rehome.dto.request.ResolveReturnRequestRequest;
import com.rehome.rehome.dto.request.StartInspectionRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.PayShipBackFeeResponse;
import com.rehome.rehome.dto.response.ReturnRequestResponse;
import com.rehome.rehome.dto.response.ShipBackPaymentStatusResponse;
import com.rehome.rehome.dto.response.ShipBackQuoteResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.ReturnRequestService;
import com.rehome.rehome.service.ReturnShipBackService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/return-requests")
@RequiredArgsConstructor
public class ReturnRequestController {

    private final ReturnRequestService returnRequestService;
    private final ReturnShipBackService returnShipBackService;

    @PostMapping
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> create(
            Authentication authentication,
            @RequestBody CreateReturnRequestRequest request) {

        String email = authentication.getName();
        ReturnRequestResponse result = returnRequestService.create(email, request);

        return ResponseEntity.ok(ApiResponse.success(result, HttpStatus.CREATED.value(),
                "Đã gửi yêu cầu trả hàng"));
    }

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PageResponse<ReturnRequestResponse>>> getMyReturnRequests(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String email = authentication.getName();

        return ResponseEntity.ok(ApiResponse.success(
                returnRequestService.getMyReturnRequests(email, page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> getMyReturnRequest(
            Authentication authentication,
            @PathVariable UUID id) {

        String email = authentication.getName();

        return ResponseEntity.ok(ApiResponse.success(
                returnRequestService.getMyReturnRequest(email, id)));
    }

    @PostMapping("/{id}/handover")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> confirmHandover(
            Authentication authentication,
            @PathVariable UUID id) {

        String email = authentication.getName();
        ReturnRequestResponse result = returnRequestService.confirmHandover(email, id);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã ghi nhận bàn giao cho GHN"));
    }

    @GetMapping("/{id}/ship-back-quote")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<ShipBackQuoteResponse>> getShipBackQuote(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestParam(required = false) UUID addressId) {

        String email = authentication.getName();
        ShipBackQuoteResponse result = returnRequestService.getShipBackQuote(email, id, addressId);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/invalid-choice")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> chooseInvalidReturn(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody InvalidReturnChoiceRequest request) {

        String email = authentication.getName();
        ReturnRequestResponse result = returnRequestService.chooseInvalidReturn(email, id, request);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã ghi nhận lựa chọn"));
    }

    @PostMapping("/{id}/ship-back/pay")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PayShipBackFeeResponse>> payShipBackFee(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestBody PayShipBackFeeRequest request,
            HttpServletRequest httpRequest) {

        String email = authentication.getName();
        PayShipBackFeeResponse result = returnShipBackService.payFee(email, id, request, clientIp(httpRequest));
        String message = Boolean.TRUE.equals(result.getPaid()) ? "Đã thanh toán phí gửi lại hàng" : "";

        return ResponseEntity.ok(ApiResponse.success(result, message));
    }

    @GetMapping("/{id}/ship-back/payment-status")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<ShipBackPaymentStatusResponse>> getShipBackPaymentStatus(
            Authentication authentication,
            @PathVariable UUID id) {

        String email = authentication.getName();
        return ResponseEntity.ok(ApiResponse.success(returnShipBackService.syncPaymentStatus(email, id)));
    }

    @PostMapping("/{id}/ship-back/ship")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> shipInvalidReturn(
            @AuthenticationPrincipal Account moderator,
            @PathVariable UUID id) {

        ReturnRequestResponse result = returnShipBackService.ship(moderator, id);
        return ResponseEntity.ok(ApiResponse.success(result, "Đã tạo vận đơn gửi lại hàng trả"));
    }

    @GetMapping("/queue")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<PageResponse<ReturnRequestResponse>>> getQueue(
            @AuthenticationPrincipal Account moderator,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(
                returnRequestService.getQueue(moderator, status, page, size)));
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> getDetailForModerator(@PathVariable UUID id) {

        return ResponseEntity.ok(ApiResponse.success(
                returnRequestService.getDetailForModerator(id)));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> approve(@AuthenticationPrincipal Account moderator,
            @PathVariable UUID id) {

        ReturnRequestResponse result = returnRequestService.approve(moderator, id);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã duyệt và tạo vận đơn trả"));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> reject(
            @PathVariable UUID id,
            @RequestBody RejectReturnRequestRequest request) {

        ReturnRequestResponse result = returnRequestService.reject(id, request);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã từ chối yêu cầu trả hàng"));
    }

    @PostMapping("/{id}/inspect")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> startInspection(
            @PathVariable UUID id,
            @RequestBody StartInspectionRequest request) {

        ReturnRequestResponse result = returnRequestService.startInspection(id, request);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã ghi nhận kết quả kiểm tra"));
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> resolve(
            @AuthenticationPrincipal Account moderator,
            @PathVariable UUID id,
            @RequestBody ResolveReturnRequestRequest request) {

        ReturnRequestResponse result = returnRequestService.resolve(moderator, id, request);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã xử lý yêu cầu trả hàng"));
    }

    @PostMapping("/{id}/item-action")
    @PreAuthorize("hasRole('MODERATOR')")
    public ApiResponse<ReturnRequestResponse> applyItemAction(@AuthenticationPrincipal Account moderator,
            @PathVariable UUID id,
            @jakarta.validation.Valid @RequestBody com.rehome.rehome.dto.request.ApplyReturnItemActionRequest request) {
        return ApiResponse.success(returnRequestService.applyItemAction(moderator, id, request));
    }

    @PostMapping("/{id}/mark-lost")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> markLost(
            @PathVariable UUID id,
            @RequestBody MarkLostRequest request) {

        ReturnRequestResponse result = returnRequestService.markLost(id, request);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã đánh dấu thất lạc, hoàn tiền người mua"));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
