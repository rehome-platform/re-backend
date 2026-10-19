package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.ConsignmentFormResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.ConsignmentFormService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}