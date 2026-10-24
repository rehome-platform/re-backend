package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CompleteInspectionRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.CompleteInspectionResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.ConsignmentInspectionService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/consignment-forms")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MODERATOR')")
public class ConsignmentInspectionController {

    private final ConsignmentInspectionService consignmentInspectionService;

    // API 34
    @PostMapping("/{id}/inspection/complete")
    public ApiResponse<CompleteInspectionResponse> completeInspection(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteInspectionRequest request,
            @AuthenticationPrincipal Account moderator) {
        return ApiResponse.success(
                consignmentInspectionService.completeInspection(id, request, moderator), "Đã tạo phiếu ký gửi");
    }
}