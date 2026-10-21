package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CompleteInspectionRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.CompleteInspectionResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptConfirmResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.service.ConsignmentFormService;
import com.rehome.rehome.service.ConsignmentReceiptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/consignment-receipts")
@RequiredArgsConstructor
public class ConsignmentReceiptController {
    private final ConsignmentReceiptService consignmentReceiptService;
    @GetMapping("/{id}")
    public ApiResponse<ConsignmentReceiptResponse> getReceipt(
            @AuthenticationPrincipal Account account,
            @PathVariable UUID id) {
        return ApiResponse.success(consignmentReceiptService.getMyReceipt(account, id));
    }

    @PostMapping("/{id}/confirm")
    public ApiResponse<ConsignmentReceiptConfirmResponse> confirm(
            @AuthenticationPrincipal Account account,
            @PathVariable UUID id) {
        return ApiResponse.success(
                consignmentReceiptService.confirmReceipt(account, id),
                "Xác nhận ký gửi thành công, sản phẩm đã lên sàn");
    }

    @PostMapping("/{id}/inspection/complete")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<CompleteInspectionResponse>> completeInspection(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteInspectionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                consignmentReceiptService.completeInspection(id, request), 200, "Đã tạo phiếu ký gửi"));
    }


}
