package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.ConsignmentReceiptResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.ConsignmentReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
