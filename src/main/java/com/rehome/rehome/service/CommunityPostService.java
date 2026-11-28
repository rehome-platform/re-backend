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

    PageResponse<PostSummaryResponse> listPosts(UUID organizationId, String scope, UUID viewerId,
                                                int page, int size);

    PostDetailResponse getDetail(UUID postId, UUID viewerId);

    void like(UUID accountId, UUID postId);
    void unlike(UUID accountId, UUID postId);

    PageResponse<CommentResponse> listComments(UUID postId, UUID viewerId, int page, int size);

    CommentResponse createComment(UUID authorId, UUID postId, CreateCommentRequest request);

    PostCreateResponse createPost(UUID organizationAccountId, CreatePostRequest request);

    PostUpdateResponse updatePost(UUID organizationAccountId, UUID postId, UpdatePostRequest request);

    void deletePost(UUID organizationAccountId, UUID postId);

    PageResponse<MyPostResponse> listMyPosts(UUID organizationAccountId, int page, int size);
}
