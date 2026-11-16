package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ContentReviewStatus;
import com.rehome.rehome.service.ContentReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/content-reviews")
@PreAuthorize("hasRole('MODERATOR')")
@RequiredArgsConstructor
public class ContentReviewController {
    private final ContentReviewService reviews;

    public record Decision(@NotNull Boolean approved, @NotNull Long version, @Size(max = 1000) String reason) {}

    @GetMapping("/{kind}")
    public ApiResponse<PageResponse<ContentReviewService.Item>> pending(
            @PathVariable ContentReviewService.Kind kind, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(reviews.pending(kind, page, size), "OK");
    }

    @PostMapping("/{kind}/{id}/decision")
    public ApiResponse<ContentReviewStatus> resolve(@AuthenticationPrincipal Account moderator,
            @PathVariable ContentReviewService.Kind kind, @PathVariable UUID id,
            @Valid @RequestBody Decision decision) {
        return ApiResponse.success(reviews.resolve(moderator, kind, id, decision.version(), decision.approved(), decision.reason()), "OK");
    }
}
