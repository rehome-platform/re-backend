package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.ModeratorConsignmentFormResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.ModeratorConsignmentFormService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// API dành riêng cho Moderator; API của Member (/consignment-forms) giữ nguyên
@RestController
@RequestMapping("/moderator/consignment-forms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MODERATOR')")
public class ModeratorConsignmentFormController {

    private final ModeratorConsignmentFormService moderatorConsignmentFormService;

    @GetMapping
    @Operation(summary = "Danh sách form ký gửi của kho tôi phụ trách (không gồm nháp). "
            + "stage: PENDING | APPROVED | INSPECTING | STOCKED_TODAY; bỏ trống = tất cả")
    public ApiResponse<PageResponse<ModeratorConsignmentFormResponse>> list(
            @RequestParam(required = false) String stage,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Account moderator) {
        return ApiResponse.success(moderatorConsignmentFormService.list(moderator, stage, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết form ký gửi thuộc kho tôi phụ trách")
    public ApiResponse<ModeratorConsignmentFormResponse> getDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal Account moderator) {
        return ApiResponse.success(moderatorConsignmentFormService.getDetail(moderator, id));
    }
}