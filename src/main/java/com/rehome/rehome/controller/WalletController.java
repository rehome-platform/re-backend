package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreatePayoutRequestRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.OrderRevenueResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.PayoutRequestResponse;
import com.rehome.rehome.dto.response.WalletBalanceResponse;
import com.rehome.rehome.dto.response.WalletTransactionResponse;
import com.rehome.rehome.service.WalletService;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<WalletBalanceResponse>> getBalance(Authentication authentication) {

        String email = authentication.getName();
        WalletBalanceResponse result = walletService.getBalance(email);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

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

    @GetMapping("/revenue")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PageResponse<OrderRevenueResponse>>> getRevenue(
            Authentication authentication,
            @RequestParam(required = false) String month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String email = authentication.getName();
        PageResponse<OrderRevenueResponse> result = walletService.getRevenue(email, month, page, size);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/payout-requests")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PayoutRequestResponse>> createPayoutRequest(
            Authentication authentication,
            @RequestBody CreatePayoutRequestRequest request) {

        String email = authentication.getName();
        PayoutRequestResponse result = walletService.createPayoutRequest(email, request);

        return ResponseEntity.status(201)
                .body(ApiResponse.success(result, 201, "Đã tạo yêu cầu rút tiền"));
    }

    @GetMapping("/payout-requests/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PayoutRequestResponse>> getPayoutRequestDetail(
            Authentication authentication,
            @PathVariable UUID id) {

        String email = authentication.getName();
        PayoutRequestResponse result = walletService.getPayoutRequestDetail(email, id);

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/payout-requests")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PageResponse<PayoutRequestResponse>>> getPayoutRequests(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String email = authentication.getName();
        PageResponse<PayoutRequestResponse> result = walletService.getPayoutRequests(email, page, size);

        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
