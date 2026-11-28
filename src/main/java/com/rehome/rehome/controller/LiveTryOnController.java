package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CompleteLiveTryOnRequest;
import com.rehome.rehome.dto.request.StartLiveTryOnRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.LiveTryOnHistoryItemResponse;
import com.rehome.rehome.dto.response.LiveTryOnQuotaResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.LiveTryOnSessionResponse;
import com.rehome.rehome.dto.response.LiveTryOnSessionVideoResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.LiveTryOnService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/tryon/live")
@RequiredArgsConstructor
public class LiveTryOnController {

    private final LiveTryOnService liveTryOnService;

    @GetMapping("/quota")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<LiveTryOnQuotaResponse>> getQuota(
            @AuthenticationPrincipal Account account) {
        requireAccount(account);
        return ResponseEntity.ok(ApiResponse.success(liveTryOnService.getQuota(account)));
    }

    @PostMapping("/sessions")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<LiveTryOnSessionResponse>> startSession(
            @Valid @RequestBody StartLiveTryOnRequest request,
            @AuthenticationPrincipal Account account) {
        requireAccount(account);
        LiveTryOnSessionResponse created = liveTryOnService.startSession(account, request);
        return ResponseEntity.status(201).body(ApiResponse.success(created, 201, "Đã tạo phiên thử đồ AI"));
    }

    @PostMapping(value = "/sessions/{id}/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<LiveTryOnSessionVideoResponse>> uploadVideo(
            @PathVariable UUID id,
            @RequestPart("video") MultipartFile video,
            @AuthenticationPrincipal Account account) {
        requireAccount(account);
        LiveTryOnSessionVideoResponse uploaded = liveTryOnService.uploadVideo(account, id, video);
        return ResponseEntity.ok(ApiResponse.success(uploaded, "Đã lưu video thử đồ"));
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PageResponse<LiveTryOnHistoryItemResponse>>> getHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Account account) {
        requireAccount(account);
        return ResponseEntity.ok(ApiResponse.success(liveTryOnService.getHistory(account, page, size)));
    }

    @DeleteMapping("/sessions/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<Void>> deleteVideo(
            @PathVariable UUID id, @AuthenticationPrincipal Account account) {
        requireAccount(account);
        liveTryOnService.deleteVideo(account, id);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã xoá video thử đồ"));
    }

    @PatchMapping("/sessions/{id}/started")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<Void>> confirmFirstFrame(
            @PathVariable UUID id, @AuthenticationPrincipal Account account) {
        requireAccount(account);
        liveTryOnService.confirmFirstFrame(account, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PatchMapping("/sessions/{id}/complete")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<Void>> completeSession(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteLiveTryOnRequest request,
            @AuthenticationPrincipal Account account) {
        requireAccount(account);
        liveTryOnService.completeSession(account, id, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Đã ghi nhận"));
    }

    private void requireAccount(Account account) {
        if (account == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
    }
}
