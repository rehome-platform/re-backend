package com.rehome.rehome.service;

import com.rehome.rehome.component.AuditLogger;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ContentReviewStatus;
import com.rehome.rehome.entity.enums.ContentStatus;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.repository.CommunityPostRepository;
import com.rehome.rehome.repository.PostCommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContentReviewService {
    private final CommunityPostRepository posts;
    private final PostCommentRepository comments;
    private final AuditLogger auditLogger;

    public enum Kind { POST, COMMENT }

    public record Item(UUID id, UUID postId, UUID parentId, String content, Instant createdAt, long version) {}

    @Transactional(readOnly = true)
    public PageResponse<Item> pending(Kind kind, int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.clamp(size, 1, 100),
                Sort.by("createdAt", "id"));
        return PageResponse.from(switch (kind) {
            case POST -> posts.findByReviewStatus(ContentReviewStatus.PENDING_REVIEW, pageable)
                    .map(post -> new Item(post.getId(), post.getId(), null, post.getContent(), post.getCreatedAt(), post.getVersion()));
            case COMMENT -> comments.findByReviewStatus(ContentReviewStatus.PENDING_REVIEW, pageable)
                    .map(comment -> new Item(comment.getId(), comment.getPost().getId(),
                            comment.getParent() == null ? null : comment.getParent().getId(),
                            comment.getContent(), comment.getCreatedAt(), comment.getVersion()));
        });
    }

    @Transactional
    public ContentReviewStatus resolve(Account moderator, Kind kind, UUID id, long version, boolean approved, String reason) {
        if (!approved && (reason == null || reason.isBlank())) {
            throw new com.rehome.rehome.exception.BadRequestException("Vui lòng nhập lý do từ chối.");
        }

        ContentReviewStatus result = approved ? ContentReviewStatus.APPROVED : ContentReviewStatus.REJECTED;
        ContentStatus visibility = approved ? ContentStatus.VISIBLE : ContentStatus.REMOVED;
        Instant now = Instant.now();
        String decisionReason = approved ? null : reason.strip();
        switch (kind) {
            case POST -> {
                var post = posts.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết."));
                requirePending(post.getReviewStatus(), post.getVersion() == version && post.getStatus() == ContentStatus.PENDING_REVIEW);
                post.setStatus(visibility);
                post.setReviewStatus(result);
                post.setReviewReason(decisionReason);
                post.setReviewedBy(moderator.getId());
                post.setReviewedAt(now);
            }
            case COMMENT -> {
                var comment = comments.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bình luận."));
                requirePending(comment.getReviewStatus(), comment.getVersion() == version && comment.getStatus() == ContentStatus.VISIBLE
                        && comment.getPost().getStatus() != ContentStatus.REMOVED);
                comment.setStatus(visibility);
                comment.setReviewStatus(result);
                comment.setReviewReason(decisionReason);
                comment.setReviewedBy(moderator.getId());
                comment.setReviewedAt(now);
            }
        }
        auditLogger.log(moderator, "CONTENT_REVIEW_" + result.name(), kind.name(), id.toString(),
                JsonNodeFactory.instance.objectNode().put("reason", decisionReason));
        return result;
    }

    private static void requirePending(ContentReviewStatus status, boolean reviewable) {
        if (status != ContentReviewStatus.PENDING_REVIEW || !reviewable) {
            throw new ConflictException("Nội dung không còn chờ duyệt.", "CONTENT_NOT_PENDING_REVIEW");
        }
    }
}
