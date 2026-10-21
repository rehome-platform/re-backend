package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.request.RejectConsignmentFormRequest;
import com.rehome.rehome.dto.request.ReturnOnsiteRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.service.ConsignmentFormService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/consignment-forms")
@RequiredArgsConstructor
public class ConsignmentFormController {

    private final ConsignmentFormService consignmentFormService;

    // API 15 - POST /consignment-forms (Role: Member)
    @PostMapping
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<ConsignmentFormResponse>> create(
            @Valid @RequestBody ConsignmentFormRequest request,
            @AuthenticationPrincipal Account account) {

        ConsignmentFormResponse result = consignmentFormService.create(request, account);

        ApiResponse<ConsignmentFormResponse> body = ApiResponse.<ConsignmentFormResponse>builder()
                .result(result)
                .statusCode(HttpStatus.CREATED.value())
                .message("Tạo yêu cầu ký gửi thành công")
                .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @GetMapping
    @Operation(summary = "Danh sách form ký gửi của tôi")
    public ResponseEntity<ApiResponse<PageResponse<ConsignmentFormListItemResponse>>> getMyForms(
            @RequestParam(required = false) ConsignmentFormStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Account account) {
        return ResponseEntity.ok(ApiResponse.success(
                consignmentFormService.getMyForms(account, status, page, size), 200));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết form ký gửi của tôi")
    public ResponseEntity<ApiResponse<ConsignmentFormDetailResponse>> getMyForm(
            @PathVariable UUID id,
            @AuthenticationPrincipal Account account) {
        return ResponseEntity.ok(ApiResponse.success(
                consignmentFormService.getMyForm(account, id), 200));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ConsignmentFormResponse>> approve(@PathVariable UUID id) {
        ConsignmentFormResponse result = consignmentFormService.approve(id);
        return ResponseEntity.ok(ApiResponse.success(result, 200, "Đã duyệt form"));
    }

    @PostMapping("/{id}/inspection/start")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<Void>> startInspection(@PathVariable UUID id) {
        consignmentFormService.startInspection(id);
        // Cách 1 (khuyên dùng): HTTP 200, body giữ statusCode 204 theo spec
        return ResponseEntity.ok(ApiResponse.success(null, 204, "Đã bắt đầu kiểm định"));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ConsignmentFormStatusResponse>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectConsignmentFormRequest request) {
        ConsignmentFormStatusResponse result = consignmentFormService.reject(id, request);
        return ResponseEntity.ok(ApiResponse.success(result, 200, "Đã từ chối form"));
    }

    @PostMapping("/{id}/return-onsite")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ConsignmentFormStatusResponse>> returnOnsite(
            @PathVariable UUID id,
            @Valid @RequestBody ReturnOnsiteRequest request) {
        ConsignmentFormStatusResponse result = consignmentFormService.returnOnsite(id, request);
        return ResponseEntity.ok(ApiResponse.success(result, 200, "Đã trả hàng tại quầy"));
    }

}