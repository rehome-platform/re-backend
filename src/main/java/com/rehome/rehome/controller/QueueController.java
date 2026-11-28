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

    @GetMapping
    public ApiResponse<List<QueueItemResponse>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean mine, @AuthenticationPrincipal Account moderator) {
        return ApiResponse.success(queueService.list(type, mine, moderator));
    }

    @PostMapping("/{id}/claim")
    public ApiResponse<QueueClaimResponse> claim(@PathVariable String id, @AuthenticationPrincipal Account moderator) {
        return ApiResponse.success(queueService.claim(id, moderator), "Đã nhận việc");
    }

    @PostMapping("/{id}/release")
    public ApiResponse<Void> release(@PathVariable String id, @AuthenticationPrincipal Account moderator) {
        queueService.release(id, moderator);
        return ApiResponse.success(null, 204, "Đã nhả việc");
    }
}
