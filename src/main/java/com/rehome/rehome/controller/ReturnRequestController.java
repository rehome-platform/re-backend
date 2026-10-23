package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateReturnRequestRequest;
import com.rehome.rehome.dto.request.InspectReturnRequest;
import com.rehome.rehome.dto.request.InvalidReturnChoiceRequest;
import com.rehome.rehome.dto.request.RejectReturnRequestRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ReturnRequestResponse;
import com.rehome.rehome.service.ReturnRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// Flow trả hàng F-067 -> F-071 (Member) và F-131/F-132 (Moderator)
@RestController
@RequestMapping("/return-requests")
@RequiredArgsConstructor
public class ReturnRequestController {

    private final ReturnRequestService returnRequestService;

    // F-067
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

    // F-068 - Member xác nhận đã bàn giao hàng cho GHN
    @PostMapping("/{id}/handover")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> confirmHandover(
            Authentication authentication,
            @PathVariable UUID id) {

        String email = authentication.getName();
        ReturnRequestResponse result = returnRequestService.confirmHandover(email, id);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã ghi nhận bàn giao cho GHN"));
    }

    // F-070 - Member chọn cách nhận lại hàng trả không hợp lệ
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

    // Hàng đợi xử lý của Moderator
    @GetMapping("/queue")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<PageResponse<ReturnRequestResponse>>> getQueue(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.ok(ApiResponse.success(
                returnRequestService.getQueue(status, page, size)));
    }

    @GetMapping("/{id}/detail")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> getDetailForModerator(@PathVariable UUID id) {

        return ResponseEntity.ok(ApiResponse.success(
                returnRequestService.getDetailForModerator(id)));
    }

    // F-131 - duyệt và tạo vận đơn GHN chiều trả
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> approve(@PathVariable UUID id) {

        ReturnRequestResponse result = returnRequestService.approve(id);

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

    // F-132 - nhận hàng về kho, bắt đầu kiểm
    @PostMapping("/{id}/inspection/start")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> startInspection(@PathVariable UUID id) {

        ReturnRequestResponse result = returnRequestService.startInspection(id);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã bắt đầu kiểm hàng trả"));
    }

    // F-132 - chốt kết luận hợp lệ / không hợp lệ
    @PostMapping("/{id}/inspection")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> inspect(
            @PathVariable UUID id,
            @RequestBody InspectReturnRequest request) {

        ReturnRequestResponse result = returnRequestService.inspect(id, request);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã chốt kết luận hàng trả"));
    }
}
