package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateCommentRequest;
import com.rehome.rehome.dto.request.CreatePostRequest;
import com.rehome.rehome.dto.request.UpdatePostRequest;
import com.rehome.rehome.dto.response.CommentResponse;
import com.rehome.rehome.dto.response.MyPostResponse;
import com.rehome.rehome.dto.response.PostCreateResponse;
import com.rehome.rehome.dto.response.PostUpdateResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.PostDetailResponse;
import com.rehome.rehome.dto.response.PostSummaryResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.ContentStatus;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.entity.marketplace.CommunityPost;
import com.rehome.rehome.entity.marketplace.CommunityPostPhoto;
import com.rehome.rehome.entity.marketplace.PostComment;
import com.rehome.rehome.entity.marketplace.PostLike;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.exception.UnauthorizedException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CommunityPostPhotoRepository;
import com.rehome.rehome.repository.CommunityPostRepository;
import com.rehome.rehome.repository.FollowRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.repository.PostCommentRepository;
import com.rehome.rehome.repository.PostLikeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommunityPostServiceImplTest {

    @Mock private CommunityPostRepository communityPostRepository;
    @Mock private FollowRepository followRepository;
    @Mock private PostLikeRepository postLikeRepository;
    @Mock private PostCommentRepository postCommentRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private OrganizationProfileRepository organizationProfileRepository;
    @Mock private CommunityPostPhotoRepository communityPostPhotoRepository;

    @InjectMocks private CommunityPostServiceImpl service;

    private CommunityPost post(UUID id, UUID organizationId, ContentStatus status) {
        OrganizationProfile organization = OrganizationProfile.builder().accountId(organizationId).build();
        return CommunityPost.builder()
                .id(id)
                .organization(organization)
                .content("Đã phát 200 áo ấm cho các em...")
                .status(status)
                .build();
    }

    // =====================================================================
    // listPosts
    // =====================================================================

    @Test
    void listPosts_byOrganizationId_mapsToSummary() {
        UUID orgId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);

        when(communityPostRepository.findByOrganization_AccountIdAndStatus(
                eq(orgId), eq(ContentStatus.VISIBLE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));

        PageResponse<PostSummaryResponse> result = service.listPosts(orgId, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        PostSummaryResponse summary = result.getContent().get(0);
        assertThat(summary.getId()).isEqualTo(postId.toString());
        assertThat(summary.getOrganizationId()).isEqualTo(orgId.toString());
        assertThat(summary.getContent()).isEqualTo("Đã phát 200 áo ấm cho các em...");
        assertThat(summary.getStatus()).isEqualTo("VISIBLE");
        verifyNoInteractions(followRepository);
    }

    @Test
    void listPosts_noOrganizationId_guest_throwsUnauthorized() {
        assertThatThrownBy(() -> service.listPosts(null, null, 0, 20))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Cần đăng nhập để xem newsfeed.");

        verifyNoInteractions(communityPostRepository, followRepository);
    }

    @Test
    void listPosts_newsfeed_memberNotFollowingAnyone_returnsEmptyPage() {
        UUID memberId = UUID.randomUUID();
        when(followRepository.findFollowedOrganizationIds(memberId)).thenReturn(List.of());

        PageResponse<PostSummaryResponse> result = service.listPosts(null, memberId, 0, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        verify(communityPostRepository, never())
                .findByOrganization_AccountIdInAndStatus(anyList(), any(), any());
    }

    @Test
    void listPosts_newsfeed_memberFollowing_queriesByFollowedOrgIds() {
        UUID memberId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(followRepository.findFollowedOrganizationIds(memberId)).thenReturn(List.of(orgId));
        when(communityPostRepository.findByOrganization_AccountIdInAndStatus(
                eq(List.of(orgId)), eq(ContentStatus.VISIBLE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post(postId, orgId, ContentStatus.VISIBLE))));

        PageResponse<PostSummaryResponse> result = service.listPosts(null, memberId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(postId.toString());
    }

    @Test
    void listPosts_sizeClampedToMaxPageSize() {
        UUID orgId = UUID.randomUUID();
        when(communityPostRepository.findByOrganization_AccountIdAndStatus(any(), any(), any()))
                .thenReturn(Page.empty());

        service.listPosts(orgId, null, 0, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(communityPostRepository).findByOrganization_AccountIdAndStatus(any(), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
    }

    // =====================================================================
    // getDetail
    // =====================================================================

    @Test
    void getDetail_visible_guest_returnsLikedByMeFalse() {
        UUID postId = UUID.randomUUID();
        CommunityPost entity = post(postId, UUID.randomUUID(), ContentStatus.VISIBLE);
        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(postLikeRepository.countByPost_Id(postId)).thenReturn(42L);
        when(postCommentRepository.countByPost_IdAndStatus(postId, ContentStatus.VISIBLE)).thenReturn(5L);

        PostDetailResponse res = service.getDetail(postId, null);

        assertThat(res.getId()).isEqualTo(postId.toString());
        assertThat(res.getLikeCount()).isEqualTo(42L);
        assertThat(res.getCommentCount()).isEqualTo(5L);
        assertThat(res.isLikedByMe()).isFalse();
        verifyNoInteractions(followRepository);
    }

    @Test
    void getDetail_visible_memberLiked_returnsLikedByMeTrue() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        CommunityPost entity = post(postId, UUID.randomUUID(), ContentStatus.VISIBLE);
        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(postLikeRepository.existsByPost_IdAndMember_Id(postId, memberId)).thenReturn(true);

        PostDetailResponse res = service.getDetail(postId, memberId);

        assertThat(res.isLikedByMe()).isTrue();
    }

    @Test
    void getDetail_notFound_throws() {
        UUID postId = UUID.randomUUID();
        when(communityPostRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(postId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bài viết.");

        verifyNoInteractions(postLikeRepository, postCommentRepository);
    }

    @Test
    void getDetail_removed_throwsNotFound() {
        UUID postId = UUID.randomUUID();
        CommunityPost entity = post(postId, UUID.randomUUID(), ContentStatus.REMOVED);
        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.getDetail(postId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bài viết.");

        verifyNoInteractions(postLikeRepository, postCommentRepository);
    }

    // =====================================================================
    // like / unlike  (F-087)
    // =====================================================================

    @Test
    void like_notYetLiked_savesNewLike() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        CommunityPost entity = post(postId, UUID.randomUUID(), ContentStatus.VISIBLE);
        Account member = Account.builder().id(memberId).build();

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(postLikeRepository.existsByPost_IdAndMember_Id(postId, memberId)).thenReturn(false);
        when(accountRepository.findById(memberId)).thenReturn(Optional.of(member));

        service.like(memberId, postId);

        ArgumentCaptor<PostLike> captor = ArgumentCaptor.forClass(PostLike.class);
        verify(postLikeRepository).save(captor.capture());
        assertThat(captor.getValue().getMember()).isSameAs(member);
        assertThat(captor.getValue().getPost()).isSameAs(entity);
    }

    @Test
    void like_alreadyLiked_isIdempotent_doesNotSaveAgain() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        CommunityPost entity = post(postId, UUID.randomUUID(), ContentStatus.VISIBLE);

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(postLikeRepository.existsByPost_IdAndMember_Id(postId, memberId)).thenReturn(true);

        service.like(memberId, postId);

        verify(postLikeRepository, never()).save(any());
        verifyNoInteractions(accountRepository);
    }

    @Test
    void like_postNotFound_throws() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(communityPostRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.like(memberId, postId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bài viết.");

        verifyNoInteractions(postLikeRepository, accountRepository);
    }

    @Test
    void like_postRemoved_throwsNotFound() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        CommunityPost entity = post(postId, UUID.randomUUID(), ContentStatus.REMOVED);
        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.like(memberId, postId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bài viết.");

        verifyNoInteractions(postLikeRepository, accountRepository);
    }

    @Test
    void unlike_existingLike_deletesIt() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        PostLike like = PostLike.builder().id(UUID.randomUUID()).build();

        when(communityPostRepository.existsById(postId)).thenReturn(true);
        when(postLikeRepository.findByPost_IdAndMember_Id(postId, memberId)).thenReturn(Optional.of(like));

        service.unlike(memberId, postId);

        verify(postLikeRepository).delete(like);
    }

    @Test
    void unlike_notLiked_isIdempotent_doesNotThrow() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        when(communityPostRepository.existsById(postId)).thenReturn(true);
        when(postLikeRepository.findByPost_IdAndMember_Id(postId, memberId)).thenReturn(Optional.empty());

        service.unlike(memberId, postId);

        verify(postLikeRepository, never()).delete(any());
    }

    @Test
    void unlike_postNotFound_throws() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(communityPostRepository.existsById(postId)).thenReturn(false);

        assertThatThrownBy(() -> service.unlike(memberId, postId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bài viết.");

        verifyNoInteractions(postLikeRepository);
    }

    // =====================================================================
    // listComments  (F-087)
    // =====================================================================

    @Test
    void listComments_found_mapsToCommentResponse() {
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        CommunityPost entity = post(postId, UUID.randomUUID(), ContentStatus.VISIBLE);
        PostComment comment = PostComment.builder()
                .id(commentId)
                .content("Ủng hộ chiến dịch này!")
                .status(ContentStatus.VISIBLE)
                .build();

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(postCommentRepository.findByPost_IdAndStatus(eq(postId), eq(ContentStatus.VISIBLE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment)));

        PageResponse<CommentResponse> result = service.listComments(postId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        CommentResponse res = result.getContent().get(0);
        assertThat(res.getId()).isEqualTo(commentId.toString());
        assertThat(res.getContent()).isEqualTo("Ủng hộ chiến dịch này!");
        assertThat(res.getStatus()).isEqualTo("VISIBLE");
    }

    @Test
    void listComments_postNotFound_throws() {
        UUID postId = UUID.randomUUID();
        when(communityPostRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listComments(postId, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bài viết.");

        verifyNoInteractions(postCommentRepository);
    }

    @Test
    void listComments_postRemoved_throwsNotFound() {
        UUID postId = UUID.randomUUID();
        CommunityPost entity = post(postId, UUID.randomUUID(), ContentStatus.REMOVED);
        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.listComments(postId, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bài viết.");

        verifyNoInteractions(postCommentRepository);
    }

    // =====================================================================
    // createComment  (F-087)
    // =====================================================================

    @Test
    void createComment_success_savesAndReturnsResponse() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        CommunityPost entity = post(postId, UUID.randomUUID(), ContentStatus.VISIBLE);
        Account member = Account.builder().id(memberId).build();

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(accountRepository.findById(memberId)).thenReturn(Optional.of(member));
        // GenerationType.UUID gán id ngay trong persist() (không cần flush); mock lại hành vi đó ở đây
        when(postCommentRepository.save(any())).thenAnswer(invocation -> {
            PostComment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        CommentResponse res = service.createComment(memberId, postId, new CreateCommentRequest("Ủng hộ chiến dịch này!"));

        assertThat(res.getContent()).isEqualTo("Ủng hộ chiến dịch này!");
        assertThat(res.getStatus()).isEqualTo("VISIBLE");
        assertThat(res.getId()).isNotNull();

        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        verify(postCommentRepository).save(captor.capture());
        assertThat(captor.getValue().getMember()).isSameAs(member);
        assertThat(captor.getValue().getPost()).isSameAs(entity);
        assertThat(captor.getValue().getContent()).isEqualTo("Ủng hộ chiến dịch này!");
    }

    @Test
    void createComment_blankContent_throwsBadRequest() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createComment(memberId, postId, new CreateCommentRequest("   ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("content là bắt buộc.");

        verifyNoInteractions(communityPostRepository, accountRepository, postCommentRepository);
    }

    @Test
    void createComment_postNotFound_throws() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(communityPostRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createComment(memberId, postId, new CreateCommentRequest("Ủng hộ!")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bài viết.");

        verifyNoInteractions(accountRepository, postCommentRepository);
    }

    @Test
    void createComment_postRemoved_throwsNotFound() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        CommunityPost entity = post(postId, UUID.randomUUID(), ContentStatus.REMOVED);
        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.createComment(memberId, postId, new CreateCommentRequest("Ủng hộ!")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bài viết.");

        verifyNoInteractions(accountRepository, postCommentRepository);
    }

    // =====================================================================
    // createPost  (F-104)
    // =====================================================================

    private void stubVerifiedOrganization(UUID orgId) {
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(
                OrganizationProfile.builder().accountId(orgId)
                        .verificationStatus(VerificationStatus.VERIFIED).build()));
    }

    private void stubPostSaveAssignsId() {
        when(communityPostRepository.save(any())).thenAnswer(invocation -> {
            CommunityPost saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void createPost_success_savesPostAndPhotos() {
        UUID orgId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        stubPostSaveAssignsId();

        PostCreateResponse res = service.createPost(orgId, new CreatePostRequest(
                "Đã phát 200 áo ấm cho các em vùng cao, cảm ơn mọi người!",
                List.of("https://cdn.rehome.vn/uploads/post1.jpg")));

        assertThat(res.getId()).isNotNull();
        assertThat(res.getStatus()).isEqualTo("VISIBLE");

        ArgumentCaptor<CommunityPost> postCaptor = ArgumentCaptor.forClass(CommunityPost.class);
        verify(communityPostRepository).save(postCaptor.capture());
        assertThat(postCaptor.getValue().getOrganization().getAccountId()).isEqualTo(orgId);
        assertThat(postCaptor.getValue().getContent())
                .isEqualTo("Đã phát 200 áo ấm cho các em vùng cao, cảm ơn mọi người!");
        assertThat(postCaptor.getValue().getStatus()).isEqualTo(ContentStatus.VISIBLE);

        ArgumentCaptor<List<CommunityPostPhoto>> photoCaptor = ArgumentCaptor.forClass(List.class);
        verify(communityPostPhotoRepository).saveAll(photoCaptor.capture());
        assertThat(photoCaptor.getValue()).hasSize(1);
        assertThat(photoCaptor.getValue().get(0).getImageUrl())
                .isEqualTo("https://cdn.rehome.vn/uploads/post1.jpg");
    }

    @Test
    void createPost_withoutPhotos_savesNoPhotoRows() {
        UUID orgId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        stubPostSaveAssignsId();

        service.createPost(orgId, new CreatePostRequest("Cảm ơn mọi người!", null));

        verifyNoInteractions(communityPostPhotoRepository);
    }

    @Test
    void createPost_blankContent_throwsBadRequest() {
        assertThatThrownBy(() -> service.createPost(UUID.randomUUID(),
                new CreatePostRequest("  ", List.of())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("content là bắt buộc.");

        verifyNoInteractions(organizationProfileRepository, communityPostRepository,
                communityPostPhotoRepository);
    }

    @Test
    void createPost_organizationProfileNotFound_throws() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPost(orgId, new CreatePostRequest("Xin chào", null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND));

        verify(communityPostRepository, never()).save(any());
    }

    @Test
    void createPost_organizationNotVerified_throwsForbidden() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(
                OrganizationProfile.builder().accountId(orgId)
                        .verificationStatus(VerificationStatus.PENDING).build()));

        assertThatThrownBy(() -> service.createPost(orgId, new CreatePostRequest("Xin chào", null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Hồ sơ tổ chức chưa được xác minh.");

        verify(communityPostRepository, never()).save(any());
    }

    // =====================================================================
    // updatePost / deletePost  (F-105, F-106)
    // =====================================================================

    @Test
    void updatePost_success_changesContent() {
        UUID orgId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);
        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));

        PostUpdateResponse res = service.updatePost(orgId, postId,
                new UpdatePostRequest("Đã phát 220 áo ấm cho các em vùng cao, cảm ơn mọi người!"));

        assertThat(entity.getContent())
                .isEqualTo("Đã phát 220 áo ấm cho các em vùng cao, cảm ơn mọi người!");
        assertThat(res.getId()).isEqualTo(postId.toString());
        assertThat(res.getContent())
                .isEqualTo("Đã phát 220 áo ấm cho các em vùng cao, cảm ơn mọi người!");
        verify(communityPostRepository).save(entity);
    }

    @Test
    void updatePost_blankContent_throwsBadRequest() {
        assertThatThrownBy(() -> service.updatePost(UUID.randomUUID(), UUID.randomUUID(),
                new UpdatePostRequest("   ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("content là bắt buộc.");

        verifyNoInteractions(communityPostRepository);
    }

    @Test
    void updatePost_notFound_throws() {
        UUID postId = UUID.randomUUID();
        when(communityPostRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePost(UUID.randomUUID(), postId,
                new UpdatePostRequest("nội dung mới")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bài viết.");
    }

    @Test
    void updatePost_otherOrganization_throwsForbidden() {
        UUID postId = UUID.randomUUID();
        when(communityPostRepository.findById(postId))
                .thenReturn(Optional.of(post(postId, UUID.randomUUID(), ContentStatus.VISIBLE)));

        assertThatThrownBy(() -> service.updatePost(UUID.randomUUID(), postId,
                new UpdatePostRequest("nội dung mới")))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không có quyền sửa bài viết này.");

        verify(communityPostRepository, never()).save(any());
    }

    @Test
    void deletePost_success_marksRemovedWithoutHardDelete() {
        UUID orgId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);
        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));

        service.deletePost(orgId, postId);

        // Giữ lại dữ liệu like/bình luận, chỉ ẩn bài khỏi sàn
        assertThat(entity.getStatus()).isEqualTo(ContentStatus.REMOVED);
        verify(communityPostRepository).save(entity);
        verify(communityPostRepository, never()).delete(any());
    }

    @Test
    void deletePost_alreadyRemoved_isIdempotent() {
        UUID orgId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(communityPostRepository.findById(postId))
                .thenReturn(Optional.of(post(postId, orgId, ContentStatus.REMOVED)));

        service.deletePost(orgId, postId);

        verify(communityPostRepository, never()).save(any());
    }

    @Test
    void deletePost_notFound_throws() {
        UUID postId = UUID.randomUUID();
        when(communityPostRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletePost(UUID.randomUUID(), postId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bài viết.");
    }

    @Test
    void deletePost_otherOrganization_throwsForbidden() {
        UUID postId = UUID.randomUUID();
        when(communityPostRepository.findById(postId))
                .thenReturn(Optional.of(post(postId, UUID.randomUUID(), ContentStatus.VISIBLE)));

        assertThatThrownBy(() -> service.deletePost(UUID.randomUUID(), postId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không có quyền xóa bài viết này.");

        verify(communityPostRepository, never()).save(any());
    }

    // =====================================================================
    // listMyPosts  (F-107)
    // =====================================================================

    @Test
    void listMyPosts_includesLikeAndCommentCounts() {
        UUID orgId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(communityPostRepository.findByOrganization_AccountId(eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post(postId, orgId, ContentStatus.VISIBLE))));
        when(postLikeRepository.countByPostIds(List.of(postId)))
                .thenReturn(List.<Object[]>of(new Object[]{postId, 15L}));
        when(postCommentRepository.countByPostIdsAndStatus(List.of(postId), ContentStatus.VISIBLE))
                .thenReturn(List.<Object[]>of(new Object[]{postId, 3L}));

        PageResponse<MyPostResponse> result = service.listMyPosts(orgId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        MyPostResponse res = result.getContent().get(0);
        assertThat(res.getId()).isEqualTo(postId.toString());
        assertThat(res.getContent()).isEqualTo("Đã phát 200 áo ấm cho các em...");
        assertThat(res.getLikeCount()).isEqualTo(15L);
        assertThat(res.getCommentCount()).isEqualTo(3L);
    }

    @Test
    void listMyPosts_noInteractions_countsAreZero() {
        UUID orgId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(communityPostRepository.findByOrganization_AccountId(eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post(postId, orgId, ContentStatus.VISIBLE))));

        PageResponse<MyPostResponse> result = service.listMyPosts(orgId, 0, 20);

        assertThat(result.getContent().get(0).getLikeCount()).isZero();
        assertThat(result.getContent().get(0).getCommentCount()).isZero();
    }

    @Test
    void listMyPosts_empty_skipsCountQueries() {
        UUID orgId = UUID.randomUUID();
        when(communityPostRepository.findByOrganization_AccountId(eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<MyPostResponse> result = service.listMyPosts(orgId, 0, 20);

        assertThat(result.getContent()).isEmpty();
        verify(postLikeRepository, never()).countByPostIds(any());
        verify(postCommentRepository, never()).countByPostIdsAndStatus(any(), any());
    }

    @Test
    void listMyPosts_sortedNewestFirstAndSizeClamped() {
        UUID orgId = UUID.randomUUID();
        when(communityPostRepository.findByOrganization_AccountId(eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listMyPosts(orgId, 0, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(communityPostRepository).findByOrganization_AccountId(eq(orgId), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }
}
