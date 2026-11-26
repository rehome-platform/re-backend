package com.rehome.rehome.service;

import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ContentReviewStatus;
import com.rehome.rehome.entity.enums.ContentStatus;
import com.rehome.rehome.entity.marketplace.CommunityPost;
import com.rehome.rehome.entity.marketplace.PostComment;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.repository.CommunityPostRepository;
import com.rehome.rehome.repository.PostCommentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ContentReviewServiceTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void pendingCommentCanBeApprovedOrRemovedByAdmin(boolean approved) {
        var comments = mock(PostCommentRepository.class);
        UUID id = UUID.randomUUID();
        var comment = PostComment.builder().post(CommunityPost.builder().status(ContentStatus.VISIBLE).build())
                .content("stored original").status(ContentStatus.PENDING_REVIEW)
                .reviewStatus(ContentReviewStatus.PENDING_REVIEW).version(4).build();
        when(comments.findById(id)).thenReturn(Optional.of(comment));
        var admin = Account.builder().id(UUID.randomUUID()).role(com.rehome.rehome.entity.enums.Role.ADMIN).build();
        var audit = mock(AuditLogger.class);
        var service = new ContentReviewService(mock(CommunityPostRepository.class), comments, audit);

        ContentReviewStatus result = service.resolve(admin, ContentReviewService.Kind.COMMENT, id, 4,
                approved, approved ? null : " Vi phạm ");

        assertEquals(approved ? ContentReviewStatus.APPROVED : ContentReviewStatus.REJECTED, result);
        assertEquals(approved ? ContentStatus.VISIBLE : ContentStatus.REMOVED, comment.getStatus());
        assertEquals(approved ? null : "Vi phạm", comment.getReviewReason());
        assertEquals("stored original", comment.getContent());
        assertEquals(admin.getId(), comment.getReviewedBy());
        assertNotNull(comment.getReviewedAt());
        verify(audit).log(eq(admin), eq("CONTENT_REVIEW_" + result.name()), eq("COMMENT"), eq(id.toString()), any());
    }

    @ParameterizedTest
    @EnumSource(value = ContentStatus.class, names = {"PENDING_REVIEW", "VISIBLE"})
    void commentReviewStillRejectsStaleVersionsAndRemovedParent(ContentStatus status) {
        var comments = mock(PostCommentRepository.class);
        UUID id = UUID.randomUUID();
        var post = CommunityPost.builder().status(ContentStatus.VISIBLE).build();
        var comment = PostComment.builder().post(post).status(status)
                .reviewStatus(ContentReviewStatus.PENDING_REVIEW).version(4).build();
        when(comments.findById(id)).thenReturn(Optional.of(comment));
        var audit = mock(AuditLogger.class);
        var service = new ContentReviewService(mock(CommunityPostRepository.class), comments, audit);
        var actor = Account.builder().id(UUID.randomUUID()).build();

        assertThrows(ConflictException.class,
                () -> service.resolve(actor, ContentReviewService.Kind.COMMENT, id, 3, true, null));
        post.setStatus(ContentStatus.REMOVED);
        assertThrows(ConflictException.class,
                () -> service.resolve(actor, ContentReviewService.Kind.COMMENT, id, 4, true, null));
        assertEquals(status, comment.getStatus());
        verifyNoInteractions(audit);
    }

    @Test
    void approvesPendingPostAndRejectsStaleDecision() {
        var posts = mock(CommunityPostRepository.class);
        var id = UUID.randomUUID();
        var post = CommunityPost.builder().status(ContentStatus.PENDING_REVIEW)
                .reviewStatus(ContentReviewStatus.PENDING_REVIEW).version(2).build();
        when(posts.findById(id)).thenReturn(Optional.of(post));
        var moderator = Account.builder().id(UUID.randomUUID()).build();
        var service = new ContentReviewService(posts, mock(PostCommentRepository.class), mock(AuditLogger.class));

        assertThrows(ConflictException.class,
                () -> service.resolve(moderator, ContentReviewService.Kind.POST, id, 1, true, null));
        assertEquals(ContentReviewStatus.APPROVED,
                service.resolve(moderator, ContentReviewService.Kind.POST, id, 2, true, null));
        assertEquals(ContentStatus.VISIBLE, post.getStatus());
    }

    @Test
    void rejectingVisibleCommentPreservesStoredContentAndReason() {
        var comments = mock(PostCommentRepository.class);
        var id = UUID.randomUUID();
        var comment = PostComment.builder().post(CommunityPost.builder().build()).content("original")
                .reviewStatus(ContentReviewStatus.PENDING_REVIEW).build();
        when(comments.findById(id)).thenReturn(Optional.of(comment));
        var moderator = Account.builder().id(UUID.randomUUID()).build();
        var service = new ContentReviewService(mock(CommunityPostRepository.class), comments, mock(AuditLogger.class));

        assertEquals(ContentStatus.VISIBLE, comment.getStatus());
        service.resolve(moderator, ContentReviewService.Kind.COMMENT, id, 0, false, "Lý do");
        assertEquals(ContentStatus.REMOVED, comment.getStatus());
        assertEquals(ContentReviewStatus.REJECTED, comment.getReviewStatus());
        assertEquals("original", comment.getContent());
        assertEquals("Lý do", comment.getReviewReason());
        assertEquals(moderator.getId(), comment.getReviewedBy());
    }
}
