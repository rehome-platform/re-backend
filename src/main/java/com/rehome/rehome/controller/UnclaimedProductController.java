package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.ConfirmTransferRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.ProductStatusResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.UnclaimedProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MODERATOR')")
public class UnclaimedProductController {

    private final UnclaimedProductService unclaimedProductService;

    // API 36
    @PostMapping("/{id}/unclaimed/confirm-transfer")
    public ApiResponse<ProductStatusResponse> confirmTransfer(
            @PathVariable String id,
            @RequestBody ConfirmTransferRequest request,
            @AuthenticationPrincipal Account moderator) {
        return ApiResponse.success(
                unclaimedProductService.confirmTransfer(id, request, moderator), "Đã xác nhận chuyển giao");
    }
}