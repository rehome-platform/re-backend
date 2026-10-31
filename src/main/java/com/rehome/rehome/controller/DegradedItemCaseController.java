package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.DecideDegradedItemCaseRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.DegradedItemCaseResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.DegradedItemCaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/degraded-item-cases")
@RequiredArgsConstructor
public class DegradedItemCaseController {

    private final DegradedItemCaseService degradedItemCaseService;

    // API 95 (F-036)
    @PostMapping("/{id}/decide")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<DegradedItemCaseResponse>> decide(
            @AuthenticationPrincipal Account account,
            @PathVariable UUID id,
            @RequestBody DecideDegradedItemCaseRequest request) {

        DegradedItemCaseResponse result = degradedItemCaseService.decide(account, id, request);

        return ResponseEntity.ok(ApiResponse.success(result, "Đã ghi nhận quyết định"));
    }
}
