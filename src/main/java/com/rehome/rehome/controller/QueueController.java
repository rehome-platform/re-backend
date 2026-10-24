package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.QueueClaimResponse;
import com.rehome.rehome.dto.response.QueueItemResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.QueueService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/queues")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MODERATOR')")
public class QueueController {

    private final QueueService queueService;

    // API 28
    @GetMapping
    public ApiResponse<List<QueueItemResponse>> list(
            @RequestParam(required = false) String type, @AuthenticationPrincipal Account moderator) {
        return ApiResponse.success(queueService.list(type, moderator));
    }

    // API 29
    @PostMapping("/{id}/claim")
    public ApiResponse<QueueClaimResponse> claim(@PathVariable String id, @AuthenticationPrincipal Account moderator) {
        return ApiResponse.success(queueService.claim(id, moderator), "Đã nhận việc");
    }

    // API 30: HTTP 200 (204 không được có body), statusCode 204 nằm trong body theo spec
    @PostMapping("/{id}/release")
    public ApiResponse<Void> release(@PathVariable String id, @AuthenticationPrincipal Account moderator) {
        queueService.release(id, moderator);
        return ApiResponse.success(null, 204, "Đã nhả việc");
    }
}