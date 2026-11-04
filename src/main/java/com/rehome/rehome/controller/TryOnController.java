package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateTryOnImageRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.TryOnImageResponse;
import com.rehome.rehome.dto.response.TryOnSuggestionResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
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

    // API 97 (F-072..F-079): sinh ảnh thử đồ. Gọi đồng bộ, có thể mất vài chục giây vì chờ AI.
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

    // F-080: xem bộ sưu tập ảnh thử đồ
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

    // Xem 1 ảnh trong bộ sưu tập (kèm imageUrl, thông tin từng món và warehouseId)
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

    // API 96 (F-075): gợi ý món còn thiếu. slot nhận String để slot sai/thiếu trả đúng message của spec
    @GetMapping("/suggestions")
    @PreAuthorize("hasRole('MEMBER')")
    public ResponseEntity<ApiResponse<List<TryOnSuggestionResponse>>> getSuggestions(
            @RequestParam(required = false) String slot,
            @RequestParam(required = false) String excludeProductId) {

        return ResponseEntity.ok(ApiResponse.success(tryOnService.getSuggestions(slot, excludeProductId)));
    }

    // F-081: xóa ảnh khỏi bộ sưu tập
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