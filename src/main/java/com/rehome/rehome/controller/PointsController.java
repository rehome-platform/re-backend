package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.RedeemPointsRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.PointsSummaryResponse;
import com.rehome.rehome.service.PointsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    @GetMapping
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PointsSummaryResponse>> getSummary(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(pointsService.getSummary(authentication.getName())));
    }

    @PostMapping("/redeem")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PointsSummaryResponse>> redeem(
            Authentication authentication,
            @Valid @RequestBody RedeemPointsRequest request) {
        PointsSummaryResponse result = pointsService.redeem(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success(result, "Đã đổi điểm"));
    }
}
