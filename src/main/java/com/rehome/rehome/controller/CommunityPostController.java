package com.rehome.rehome.controller;

import com.rehome.rehome.dto.request.CreateCommentRequest;
import com.rehome.rehome.dto.request.CreatePostRequest;
import com.rehome.rehome.dto.request.UpdatePostRequest;
import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.CommentResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.PostCreateResponse;
import com.rehome.rehome.dto.response.PostDetailResponse;
import com.rehome.rehome.dto.response.PostSummaryResponse;
import com.rehome.rehome.dto.response.PostUpdateResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.service.CommunityPostService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
public class CommunityPostController {

    private final CommunityPostService communityPostService;

    @GetMapping
    @Operation(summary = "Danh sách bài viết cộng đồng: theo tổ chức hoặc newsfeed (F-086)")
    public ResponseEntity<ApiResponse<PageResponse<PostSummaryResponse>>> listPosts(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal Account account) {
        PageResponse<PostSummaryResponse> result = communityPostService.listPosts(
                organizationId, account != null ? account.getId() : null, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Chi tiết 1 bài viết cộng đồng (F-086)")
    public ResponseEntity<ApiResponse<PostDetailResponse>> getDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal Account account) {
        PostDetailResponse result = communityPostService.getDetail(
                id, account != null ? account.getId() : null);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/like")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Thích 1 bài viết (F-087)")
    public ResponseEntity<ApiResponse<Void>> like(
            @PathVariable UUID id,
            @AuthenticationPrincipal Account account) {
        communityPostService.like(account.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null, 204));
    }

    @DeleteMapping("/{id}/like")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Bỏ thích 1 bài viết (F-087)")
    public ResponseEntity<ApiResponse<Void>> unlike(
            @PathVariable UUID id,
            @AuthenticationPrincipal Account account) {
        communityPostService.unlike(account.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null, 204));
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "Danh sách bình luận còn hiển thị của 1 bài viết (F-087)")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> listComments(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(communityPostService.listComments(id, page, size)));
    }

    @PostMapping
    @PreAuthorize("hasRole('ORGANIZATION')")
    @Operation(summary = "Tổ chức đăng bài viết mới (F-104)")
    public ResponseEntity<ApiResponse<PostCreateResponse>> createPost(
            @RequestBody CreatePostRequest request,
            @AuthenticationPrincipal Account account) {
        PostCreateResponse result = communityPostService.createPost(account.getId(), request);
        return ResponseEntity.status(201).body(ApiResponse.success(result, 201, "Đã đăng bài"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZATION')")
    @Operation(summary = "Sửa bài viết của chính tổ chức (F-105)")
    public ResponseEntity<ApiResponse<PostUpdateResponse>> updatePost(
            @PathVariable UUID id,
            @RequestBody UpdatePostRequest request,
            @AuthenticationPrincipal Account account) {
        PostUpdateResponse result = communityPostService.updatePost(account.getId(), id, request);
        return ResponseEntity.ok(ApiResponse.success(result, "Đã cập nhật bài viết"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZATION')")
    @Operation(summary = "Xóa bài viết của chính tổ chức (F-106)")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable UUID id,
            @AuthenticationPrincipal Account account) {
        communityPostService.deletePost(account.getId(), id);
        return ResponseEntity.ok(ApiResponse.success(null, 204, "Đã xóa bài viết"));
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("hasRole('MEMBER')")
    @Operation(summary = "Bình luận vào 1 bài viết (F-087)")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable UUID id,
            @RequestBody CreateCommentRequest request,
            @AuthenticationPrincipal Account account) {
        CommentResponse result = communityPostService.createComment(account.getId(), id, request);
        return ResponseEntity.status(201).body(ApiResponse.success(result, 201, "Đã đăng bình luận"));
    }
}
