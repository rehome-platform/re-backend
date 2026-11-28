package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateTryOnImageRequest;
import com.rehome.rehome.dto.response.AiTryOnQuotaResponse;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.TryOnImageResponse;
import com.rehome.rehome.dto.response.TryOnSuggestionResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.service.MembershipService;
import com.rehome.rehome.service.TryOnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tryon")
@RequiredArgsConstructor
public class TryOnController {

    private final TryOnService tryOnService;
    private final MembershipService membershipService;
    private final com.rehome.rehome.service.MlOutfitSuggestionService mlOutfitSuggestionService;

    @GetMapping("/quota")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<AiTryOnQuotaResponse>> getQuota(
            @AuthenticationPrincipal Account account) {

        if (account == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ResponseEntity.ok(ApiResponse.success(membershipService.getAiTryOnQuota(account)));
    }

    @PostMapping("/images")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<TryOnImageResponse>> createImage(
            @Valid @RequestBody CreateTryOnImageRequest request,
            @AuthenticationPrincipal Account account) {

        if (account == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        TryOnImageResponse created = tryOnService.createImage(account, request);
        return ResponseEntity.status(201)
                .body(ApiResponse.success(created, 201, "Đã tạo ảnh thử đồ"));
    }

    @GetMapping("/images")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<PageResponse<TryOnImageResponse>>> getCollection(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Account account) {

        if (account == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ResponseEntity.ok(ApiResponse.success(tryOnService.getCollection(account, page, size)));
    }

    @GetMapping("/images/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<TryOnImageResponse>> getImage(
            @PathVariable UUID id,
            @AuthenticationPrincipal Account account) {

        if (account == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        return ResponseEntity.ok(ApiResponse.success(tryOnService.getImage(account, id)));
    }

    @GetMapping("/outfit-suggestions")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<List<TryOnSuggestionResponse>>> getOutfitSuggestions(
            @RequestParam String productId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {
        return ResponseEntity.ok(ApiResponse.success(
                tryOnService.getOutfitSuggestions(productId, latitude, longitude)));
    }

    @GetMapping("/outfit-suggestions/ml")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<com.rehome.rehome.dto.response.MlOutfitSuggestionsResponse>> getMlOutfitSuggestions(
            @AuthenticationPrincipal Account account,
            @RequestParam String productId,
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude) {
        if (account == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        return ResponseEntity.ok(ApiResponse.success(
                mlOutfitSuggestionService.suggest(account, productId, latitude, longitude)));
    }

    @GetMapping("/suggestions")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<List<TryOnSuggestionResponse>>> getSuggestions(
            @RequestParam(required = false) String slot,
            @RequestParam(required = false) String excludeProductId) {

        return ResponseEntity.ok(ApiResponse.success(tryOnService.getSuggestions(slot, excludeProductId)));
    }

    @DeleteMapping("/images/{id}")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable UUID id,
            @AuthenticationPrincipal Account account) {

        if (account == null) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        tryOnService.deleteImage(account, id);
        return ResponseEntity.ok(ApiResponse.<Void>success(null, 204, "Đã xóa ảnh thử đồ"));    }
}
