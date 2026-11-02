package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateCommentRequest;
import com.rehome.rehome.dto.request.CreatePostRequest;
import com.rehome.rehome.dto.request.UpdatePostRequest;
import com.rehome.rehome.dto.response.CommentResponse;
import com.rehome.rehome.dto.response.MyPostResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.PostCreateResponse;
import com.rehome.rehome.dto.response.PostDetailResponse;
import com.rehome.rehome.dto.response.PostSummaryResponse;
import com.rehome.rehome.dto.response.PostUpdateResponse;

import java.util.UUID;

public interface CommunityPostService {

    // F-086: organizationId == null -> newsfeed theo các tổ chức viewerId đang follow (bắt buộc đăng nhập).
    PageResponse<PostSummaryResponse> listPosts(UUID organizationId, UUID viewerId, int page, int size);

    // F-086: chi tiết 1 bài viết, kèm likeCount/commentCount/likedByMe.
    PostDetailResponse getDetail(UUID postId, UUID viewerId);

    // F-087: thích / bỏ thích 1 bài viết, Member hoặc ORGANIZATION (ít nhất trên bài của mình).
    // Cả 2 đều idempotent (gọi lại không báo lỗi).
    void like(UUID accountId, UUID postId);
    void unlike(UUID accountId, UUID postId);

    // F-087: toàn bộ bình luận của 1 bài, kể cả bị ẩn (REMOVED, content null) - mobile tự
    // xếp nhóm theo parentId.
    PageResponse<CommentResponse> listComments(UUID postId, int page, int size);

    // F-087: Member hoặc ORGANIZATION (chỉ trên bài của chính mình) bình luận/trả lời 1 bài viết.
    CommentResponse createComment(UUID authorId, UUID postId, CreateCommentRequest request);

    // F-104: tổ chức đăng bài viết mới.
    PostCreateResponse createPost(UUID organizationAccountId, CreatePostRequest request);

    // F-105: sửa nội dung bài viết của chính tổ chức.
    PostUpdateResponse updatePost(UUID organizationAccountId, UUID postId, UpdatePostRequest request);

    // F-106: xóa bài viết của chính tổ chức (ẩn khỏi sàn, giữ lại dữ liệu tương tác).
    void deletePost(UUID organizationAccountId, UUID postId);

    // F-107: bài viết của chính tổ chức kèm số like và số bình luận.
    PageResponse<MyPostResponse> listMyPosts(UUID organizationAccountId, int page, int size);
}
