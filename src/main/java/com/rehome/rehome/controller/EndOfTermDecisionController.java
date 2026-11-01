package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.DecideEndOfTermRequest;
import com.rehome.rehome.dto.request.ReConsignTermsRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.DecideEndOfTermResponse;
import com.rehome.rehome.dto.response.PendingReConsignResponse;
import com.rehome.rehome.dto.response.ReConsignTermsResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.EndOfTermDecisionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/end-of-term-decisions")
@RequiredArgsConstructor
public class EndOfTermDecisionController {

    private final EndOfTermDecisionService endOfTermDecisionService;

    @PostMapping("/{id}/decide")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<DecideEndOfTermResponse>> decide(
            @AuthenticationPrincipal Account account,
            @PathVariable UUID id,
            @Valid @RequestBody DecideEndOfTermRequest request) {

        DecideEndOfTermResponse result = endOfTermDecisionService.decide(account, id, request);
        return ResponseEntity.ok(ApiResponse.success(result, 200, "Đã ghi nhận quyết định"));
    }

    // Moderator: danh sách yêu cầu ký gửi lại đang chờ chốt giá, chỉ của kho mình
    @GetMapping("/pending-pricing")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<List<PendingReConsignResponse>>> listPendingPricing(
            @AuthenticationPrincipal Account account) {
        return ResponseEntity.ok(ApiResponse.success(endOfTermDecisionService.listPendingPricing(account)));
    }

    // Moderator chốt giá và điều khoản; Member xác nhận biên nhận mới sau đó
    @PostMapping("/{id}/re-consign-terms")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<ReConsignTermsResponse>> setReConsignTerms(
            @AuthenticationPrincipal Account account,
            @PathVariable UUID id,
            @Valid @RequestBody ReConsignTermsRequest request) {
        ReConsignTermsResponse result = endOfTermDecisionService.setReConsignTerms(account, id, request);
        return ResponseEntity.ok(ApiResponse.success(result, 200, "Đã chốt giá và điều khoản, chờ Member xác nhận"));
    }
}
