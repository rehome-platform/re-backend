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
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.CampaignStatus;
import com.rehome.rehome.entity.enums.ContentStatus;
import com.rehome.rehome.entity.enums.NotificationTargetType;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.entity.enums.ViolationReportStatus;
import com.rehome.rehome.entity.enums.ViolationTargetType;
import com.rehome.rehome.entity.marketplace.Campaign;
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
import com.rehome.rehome.repository.CampaignRepository;
import com.rehome.rehome.repository.CommunityPostPhotoRepository;
import com.rehome.rehome.repository.CommunityPostRepository;
import com.rehome.rehome.repository.FollowRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.repository.PostCommentRepository;
import com.rehome.rehome.repository.PostLikeRepository;
import com.rehome.rehome.repository.ViolationReportRepository;
import com.rehome.rehome.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
    @Mock private CampaignRepository campaignRepository;
    @Mock private MemberProfileRepository memberProfileRepository;
    @Mock private ViolationReportRepository violationReportRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private CommunityPostServiceImpl service;

    private CommunityPost post(UUID id, UUID organizationId, ContentStatus status) {
        OrganizationProfile organization = OrganizationProfile.builder()
                .accountId(organizationId)
                .account(Account.builder().id(organizationId).role(Role.ORGANIZATION).build())
                .build();
        return CommunityPost.builder()
                .id(id)
                .organization(organization)
                .content("Đã phát 200 áo ấm cho các em...")
                .status(status)
                .build();
    }

    private Campaign openCampaignOf(UUID campaignId, UUID orgId) {
        return Campaign.builder()
                .id(campaignId)
                .title("Mùa đông ấm 2026")
                .organization(OrganizationProfile.builder().accountId(orgId).build())
                .status(CampaignStatus.OPEN)
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
        when(postCommentRepository.countByPost_Id(postId)).thenReturn(5L);

        PostDetailResponse res = service.getDetail(postId, null);

        assertThat(res.getId()).isEqualTo(postId.toString());
        assertThat(res.getLikeCount()).isEqualTo(42L);
        assertThat(res.getCommentCount()).isEqualTo(5L);
        assertThat(res.isLikedByMe()).isFalse();
        verifyNoInteractions(followRepository);
    }

    @Test
    void getDetail_includesPhotosCampaignAndOrganization() {
        UUID postId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        CommunityPost entity = CommunityPost.builder()
                .id(postId)
                .organization(OrganizationProfile.builder().accountId(orgId)
                        .displayName("Hội Chữ Thập Đỏ").logoUrl("https://cdn/logo.jpg").build())
                .campaign(openCampaignOf(campaignId, orgId))
                .content("Đã phát 200 áo ấm cho các em...")
                .status(ContentStatus.VISIBLE)
                .createdAt(Instant.parse("2026-10-30T09:00:00Z"))
                .build();
        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(communityPostPhotoRepository.findByPost_Id(postId)).thenReturn(List.of(
                CommunityPostPhoto.builder().imageUrl("https://cdn/photo1.jpg").build()));

        PostDetailResponse res = service.getDetail(postId, null);

        assertThat(res.getPhotoUrls()).containsExactly("https://cdn/photo1.jpg");
        assertThat(res.getCreatedAt()).isEqualTo(Instant.parse("2026-10-30T09:00:00Z"));
        assertThat(res.getCampaignId()).isEqualTo(campaignId.toString());
        assertThat(res.getCampaignTitle()).isEqualTo("Mùa đông ấm 2026");
        assertThat(res.getOrganization().getId()).isEqualTo(orgId.toString());
        assertThat(res.getOrganization().getDisplayName()).isEqualTo("Hội Chữ Thập Đỏ");
        assertThat(res.getOrganization().getLogoUrl()).isEqualTo("https://cdn/logo.jpg");
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
    // like / unlike  (F-087) — Member hoặc ORGANIZATION, không khác nhau ở tầng service
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

    private Account memberAccount(UUID id) {
        return Account.builder().id(id).role(Role.MEMBER).build();
    }

    @Test
    void listComments_found_mapsRootCommentToResponse() {
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        CommunityPost entity = post(postId, UUID.randomUUID(), ContentStatus.VISIBLE);
        PostComment comment = PostComment.builder()
                .id(commentId)
                .author(memberAccount(memberId))
                .content("Ủng hộ chiến dịch này!")
                .status(ContentStatus.VISIBLE)
                .createdAt(Instant.parse("2026-10-30T09:00:00Z"))
                .build();

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(postCommentRepository.findByPost_Id(eq(postId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(comment)));
        when(memberProfileRepository.findAllById(Set.of(memberId))).thenReturn(List.of(
                MemberProfile.builder()
                        .accountId(memberId).fullName("Nguyễn Văn A").avatarUrl("https://cdn/a.jpg").build()));

        PageResponse<CommentResponse> result = service.listComments(postId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        CommentResponse res = result.getContent().get(0);
        assertThat(res.getId()).isEqualTo(commentId.toString());
        assertThat(res.getParentId()).isNull();
        assertThat(res.getContent()).isEqualTo("Ủng hộ chiến dịch này!");
        assertThat(res.getStatus()).isEqualTo("VISIBLE");
        assertThat(res.getAuthor().getId()).isEqualTo(memberId.toString());
        assertThat(res.getAuthor().getType()).isEqualTo("MEMBER");
        assertThat(res.getAuthor().getName()).isEqualTo("Nguyễn Văn A");
        assertThat(res.getAuthor().getAvatarUrl()).isEqualTo("https://cdn/a.jpg");
        assertThat(res.getCreatedAt()).isEqualTo(Instant.parse("2026-10-30T09:00:00Z"));
    }

    @Test
    void listComments_reply_includesParentId() {
        UUID postId = UUID.randomUUID();
        UUID rootId = UUID.randomUUID();
        UUID replyId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);
        PostComment root = PostComment.builder().id(rootId).author(memberAccount(UUID.randomUUID())).build();
        PostComment reply = PostComment.builder()
                .id(replyId)
                .author(Account.builder().id(orgId).role(Role.ORGANIZATION).build())
                .parent(root)
                .content("Cảm ơn bạn!")
                .status(ContentStatus.VISIBLE)
                .createdAt(Instant.now())
                .build();

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(postCommentRepository.findByPost_Id(eq(postId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(reply)));
        when(organizationProfileRepository.findAllById(Set.of(orgId))).thenReturn(List.of(
                OrganizationProfile.builder().accountId(orgId)
                        .displayName("Hội Chữ Thập Đỏ").logoUrl("https://cdn/logo.jpg").build()));

        CommentResponse res = service.listComments(postId, 0, 20).getContent().get(0);

        assertThat(res.getParentId()).isEqualTo(rootId.toString());
        assertThat(res.getAuthor().getType()).isEqualTo("ORGANIZATION");
        assertThat(res.getAuthor().getName()).isEqualTo("Hội Chữ Thập Đỏ");
        assertThat(res.getAuthor().getAvatarUrl()).isEqualTo("https://cdn/logo.jpg");
    }

    @Test
    void listComments_removedComment_stripsContentButKeepsStatusAndAuthor() {
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        CommunityPost entity = post(postId, UUID.randomUUID(), ContentStatus.VISIBLE);
        PostComment removed = PostComment.builder()
                .id(commentId)
                .author(memberAccount(memberId))
                .content("Nội dung vi phạm")
                .status(ContentStatus.REMOVED)
                .createdAt(Instant.now())
                .build();

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(postCommentRepository.findByPost_Id(eq(postId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(removed)));

        PageResponse<CommentResponse> result = service.listComments(postId, 0, 20);

        CommentResponse res = result.getContent().get(0);
        assertThat(res.getStatus()).isEqualTo("REMOVED");
        assertThat(res.getContent()).isNull();
        assertThat(res.getAuthor().getId()).isEqualTo(memberId.toString());
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
    void createComment_memberRootComment_notifiesOrganization() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);
        Account member = memberAccount(memberId);

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(accountRepository.findById(memberId)).thenReturn(Optional.of(member));
        when(memberProfileRepository.findByAccountId(memberId)).thenReturn(Optional.of(
                MemberProfile.builder()
                        .accountId(memberId).fullName("Nguyễn Văn A").avatarUrl("https://cdn/a.jpg").build()));
        when(postCommentRepository.save(any())).thenAnswer(invocation -> {
            PostComment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        CommentResponse res = service.createComment(memberId, postId,
                new CreateCommentRequest("Ủng hộ chiến dịch này!", null));

        assertThat(res.getContent()).isEqualTo("Ủng hộ chiến dịch này!");
        assertThat(res.getStatus()).isEqualTo("VISIBLE");
        assertThat(res.getParentId()).isNull();
        assertThat(res.getId()).isNotNull();
        assertThat(res.getAuthor().getId()).isEqualTo(memberId.toString());
        assertThat(res.getAuthor().getType()).isEqualTo("MEMBER");
        assertThat(res.getAuthor().getName()).isEqualTo("Nguyễn Văn A");

        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        verify(postCommentRepository).save(captor.capture());
        assertThat(captor.getValue().getAuthor()).isSameAs(member);
        assertThat(captor.getValue().getPost()).isSameAs(entity);
        assertThat(captor.getValue().getParent()).isNull();
        assertThat(captor.getValue().getContent()).isEqualTo("Ủng hộ chiến dịch này!");

        verify(notificationService).notify(
                eq(entity.getOrganization().getAccount()), eq(NotificationType.POST_COMMENTED),
                any(), any(), eq(NotificationTargetType.POST), eq(postId), isNull());
    }

    @Test
    void createComment_organizationOnOwnPost_success() {
        UUID postId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);
        Account orgAccount = Account.builder().id(orgId).role(Role.ORGANIZATION).build();

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(accountRepository.findById(orgId)).thenReturn(Optional.of(orgAccount));
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(
                OrganizationProfile.builder().accountId(orgId)
                        .displayName("Hội Chữ Thập Đỏ").logoUrl("https://cdn/logo.jpg").build()));
        when(postCommentRepository.save(any())).thenAnswer(invocation -> {
            PostComment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        CommentResponse res = service.createComment(orgId, postId, new CreateCommentRequest("Cảm ơn mọi người!", null));

        assertThat(res.getAuthor().getType()).isEqualTo("ORGANIZATION");
        assertThat(res.getAuthor().getName()).isEqualTo("Hội Chữ Thập Đỏ");
        // Bình luận gốc của chính tổ chức sở hữu bài -> không có ai để báo
        verifyNoInteractions(notificationService);
    }

    @Test
    void createComment_organizationOnOtherOrganizationPost_throwsForbidden() {
        UUID postId = UUID.randomUUID();
        UUID ownerOrgId = UUID.randomUUID();
        UUID callerOrgId = UUID.randomUUID();
        CommunityPost entity = post(postId, ownerOrgId, ContentStatus.VISIBLE);
        Account callerOrgAccount = Account.builder().id(callerOrgId).role(Role.ORGANIZATION).build();

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(accountRepository.findById(callerOrgId)).thenReturn(Optional.of(callerOrgAccount));

        assertThatThrownBy(() -> service.createComment(callerOrgId, postId,
                new CreateCommentRequest("Xin chào", null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không có quyền bình luận trên bài viết này.");

        verify(postCommentRepository, never()).save(any());
        verifyNoInteractions(notificationService);
    }

    @Test
    void createComment_organizationRepliesToMember_notifiesMemberAndFlattensParent() {
        UUID postId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID rootCommentId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);
        Account orgAccount = Account.builder().id(orgId).role(Role.ORGANIZATION).build();
        Account memberAccount = memberAccount(memberId);
        PostComment rootComment = PostComment.builder()
                .id(rootCommentId).post(entity).author(memberAccount).build();

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(accountRepository.findById(orgId)).thenReturn(Optional.of(orgAccount));
        when(postCommentRepository.findById(rootCommentId)).thenReturn(Optional.of(rootComment));
        when(postCommentRepository.save(any())).thenAnswer(invocation -> {
            PostComment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        CommentResponse res = service.createComment(orgId, postId,
                new CreateCommentRequest("Cảm ơn bạn đã quyên góp!", rootCommentId.toString()));

        assertThat(res.getParentId()).isEqualTo(rootCommentId.toString());
        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        verify(postCommentRepository).save(captor.capture());
        assertThat(captor.getValue().getParent()).isSameAs(rootComment);

        verify(notificationService).notify(
                eq(memberAccount), eq(NotificationType.COMMENT_REPLIED),
                any(), any(), eq(NotificationTargetType.POST), eq(postId), isNull());
    }

    @Test
    void createComment_replyToAReply_flattensToOriginalRoot() {
        UUID postId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID rootCommentId = UUID.randomUUID();
        UUID replyCommentId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);
        Account orgAccount = Account.builder().id(orgId).role(Role.ORGANIZATION).build();
        PostComment rootComment = PostComment.builder()
                .id(rootCommentId).post(entity).author(memberAccount(memberId)).build();
        PostComment existingReply = PostComment.builder()
                .id(replyCommentId).post(entity).author(orgAccount).parent(rootComment).build();

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(accountRepository.findById(memberId)).thenReturn(Optional.of(memberAccount(memberId)));
        // Member trả lời vào chính reply của tổ chức -> BE phải gắn vào rootComment, không phải existingReply
        when(postCommentRepository.findById(replyCommentId)).thenReturn(Optional.of(existingReply));
        when(postCommentRepository.save(any())).thenAnswer(invocation -> {
            PostComment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        CommentResponse res = service.createComment(memberId, postId,
                new CreateCommentRequest("Dạ không có gì!", replyCommentId.toString()));

        assertThat(res.getParentId()).isEqualTo(rootCommentId.toString());
        ArgumentCaptor<PostComment> captor = ArgumentCaptor.forClass(PostComment.class);
        verify(postCommentRepository).save(captor.capture());
        assertThat(captor.getValue().getParent()).isSameAs(rootComment);
    }

    @Test
    void createComment_organizationRepliesToOwnComment_doesNotNotifySelf() {
        UUID postId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID rootCommentId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);
        Account orgAccount = Account.builder().id(orgId).role(Role.ORGANIZATION).build();
        PostComment rootComment = PostComment.builder()
                .id(rootCommentId).post(entity).author(orgAccount).build();

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(accountRepository.findById(orgId)).thenReturn(Optional.of(orgAccount));
        when(postCommentRepository.findById(rootCommentId)).thenReturn(Optional.of(rootComment));
        when(postCommentRepository.save(any())).thenAnswer(invocation -> {
            PostComment saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.createComment(orgId, postId, new CreateCommentRequest("Bổ sung thêm", rootCommentId.toString()));

        verifyNoInteractions(notificationService);
    }

    @Test
    void createComment_parentNotFound_throwsNotFound() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID missingParentId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(accountRepository.findById(memberId)).thenReturn(Optional.of(memberAccount(memberId)));
        when(postCommentRepository.findById(missingParentId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createComment(memberId, postId,
                new CreateCommentRequest("Xin chào", missingParentId.toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bình luận.");

        verify(postCommentRepository, never()).save(any());
    }

    @Test
    void createComment_parentBelongsToOtherPost_treatedAsNotFound() {
        UUID postId = UUID.randomUUID();
        UUID otherPostId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);
        CommunityPost otherPost = post(otherPostId, UUID.randomUUID(), ContentStatus.VISIBLE);
        PostComment parentOnOtherPost = PostComment.builder().id(parentId).post(otherPost).build();

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(accountRepository.findById(memberId)).thenReturn(Optional.of(memberAccount(memberId)));
        when(postCommentRepository.findById(parentId)).thenReturn(Optional.of(parentOnOtherPost));

        assertThatThrownBy(() -> service.createComment(memberId, postId,
                new CreateCommentRequest("Xin chào", parentId.toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bình luận.");

        verify(postCommentRepository, never()).save(any());
    }

    @Test
    void createComment_malformedParentId_throwsNotFound() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        CommunityPost entity = post(postId, UUID.randomUUID(), ContentStatus.VISIBLE);

        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(accountRepository.findById(memberId)).thenReturn(Optional.of(memberAccount(memberId)));

        assertThatThrownBy(() -> service.createComment(memberId, postId,
                new CreateCommentRequest("Xin chào", "not-a-uuid")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bình luận.");

        verify(postCommentRepository, never()).save(any());
    }

    @Test
    void createComment_blankContent_throwsBadRequest() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createComment(memberId, postId, new CreateCommentRequest("   ", null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("content là bắt buộc.");

        verifyNoInteractions(communityPostRepository, accountRepository, postCommentRepository);
    }

    @Test
    void createComment_contentTooLong_throwsAppException() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String tooLong = "a".repeat(501);

        assertThatThrownBy(() -> service.createComment(memberId, postId, new CreateCommentRequest(tooLong, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.COMMENT_CONTENT_TOO_LONG));

        verifyNoInteractions(communityPostRepository, accountRepository, postCommentRepository);
    }

    @Test
    void createComment_postNotFound_throws() {
        UUID postId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(communityPostRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createComment(memberId, postId, new CreateCommentRequest("Ủng hộ!", null)))
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

        assertThatThrownBy(() -> service.createComment(memberId, postId, new CreateCommentRequest("Ủng hộ!", null)))
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
                List.of("https://cdn.rehome.vn/uploads/post1.jpg"), null));

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

        service.createPost(orgId, new CreatePostRequest("Cảm ơn mọi người!", null, null));

        verifyNoInteractions(communityPostPhotoRepository);
    }

    @Test
    void createPost_blankContent_throwsBadRequest() {
        assertThatThrownBy(() -> service.createPost(UUID.randomUUID(),
                new CreatePostRequest("  ", List.of(), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("content là bắt buộc.");

        verifyNoInteractions(organizationProfileRepository, communityPostRepository,
                communityPostPhotoRepository);
    }

    @Test
    void createPost_organizationProfileNotFound_throws() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createPost(orgId, new CreatePostRequest("Xin chào", null, null)))
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

        assertThatThrownBy(() -> service.createPost(orgId, new CreatePostRequest("Xin chào", null, null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Hồ sơ tổ chức chưa được xác minh.");

        verify(communityPostRepository, never()).save(any());
    }

    @Test
    void createPost_contentTooLong_throwsAppException() {
        UUID orgId = UUID.randomUUID();
        String tooLong = "a".repeat(2001);

        assertThatThrownBy(() -> service.createPost(orgId, new CreatePostRequest(tooLong, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.POST_CONTENT_TOO_LONG));

        verifyNoInteractions(organizationProfileRepository, communityPostRepository);
    }

    @Test
    void createPost_tooManyPhotos_throwsAppException() {
        UUID orgId = UUID.randomUUID();
        List<String> sixPhotos = List.of("1", "2", "3", "4", "5", "6");

        assertThatThrownBy(() -> service.createPost(orgId, new CreatePostRequest("Xin chào", sixPhotos, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.POST_TOO_MANY_PHOTOS));

        verifyNoInteractions(organizationProfileRepository, communityPostRepository);
    }

    @Test
    void createPost_withOpenOwnCampaign_attachesCampaign() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        stubPostSaveAssignsId();
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(openCampaignOf(campaignId, orgId)));

        PostCreateResponse res = service.createPost(orgId,
                new CreatePostRequest("Xin chào", null, campaignId.toString()));

        assertThat(res.getId()).isNotNull();
        ArgumentCaptor<CommunityPost> captor = ArgumentCaptor.forClass(CommunityPost.class);
        verify(communityPostRepository).save(captor.capture());
        assertThat(captor.getValue().getCampaign().getId()).isEqualTo(campaignId);
    }

    @Test
    void createPost_campaignClosed_treatedAsNotFound() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        Campaign closed = openCampaignOf(campaignId, orgId);
        closed.setStatus(CampaignStatus.CLOSED);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.createPost(orgId,
                new CreatePostRequest("Xin chào", null, campaignId.toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");

        verify(communityPostRepository, never()).save(any());
    }

    @Test
    void createPost_campaignOfOtherOrganization_treatedAsNotFound() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        when(campaignRepository.findById(campaignId))
                .thenReturn(Optional.of(openCampaignOf(campaignId, UUID.randomUUID())));

        assertThatThrownBy(() -> service.createPost(orgId,
                new CreatePostRequest("Xin chào", null, campaignId.toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");

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
                new UpdatePostRequest("Đã phát 220 áo ấm cho các em vùng cao, cảm ơn mọi người!", null, null));

        assertThat(entity.getContent())
                .isEqualTo("Đã phát 220 áo ấm cho các em vùng cao, cảm ơn mọi người!");
        assertThat(res.getId()).isEqualTo(postId.toString());
        assertThat(res.getContent())
                .isEqualTo("Đã phát 220 áo ấm cho các em vùng cao, cảm ơn mọi người!");
        verify(communityPostRepository).save(entity);
    }

    @Test
    void updatePost_replacesPhotosEntirely() {
        UUID orgId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);
        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));

        PostUpdateResponse res = service.updatePost(orgId, postId,
                new UpdatePostRequest("Nội dung mới", List.of("https://cdn/new1.jpg", "https://cdn/new2.jpg"), null));

        verify(communityPostPhotoRepository).deleteByPost_Id(postId);
        ArgumentCaptor<List<CommunityPostPhoto>> photoCaptor = ArgumentCaptor.forClass(List.class);
        verify(communityPostPhotoRepository).saveAll(photoCaptor.capture());
        assertThat(photoCaptor.getValue()).extracting(CommunityPostPhoto::getImageUrl)
                .containsExactly("https://cdn/new1.jpg", "https://cdn/new2.jpg");
        assertThat(res.getPhotoUrls()).containsExactly("https://cdn/new1.jpg", "https://cdn/new2.jpg");
    }

    @Test
    void updatePost_withOpenOwnCampaign_attachesCampaign() {
        UUID orgId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);
        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(openCampaignOf(campaignId, orgId)));

        PostUpdateResponse res = service.updatePost(orgId, postId,
                new UpdatePostRequest("Nội dung mới", null, campaignId.toString()));

        assertThat(entity.getCampaign().getId()).isEqualTo(campaignId);
        assertThat(res.getCampaignId()).isEqualTo(campaignId.toString());
        assertThat(res.getCampaignTitle()).isEqualTo("Mùa đông ấm 2026");
    }

    @Test
    void updatePost_blankCampaignId_detachesCampaign() {
        UUID orgId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);
        entity.setCampaign(openCampaignOf(UUID.randomUUID(), orgId));
        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(entity));

        PostUpdateResponse res = service.updatePost(orgId, postId,
                new UpdatePostRequest("Nội dung mới", null, null));

        assertThat(entity.getCampaign()).isNull();
        assertThat(res.getCampaignId()).isNull();
    }

    @Test
    void updatePost_contentTooLong_throwsAppException() {
        String tooLong = "a".repeat(2001);

        assertThatThrownBy(() -> service.updatePost(UUID.randomUUID(), UUID.randomUUID(),
                new UpdatePostRequest(tooLong, null, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.POST_CONTENT_TOO_LONG));

        verifyNoInteractions(communityPostRepository);
    }

    @Test
    void updatePost_tooManyPhotos_throwsAppException() {
        List<String> sixPhotos = List.of("1", "2", "3", "4", "5", "6");

        assertThatThrownBy(() -> service.updatePost(UUID.randomUUID(), UUID.randomUUID(),
                new UpdatePostRequest("nội dung mới", sixPhotos, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.POST_TOO_MANY_PHOTOS));

        verifyNoInteractions(communityPostRepository);
    }

    @Test
    void updatePost_blankContent_throwsBadRequest() {
        assertThatThrownBy(() -> service.updatePost(UUID.randomUUID(), UUID.randomUUID(),
                new UpdatePostRequest("   ", null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("content là bắt buộc.");

        verifyNoInteractions(communityPostRepository);
    }

    @Test
    void updatePost_notFound_throws() {
        UUID postId = UUID.randomUUID();
        when(communityPostRepository.findById(postId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updatePost(UUID.randomUUID(), postId,
                new UpdatePostRequest("nội dung mới", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy bài viết.");
    }

    @Test
    void updatePost_otherOrganization_throwsForbidden() {
        UUID postId = UUID.randomUUID();
        when(communityPostRepository.findById(postId))
                .thenReturn(Optional.of(post(postId, UUID.randomUUID(), ContentStatus.VISIBLE)));

        assertThatThrownBy(() -> service.updatePost(UUID.randomUUID(), postId,
                new UpdatePostRequest("nội dung mới", null, null)))
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
        when(postCommentRepository.countByPostIds(List.of(postId)))
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
    void listMyPosts_includesPhotosCampaignReportCountAndLikedByMe() {
        UUID orgId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        CommunityPost entity = post(postId, orgId, ContentStatus.VISIBLE);
        entity.setCampaign(openCampaignOf(campaignId, orgId));
        entity.setCreatedAt(Instant.parse("2026-10-30T09:00:00Z"));
        when(communityPostRepository.findByOrganization_AccountId(eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity)));
        when(communityPostPhotoRepository.findUrlsByPostIds(List.of(postId)))
                .thenReturn(List.<Object[]>of(new Object[]{postId, "https://cdn/photo1.jpg"}));
        when(violationReportRepository.countByTargetTypeAndTargetIdInAndStatus(
                ViolationTargetType.POST, List.of(postId), ViolationReportStatus.PENDING))
                .thenReturn(List.<Object[]>of(new Object[]{postId, 2L}));
        when(postLikeRepository.findLikedPostIds(List.of(postId), orgId)).thenReturn(List.of(postId));

        MyPostResponse res = service.listMyPosts(orgId, 0, 20).getContent().get(0);

        assertThat(res.getPhotoUrls()).containsExactly("https://cdn/photo1.jpg");
        assertThat(res.getCreatedAt()).isEqualTo(Instant.parse("2026-10-30T09:00:00Z"));
        assertThat(res.getCampaignId()).isEqualTo(campaignId.toString());
        assertThat(res.getCampaignTitle()).isEqualTo("Mùa đông ấm 2026");
        assertThat(res.getReportCount()).isEqualTo(2L);
        assertThat(res.isLikedByMe()).isTrue();
    }

    @Test
    void listMyPosts_notLikedByMe_returnsFalse() {
        UUID orgId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(communityPostRepository.findByOrganization_AccountId(eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(post(postId, orgId, ContentStatus.VISIBLE))));
        when(postLikeRepository.findLikedPostIds(List.of(postId), orgId)).thenReturn(List.of());

        MyPostResponse res = service.listMyPosts(orgId, 0, 20).getContent().get(0);

        assertThat(res.isLikedByMe()).isFalse();
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
        verify(postCommentRepository, never()).countByPostIds(any());
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
