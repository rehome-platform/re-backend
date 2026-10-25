package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CounterHandoverRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.CounterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/counter")
@RequiredArgsConstructor
public class CounterController {

    private final CounterService counterService;

    // API 26
    @PostMapping("/members/{accountId}/confirm-identity")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<Void>> confirmIdentity(
            @AuthenticationPrincipal Account moderator,
            @PathVariable UUID accountId) {

        counterService.confirmIdentity(moderator, accountId);
        return ResponseEntity.ok(ApiResponse.success(null, 200, "Đã xác nhận danh tính"));
    }

    // API 72
    @PostMapping("/handover")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<Void>> handover(
            @AuthenticationPrincipal Account moderator,
            @Valid @RequestBody CounterHandoverRequest request) {

        counterService.handover(moderator, request);
        return ResponseEntity.ok(ApiResponse.success(null, 200, "Đã bàn giao"));
    }
}
