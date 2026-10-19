package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.ConsignmentFormListItemResponse;
import com.rehome.rehome.dto.response.ConsignmentFormResponse;
import com.rehome.rehome.dto.response.PageResponse;
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


}