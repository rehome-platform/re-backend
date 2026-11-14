package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateCommentRequest;
import com.rehome.rehome.dto.request.CreatePostRequest;
import com.rehome.rehome.dto.request.UpdatePostRequest;
import com.rehome.rehome.dto.response.CommentAuthorResponse;
import com.rehome.rehome.dto.response.CommentResponse;
import com.rehome.rehome.dto.response.MyPostResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.PostCreateResponse;
import com.rehome.rehome.dto.response.PostDetailResponse;
import com.rehome.rehome.dto.response.PostOrganizationSummaryResponse;
import com.rehome.rehome.dto.response.PostSummaryResponse;
import com.rehome.rehome.dto.response.PostUpdateResponse;
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
import com.rehome.rehome.exception.ConflictException;
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
import com.rehome.rehome.service.CommunityPostService;
import com.rehome.rehome.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityPostServiceImpl implements CommunityPostService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int MAX_PHOTOS = 5;
    private static final int MAX_COMMENT_LENGTH = 500;
    private static final int POST_NOTIFICATION_BODY_LENGTH = 80;
    private static final String CAMPAIGN_NOT_FOUND = "Không tìm thấy chiến dịch.";
    private static final String COMMENT_NOT_FOUND = "Không tìm thấy bình luận.";

    private final com.rehome.rehome.service.ContentModerationService moderation;
    private final CommunityPostRepository communityPostRepository;
    private final FollowRepository followRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final AccountRepository accountRepository;
    private final OrganizationProfileRepository organizationProfileRepository;
    private final CommunityPostPhotoRepository communityPostPhotoRepository;
    private final CampaignRepository campaignRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final ViolationReportRepository violationReportRepository;
    private final NotificationService notificationService;

    // Scope của newsfeed khi không lọc theo 1 tổ chức cụ thể (organizationId == null).
    // ALL: mọi bài VISIBLE xuyên tổ chức, Guest xem được. FOLLOWING: chỉ bài của tổ chức
    // viewer đang theo dõi, bắt buộc đăng nhập.
    private enum PostFeedScope { ALL, FOLLOWING }

    // Không truyền scope: giữ hành vi cũ là FOLLOWING khi đã đăng nhập; Guest không có gì để
    // "theo dõi" nên mặc định cho xem ALL thay vì chặn 401 như trước.
    private static PostFeedScope resolveScope(String rawScope, UUID viewerId) {
        if (!StringUtils.hasText(rawScope)) {
            return viewerId != null ? PostFeedScope.FOLLOWING : PostFeedScope.ALL;
        }
        try {
            return PostFeedScope.valueOf(rawScope.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("scope không hợp lệ.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostSummaryResponse> listPosts(UUID organizationId, String scope, UUID viewerId,
                                                         int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<CommunityPost> result;
        if (organizationId != null) {
            result = communityPostRepository.findByOrganization_AccountIdAndStatus(
                    organizationId, ContentStatus.VISIBLE, pageable);
        } else if (resolveScope(scope, viewerId) == PostFeedScope.ALL) {
            result = communityPostRepository.findByStatus(ContentStatus.VISIBLE, pageable);
        } else {
            if (viewerId == null) {
                throw new UnauthorizedException("Cần đăng nhập để xem newsfeed theo dõi.");
            }
            List<UUID> followedOrganizationIds = followRepository.findFollowedOrganizationIds(viewerId);
            result = followedOrganizationIds.isEmpty()
                    ? Page.empty(pageable)
                    : communityPostRepository.findByOrganization_AccountIdInAndStatus(
                            followedOrganizationIds, ContentStatus.VISIBLE, pageable);
        }

        return PageResponse.from(toSummaryPage(result, viewerId));
    }

    private Page<PostSummaryResponse> toSummaryPage(Page<CommunityPost> result, UUID viewerId) {
        if (result.isEmpty()) {
            return result.map(post -> PostSummaryResponse.builder().build());
        }

        List<UUID> postIds = result.getContent().stream().map(CommunityPost::getId).toList();
        Map<UUID, Long> likeCounts = toCountMap(postLikeRepository.countByPostIds(postIds));
        Map<UUID, Long> commentCounts = toCountMap(postCommentRepository.countByPostIds(postIds));
        Map<UUID, List<String>> photoUrlsByPost = communityPostPhotoRepository.findUrlsByPostIds(postIds).stream()
                .collect(Collectors.groupingBy(row -> (UUID) row[0],
                        Collectors.mapping(row -> (String) row[1], Collectors.toList())));
        Set<UUID> likedByMePostIds = viewerId == null ? Set.of()
                : Set.copyOf(postLikeRepository.findLikedPostIds(postIds, viewerId));

        List<UUID> organizationIds = result.getContent().stream()
                .map(post -> post.getOrganization().getAccountId()).distinct().toList();
        Map<UUID, OrganizationProfile> organizationsById = organizationProfileRepository.findAllById(organizationIds)
                .stream().collect(Collectors.toMap(OrganizationProfile::getAccountId, o -> o));

        return result.map(post -> {
            Campaign campaign = post.getCampaign();
            OrganizationProfile organization = organizationsById.get(post.getOrganization().getAccountId());
            return PostSummaryResponse.builder()
                    .id(post.getId().toString())
                    .organizationId(post.getOrganization().getAccountId().toString())
                    .content(post.getContent())
                    .status(post.getStatus().name())
                    .photoUrls(photoUrlsByPost.getOrDefault(post.getId(), List.of()))
                    .createdAt(post.getCreatedAt())
                    .campaignId(campaign == null ? null : campaign.getId().toString())
                    .campaignTitle(campaign == null ? null : campaign.getTitle())
                    .organization(organization == null ? null : PostOrganizationSummaryResponse.builder()
                            .id(organization.getAccountId().toString())
                            .displayName(organization.getDisplayName())
                            .logoUrl(organization.getLogoUrl())
                            .build())
                    .likeCount(likeCounts.getOrDefault(post.getId(), 0L))
                    .commentCount(commentCounts.getOrDefault(post.getId(), 0L))
                    .likedByMe(likedByMePostIds.contains(post.getId()))
                    .build();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetailResponse getDetail(UUID postId, UUID viewerId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết."));

        boolean isOwner = viewerId != null && post.getOrganization().getAccountId().equals(viewerId);
        // REMOVED (Moderator gỡ do vi phạm): chỉ tổ chức sở hữu xem được, kèm lý do gỡ. Member/Guest
        // coi như không tồn tại - giống hành vi với Campaign HIDDEN (CampaignServiceImpl.getPublicDetail)
        if (post.getStatus() == ContentStatus.REMOVED && !isOwner) {
            throw new IllegalArgumentException("Không tìm thấy bài viết.");
        }

        long likeCount = postLikeRepository.countByPost_Id(postId);
        // Bình luận REMOVED vẫn chiếm 1 dòng trong danh sách (content null), nên vẫn tính vào tổng
        long commentCount = postCommentRepository.countByPost_Id(postId);
        boolean likedByMe = viewerId != null && postLikeRepository.existsByPost_IdAndMember_Id(postId, viewerId);

        List<String> photoUrls = communityPostPhotoRepository.findByPost_Id(postId).stream()
                .map(CommunityPostPhoto::getImageUrl)
                .toList();
        Campaign campaign = post.getCampaign();
        OrganizationProfile organization = post.getOrganization();

        return PostDetailResponse.builder()
                .id(post.getId().toString())
                .content(post.getContent())
                .photoUrls(photoUrls)
                .createdAt(post.getCreatedAt())
                .campaignId(campaign == null ? null : campaign.getId().toString())
                .campaignTitle(campaign == null ? null : campaign.getTitle())
                .organization(PostOrganizationSummaryResponse.builder()
                        .id(organization.getAccountId().toString())
                        .displayName(organization.getDisplayName())
                        .logoUrl(organization.getLogoUrl())
                        .build())
                .likeCount(likeCount)
                .commentCount(commentCount)
                .likedByMe(likedByMe)
                .status(post.getStatus().name())
                .removalReason(isOwner && post.getStatus() == ContentStatus.REMOVED
                        ? latestResolutionReason(ViolationTargetType.POST, postId)
                        : null)
                .build();
    }

    // Báo cáo RESOLVED gần nhất của target này; null nếu không có (gỡ vì lý do khác chưa có
    // trong hệ thống, hoặc dữ liệu cũ) - dùng chung logic với CampaignServiceImpl.latestResolutionReason
    private String latestResolutionReason(ViolationTargetType targetType, UUID targetId) {
        return violationReportRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(targetType, targetId)
                .stream()
                .filter(r -> r.getResolvedAt() != null)
                .max(Comparator.comparing(com.rehome.rehome.entity.marketplace.ViolationReport::getResolvedAt))
                .map(com.rehome.rehome.entity.marketplace.ViolationReport::getResolutionReason)
                .orElse(null);
    }

    @Override
    @Transactional
    public void like(UUID accountId, UUID postId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .filter(p -> p.getStatus() == ContentStatus.VISIBLE)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết."));

        if (!postLikeRepository.existsByPost_IdAndMember_Id(postId, accountId)) {
            Account account = accountRepository.findById(accountId)
                    .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
            postLikeRepository.save(PostLike.builder().post(post).member(account).build());
        }
    }

    @Override
    @Transactional
    public void unlike(UUID accountId, UUID postId) {
        if (!communityPostRepository.existsById(postId)) {
            throw new IllegalArgumentException("Không tìm thấy bài viết.");
        }
        postLikeRepository.findByPost_IdAndMember_Id(postId, accountId)
                .ifPresent(postLikeRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> listComments(UUID postId, UUID viewerId, int page, int size) {
        communityPostRepository.findById(postId)
                .filter(p -> p.getStatus() == ContentStatus.VISIBLE)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết."));

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        // Trả cả REMOVED (content bỏ ở tầng response) - mobile tự xếp nhóm theo parentId (OB-05)
        Page<PostComment> result = postCommentRepository.findByPost_Id(postId, pageable);
        Map<UUID, AuthorInfo> authorInfoByAccountId = loadAuthorInfoMap(result.getContent());
        return PageResponse.from(result.map(c -> toCommentResponse(c, authorInfoByAccountId, viewerId)));
    }

    @Override
    @Transactional
    public CommentResponse createComment(UUID authorId, UUID postId, CreateCommentRequest request) {
        if (!StringUtils.hasText(request.getContent())) {
            throw new BadRequestException("content là bắt buộc.");
        }
        if (request.getContent().length() > MAX_COMMENT_LENGTH) {
            throw new AppException(ErrorCode.COMMENT_CONTENT_TOO_LONG);
        }

        CommunityPost post = communityPostRepository.findById(postId)
                .filter(p -> p.getStatus() == ContentStatus.VISIBLE)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết."));
        Account author = accountRepository.findById(authorId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));

        // ORGANIZATION chỉ được bình luận/trả lời trên bài của chính mình; Member không giới hạn
        if (author.getRole() == Role.ORGANIZATION
                && !post.getOrganization().getAccountId().equals(authorId)) {
            throw new ForbiddenException("Bạn không có quyền bình luận trên bài viết này.");
        }

        // replyTarget: bình luận thực sự được bấm trả lời (trước khi quy về gốc) - dùng để báo
        // COMMENT_REPLIED cho đúng người, dù lưu quan hệ parent vẫn quy về bình luận gốc (chỉ 1 cấp)
        PostComment replyTarget = resolveReplyTargetOrNull(postId, request.getParentId());
        PostComment rootParent = replyTarget == null ? null
                : (replyTarget.getParent() != null ? replyTarget.getParent() : replyTarget);

        var moderationAction = moderation.evaluate(request.getContent());
        PostComment comment = PostComment.builder()
                .post(post)
                .author(author)
                .parent(rootParent)
                .content(request.getContent())
                .build();
        postCommentRepository.save(comment);
        moderation.recordMatch(authorId, "POST_COMMENT", comment.getId(), moderationAction);

        notifyAboutComment(post, author, replyTarget);

        AuthorInfo authorInfo = loadAuthorInfo(author);
        return toCommentResponse(comment, Collections.singletonMap(authorId, authorInfo), authorId);
    }

    // Trả về đúng bình luận được bấm trả lời (không quy về gốc) - chỉ dùng để xác định người
    // nhận thông báo; quan hệ lưu DB vẫn chỉ 1 cấp, xem rootParent ở createComment.
    private PostComment resolveReplyTargetOrNull(UUID postId, String rawParentId) {
        if (!StringUtils.hasText(rawParentId)) {
            return null;
        }
        UUID parentId;
        try {
            parentId = UUID.fromString(rawParentId.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(COMMENT_NOT_FOUND);
        }
        return postCommentRepository.findById(parentId)
                .filter(c -> c.getPost().getId().equals(postId))
                .orElseThrow(() -> new IllegalArgumentException(COMMENT_NOT_FOUND));
    }

    // COMMENT_REPLIED: có replyTarget và tác giả replyTarget khác người vừa bình luận -> báo cho
    // tác giả replyTarget (có thể là Member hoặc tổ chức, vì giờ cả hai đều có thể bị trả lời).
    // POST_COMMENTED: người bình luận là Member và tổ chức (chủ bài) chưa nhận COMMENT_REPLIED ở
    // bước trên -> báo thêm cho tổ chức. Không bao giờ báo cho chính người vừa bình luận.
    private void notifyAboutComment(CommunityPost post, Account author, PostComment replyTarget) {
        Account organizationAccount = post.getOrganization().getAccount();
        boolean organizationAlreadyNotified = false;

        if (replyTarget != null && !replyTarget.getAuthor().getId().equals(author.getId())) {
            String replierName = loadAuthorInfo(author).name();
            notificationService.notify(replyTarget.getAuthor(), NotificationType.COMMENT_REPLIED,
                    "Bình luận của bạn có phản hồi",
                    (StringUtils.hasText(replierName) ? replierName : "Một người dùng")
                            + " đã trả lời bình luận của bạn.",
                    NotificationTargetType.POST, post.getId(), null);
            organizationAlreadyNotified = replyTarget.getAuthor().getId().equals(organizationAccount.getId());
        }

        if (author.getRole() == Role.MEMBER && !organizationAlreadyNotified
                && !organizationAccount.getId().equals(author.getId())) {
            notificationService.notify(organizationAccount, NotificationType.POST_COMMENTED,
                    "Có bình luận mới",
                    "Bài viết của bạn có bình luận mới.",
                    NotificationTargetType.POST, post.getId(), null);
        }
    }

    // type/name/avatarUrl nhúng trong CommentAuthorResponse; MEMBER đọc MemberProfile,
    // ORGANIZATION đọc OrganizationProfile (tên/logo)
    private record AuthorInfo(String type, String name, String avatarUrl) {
    }

    private AuthorInfo loadAuthorInfo(Account account) {
        if (account.getRole() == Role.ORGANIZATION) {
            OrganizationProfile profile = organizationProfileRepository.findById(account.getId()).orElse(null);
            return new AuthorInfo(Role.ORGANIZATION.name(),
                    profile == null ? null : profile.getDisplayName(),
                    profile == null ? null : profile.getLogoUrl());
        }
        MemberProfile profile = memberProfileRepository.findByAccountId(account.getId()).orElse(null);
        return new AuthorInfo(Role.MEMBER.name(),
                profile == null ? null : profile.getFullName(),
                profile == null ? null : profile.getAvatarUrl());
    }

    private Map<UUID, AuthorInfo> loadAuthorInfoMap(List<PostComment> comments) {
        Set<UUID> memberIds = new HashSet<>();
        Set<UUID> organizationIds = new HashSet<>();
        for (PostComment comment : comments) {
            Account author = comment.getAuthor();
            if (author.getRole() == Role.ORGANIZATION) {
                organizationIds.add(author.getId());
            } else {
                memberIds.add(author.getId());
            }
        }

        Map<UUID, AuthorInfo> result = new HashMap<>();
        if (!memberIds.isEmpty()) {
            for (MemberProfile profile : memberProfileRepository.findAllById(memberIds)) {
                result.put(profile.getAccountId(),
                        new AuthorInfo(Role.MEMBER.name(), profile.getFullName(), profile.getAvatarUrl()));
            }
        }
        if (!organizationIds.isEmpty()) {
            for (OrganizationProfile profile : organizationProfileRepository.findAllById(organizationIds)) {
                result.put(profile.getAccountId(),
                        new AuthorInfo(Role.ORGANIZATION.name(), profile.getDisplayName(), profile.getLogoUrl()));
            }
        }
        return result;
    }

    @Override
    @Transactional
    public PostCreateResponse createPost(UUID organizationAccountId, CreatePostRequest request) {
        if (!StringUtils.hasText(request.getContent())) {
            throw new BadRequestException("content là bắt buộc.");
        }
        validateContentAndPhotos(request.getContent(), request.getPhotoUrls());

        OrganizationProfile organization = organizationProfileRepository.findById(organizationAccountId)
                .orElseThrow(() -> new AppException(ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND));
        // Chỉ tổ chức đã xác minh mới được đăng bài (docs/OVERVIEW.md mục 2, Actor Organization)
        if (organization.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ForbiddenException("Hồ sơ tổ chức chưa được xác minh.");
        }
        // OB-02: campaignId không bắt buộc; nếu có thì chỉ chấp nhận chiến dịch đang OPEN của
        // chính tổ chức này, ngoài phạm vi đó coi như không tìm thấy (giống DonationServiceImpl)
        Campaign campaign = resolveOwnOpenCampaignOrNull(organizationAccountId, request.getCampaignId(), null);

        var moderationAction = moderation.evaluate(request.getContent());
        CommunityPost post = CommunityPost.builder()
                .organization(organization)
                .content(request.getContent())
                .campaign(campaign)
                .build();
        communityPostRepository.save(post);
        moderation.recordMatch(organizationAccountId, "POST", post.getId(), moderationAction);

        List<CommunityPostPhoto> photos = toPhotoEntities(post, request.getPhotoUrls());
        if (!photos.isEmpty()) {
            communityPostPhotoRepository.saveAll(photos);
        }

        // UC-44: báo cho người theo dõi tổ chức khi có bài viết mới. Chỉ gửi lúc TẠO, không gửi
        // khi sửa (updatePost không gọi hàm này).
        notificationService.notifyFollowers(organizationAccountId, NotificationType.FOLLOWED_ORG_POST,
                organization.getDisplayName() + " có bài viết mới",
                truncate(request.getContent(), POST_NOTIFICATION_BODY_LENGTH),
                NotificationTargetType.POST, post.getId());

        return PostCreateResponse.builder()
                .id(post.getId().toString())
                .status(post.getStatus().name())
                .build();
    }

    @Override
    @Transactional
    public PostUpdateResponse updatePost(UUID organizationAccountId, UUID postId, UpdatePostRequest request) {
        if (!StringUtils.hasText(request.getContent())) {
            throw new BadRequestException("content là bắt buộc.");
        }
        validateContentAndPhotos(request.getContent(), request.getPhotoUrls());

        CommunityPost post = requireOwnPost(organizationAccountId, postId,
                "Bạn không có quyền sửa bài viết này.");
        // Bài đã bị Moderator gỡ do vi phạm thì không cho sửa lại nội dung - cùng lý do với
        // Campaign HIDDEN (CampaignServiceImpl.closeCampaign): tránh "rửa" nội dung vi phạm
        // rồi trông như bài vẫn còn hợp lệ.
        if (post.getStatus() == ContentStatus.REMOVED) {
            throw new ConflictException(
                    "Bài viết đã bị gỡ do vi phạm, không thể sửa.", "POST_ALREADY_REMOVED");
        }
        Campaign campaign = resolveOwnOpenCampaignOrNull(organizationAccountId, request.getCampaignId(), post.getCampaign());

        var moderationAction = moderation.evaluate(request.getContent());
        post.setContent(request.getContent());
        post.setCampaign(campaign);
        communityPostRepository.save(post);
        moderation.recordMatch(organizationAccountId, "POST", post.getId(), moderationAction);

        // OB-03: danh sách ảnh mới thay thế hẳn danh sách cũ, không patch từng ảnh
        communityPostPhotoRepository.deleteByPost_Id(postId);
        List<CommunityPostPhoto> photos = toPhotoEntities(post, request.getPhotoUrls());
        if (!photos.isEmpty()) {
            communityPostPhotoRepository.saveAll(photos);
        }

        return PostUpdateResponse.builder()
                .id(post.getId().toString())
                .content(post.getContent())
                .photoUrls(photos.stream().map(CommunityPostPhoto::getImageUrl).toList())
                .campaignId(campaign == null ? null : campaign.getId().toString())
                .campaignTitle(campaign == null ? null : campaign.getTitle())
                .build();
    }

    // Giới hạn mobile đang áp (content <= 2000 ký tự, tối đa 5 ảnh) — BE trước đây không kiểm,
    // giờ chặn luôn ở tầng service và trả AppException để FE phân biệt bằng code (xem ErrorCode)
    private void validateContentAndPhotos(String content, List<String> photoUrls) {
        if (content != null && content.length() > MAX_CONTENT_LENGTH) {
            throw new AppException(ErrorCode.POST_CONTENT_TOO_LONG);
        }
        long photoCount = photoUrls == null ? 0 : photoUrls.stream().filter(StringUtils::hasText).count();
        if (photoCount > MAX_PHOTOS) {
            throw new AppException(ErrorCode.POST_TOO_MANY_PHOTOS);
        }
    }

    // UC-44: body thông báo bài viết mới chỉ lấy 80 ký tự đầu, tránh thông báo quá dài
    private String truncate(String content, int maxLength) {
        if (content == null || content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength);
    }

    private List<CommunityPostPhoto> toPhotoEntities(CommunityPost post, List<String> photoUrls) {
        if (photoUrls == null) {
            return List.of();
        }
        return photoUrls.stream()
                .filter(StringUtils::hasText)
                .map(url -> CommunityPostPhoto.builder().post(post).imageUrl(url.trim()).build())
                .toList();
    }

    // OB-02/OB-03: campaignId rỗng -> không gắn/gỡ chiến dịch; có giá trị thì chiến dịch phải
    // đang OPEN và thuộc đúng tổ chức này, ngoài phạm vi đó coi như không tìm thấy.
    // Ngoại lệ: nếu campaignId gửi lên trùng với chiến dịch bài đang gắn (currentCampaign) thì
    // giữ nguyên dù chiến dịch đã CLOSED - không thì sửa nội dung/ảnh của bài cũ sẽ luôn fail.
    private Campaign resolveOwnOpenCampaignOrNull(UUID organizationAccountId, String rawCampaignId,
            Campaign currentCampaign) {
        if (!StringUtils.hasText(rawCampaignId)) {
            return null;
        }
        UUID campaignId;
        try {
            campaignId = UUID.fromString(rawCampaignId.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(CAMPAIGN_NOT_FOUND);
        }
        if (currentCampaign != null && currentCampaign.getId().equals(campaignId)) {
            return currentCampaign;
        }
        return campaignRepository.findById(campaignId)
                .filter(c -> c.getOrganization().getAccountId().equals(organizationAccountId))
                .filter(c -> c.getStatus() == CampaignStatus.OPEN)
                .orElseThrow(() -> new IllegalArgumentException(CAMPAIGN_NOT_FOUND));
    }

    @Override
    @Transactional
    public void deletePost(UUID organizationAccountId, UUID postId) {
        CommunityPost post = requireOwnPost(organizationAccountId, postId,
                "Bạn không có quyền xóa bài viết này.");

        // Chuyển REMOVED thay vì xóa cứng: like/bình luận đang trỏ tới bài này bằng FK,
        // và bài REMOVED đã bị loại khỏi mọi endpoint công khai (F-086)
        if (post.getStatus() != ContentStatus.REMOVED) {
            post.setStatus(ContentStatus.REMOVED);
            communityPostRepository.save(post);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MyPostResponse> listMyPosts(UUID organizationAccountId, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<CommunityPost> result = communityPostRepository
                .findByOrganization_AccountId(organizationAccountId, pageable);
        if (result.isEmpty()) {
            return PageResponse.from(result.map(post -> MyPostResponse.builder().build()));
        }

        List<UUID> postIds = result.getContent().stream().map(CommunityPost::getId).toList();
        Map<UUID, Long> likeCounts = toCountMap(postLikeRepository.countByPostIds(postIds));
        // Bình luận REMOVED vẫn chiếm 1 dòng trong danh sách (content null), nên vẫn tính vào tổng
        Map<UUID, Long> commentCounts = toCountMap(postCommentRepository.countByPostIds(postIds));
        Map<UUID, Long> reportCounts = toCountMap(violationReportRepository.countByTargetTypeAndTargetIdInAndStatus(
                ViolationTargetType.POST, postIds, ViolationReportStatus.PENDING));
        Map<UUID, List<String>> photoUrlsByPost = communityPostPhotoRepository.findUrlsByPostIds(postIds).stream()
                .collect(Collectors.groupingBy(row -> (UUID) row[0],
                        Collectors.mapping(row -> (String) row[1], Collectors.toList())));
        Set<UUID> likedByMePostIds = Set.copyOf(
                postLikeRepository.findLikedPostIds(postIds, organizationAccountId));
        Map<UUID, String> removalReasonsByPost = latestResolutionReasons(ViolationTargetType.POST, result.getContent().stream()
                .filter(p -> p.getStatus() == ContentStatus.REMOVED)
                .map(CommunityPost::getId).toList());

        return PageResponse.from(result.map(post -> {
            Campaign campaign = post.getCampaign();
            return MyPostResponse.builder()
                    .id(post.getId().toString())
                    .content(post.getContent())
                    .photoUrls(photoUrlsByPost.getOrDefault(post.getId(), List.of()))
                    .createdAt(post.getCreatedAt())
                    .campaignId(campaign == null ? null : campaign.getId().toString())
                    .campaignTitle(campaign == null ? null : campaign.getTitle())
                    .likeCount(likeCounts.getOrDefault(post.getId(), 0L))
                    .commentCount(commentCounts.getOrDefault(post.getId(), 0L))
                    .reportCount(reportCounts.getOrDefault(post.getId(), 0L))
                    .likedByMe(likedByMePostIds.contains(post.getId()))
                    .status(post.getStatus().name())
                    .removalReason(removalReasonsByPost.get(post.getId()))
                    .build();
        }));
    }

    // Phiên bản gom nhiều target của latestResolutionReason (xem getDetail) - tránh N+1 khi 1 trang
    // có nhiều bài REMOVED; targetIds rỗng thì bỏ qua query luôn.
    private Map<UUID, String> latestResolutionReasons(ViolationTargetType targetType, List<UUID> targetIds) {
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        return violationReportRepository.findByTargetTypeAndTargetIdInAndStatus(
                        targetType, targetIds, ViolationReportStatus.RESOLVED)
                .stream()
                .filter(r -> r.getResolvedAt() != null)
                .collect(Collectors.groupingBy(
                        com.rehome.rehome.entity.marketplace.ViolationReport::getTargetId,
                        Collectors.collectingAndThen(
                                Collectors.maxBy(Comparator.comparing(
                                        com.rehome.rehome.entity.marketplace.ViolationReport::getResolvedAt)),
                                r -> r.map(com.rehome.rehome.entity.marketplace.ViolationReport::getResolutionReason)
                                        .orElse(null))));
    }

    private CommunityPost requireOwnPost(UUID organizationAccountId, UUID postId, String forbiddenMessage) {
        CommunityPost post = communityPostRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết."));
        if (!post.getOrganization().getAccountId().equals(organizationAccountId)) {
            throw new ForbiddenException(forbiddenMessage);
        }
        return post;
    }

    private static Map<UUID, Long> toCountMap(List<Object[]> rows) {
        return rows.stream().collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
    }

    private static CommentResponse toCommentResponse(PostComment comment, Map<UUID, AuthorInfo> authorInfoByAccountId,
                                                      UUID viewerId) {
        boolean removed = comment.getStatus() == ContentStatus.REMOVED;
        Account authorAccount = comment.getAuthor();
        AuthorInfo authorInfo = authorInfoByAccountId.get(authorAccount.getId());

        return CommentResponse.builder()
                .id(comment.getId().toString())
                .parentId(comment.getParent() == null ? null : comment.getParent().getId().toString())
                // REMOVED: Moderator đã ẩn -> bỏ nội dung, dòng vẫn trả về (OB-05)
                .content(removed ? null : comment.getContent())
                .status(comment.getStatus().name())
                .author(CommentAuthorResponse.builder()
                        .id(authorAccount.getId().toString())
                        .type(authorInfo != null ? authorInfo.type() : authorAccount.getRole().name())
                        .displayName(authorInfo == null ? null : authorInfo.name())
                        .avatarUrl(authorInfo == null ? null : authorInfo.avatarUrl())
                        .build())
                .createdAt(comment.getCreatedAt())
                .isMine(viewerId != null && authorAccount.getId().equals(viewerId))
                .build();
    }
}
