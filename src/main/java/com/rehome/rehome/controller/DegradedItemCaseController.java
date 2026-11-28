package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.DecideDegradedItemCaseRequest;
import com.rehome.rehome.dto.request.OpenDegradedItemCaseRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.DegradedItemCaseDetailResponse;
import com.rehome.rehome.dto.response.DegradedItemCaseResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.DegradedItemCaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/degraded-item-cases")
@RequiredArgsConstructor
public class DegradedItemCaseController {

    private final DegradedItemCaseService degradedItemCaseService;

    @PostMapping
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<DegradedItemCaseDetailResponse>> open(
            @AuthenticationPrincipal Account moderator,
            @Valid @RequestBody OpenDegradedItemCaseRequest request) {

        DegradedItemCaseDetailResponse result = degradedItemCaseService.open(moderator, request);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã mở ca xử lý hàng xuống cấp"));
    }

    @PostMapping("/{id}/decide")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<DegradedItemCaseResponse>> decide(
            @AuthenticationPrincipal Account account,
            @PathVariable UUID id,
            @RequestBody DecideDegradedItemCaseRequest request) {

        DegradedItemCaseResponse result = degradedItemCaseService.decide(account, id, request);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã ghi nhận quyết định"));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('MEMBER','MODERATOR')")
    public ResponseEntity<ApiResponse<DegradedItemCaseDetailResponse>> getMine(
            @AuthenticationPrincipal Account account,
            @PathVariable UUID id) {

        return ResponseEntity.ok(ApiResponse.success(
                degradedItemCaseService.getMine(account, id)));
    }

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<List<DegradedItemCaseDetailResponse>>> listMine(
            @AuthenticationPrincipal Account account,
            @RequestParam(required = false) String status) {

        return ResponseEntity.ok(ApiResponse.success(
                degradedItemCaseService.listMine(account, status)));
    }

    @GetMapping("/warehouse")
    @PreAuthorize("hasRole('MODERATOR')")
    public ResponseEntity<ApiResponse<List<DegradedItemCaseDetailResponse>>> listByWarehouse(
            @AuthenticationPrincipal Account account,
            @RequestParam(required = false) String status) {

        return ResponseEntity.ok(ApiResponse.success(
                degradedItemCaseService.listByWarehouse(account, status)));
    }
}
