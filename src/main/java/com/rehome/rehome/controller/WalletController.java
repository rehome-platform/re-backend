package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.WalletTransactionResponse;
import com.rehome.rehome.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/transactions")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PageResponse<WalletTransactionResponse>>> getTransactions(
            Authentication authentication,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String email = authentication.getName();
        PageResponse<WalletTransactionResponse> result =
                walletService.getTransactions(email, type, direction, page, size);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<WalletTransactionResponse>> getTransactionDetail(
            Authentication authentication,
            @PathVariable UUID id) {

        String email = authentication.getName();
        WalletTransactionResponse result = walletService.getTransactionDetail(email, id);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
