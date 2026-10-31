package com.rehome.rehome.serviceImpl;

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
import com.rehome.rehome.service.CommunityPostService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommunityPostServiceImpl implements CommunityPostService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CommunityPostRepository communityPostRepository;
    private final FollowRepository followRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final AccountRepository accountRepository;
    private final OrganizationProfileRepository organizationProfileRepository;
    private final CommunityPostPhotoRepository communityPostPhotoRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostSummaryResponse> listPosts(UUID organizationId, UUID viewerId, int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<CommunityPost> result;
        if (organizationId != null) {
            result = communityPostRepository.findByOrganization_AccountIdAndStatus(
                    organizationId, ContentStatus.VISIBLE, pageable);
        } else {
            if (viewerId == null) {
                throw new UnauthorizedException("Cần đăng nhập để xem newsfeed.");
            }
            List<UUID> followedOrganizationIds = followRepository.findFollowedOrganizationIds(viewerId);
            result = followedOrganizationIds.isEmpty()
                    ? Page.empty(pageable)
                    : communityPostRepository.findByOrganization_AccountIdInAndStatus(
                            followedOrganizationIds, ContentStatus.VISIBLE, pageable);
        }

        return PageResponse.from(result.map(CommunityPostServiceImpl::toSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public PostDetailResponse getDetail(UUID postId, UUID viewerId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .filter(p -> p.getStatus() == ContentStatus.VISIBLE)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết."));

        long likeCount = postLikeRepository.countByPost_Id(postId);
        long commentCount = postCommentRepository.countByPost_IdAndStatus(postId, ContentStatus.VISIBLE);
        boolean likedByMe = viewerId != null && postLikeRepository.existsByPost_IdAndMember_Id(postId, viewerId);

        return PostDetailResponse.builder()
                .id(post.getId().toString())
                .content(post.getContent())
                .likeCount(likeCount)
                .commentCount(commentCount)
                .likedByMe(likedByMe)
                .build();
    }

    @Override
    @Transactional
    public void like(UUID memberId, UUID postId) {
        CommunityPost post = communityPostRepository.findById(postId)
                .filter(p -> p.getStatus() == ContentStatus.VISIBLE)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết."));

        if (!postLikeRepository.existsByPost_IdAndMember_Id(postId, memberId)) {
            Account member = accountRepository.findById(memberId)
                    .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
            postLikeRepository.save(PostLike.builder().post(post).member(member).build());
        }
    }

    @Override
    @Transactional
    public void unlike(UUID memberId, UUID postId) {
        if (!communityPostRepository.existsById(postId)) {
            throw new IllegalArgumentException("Không tìm thấy bài viết.");
        }
        postLikeRepository.findByPost_IdAndMember_Id(postId, memberId)
                .ifPresent(postLikeRepository::delete);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> listComments(UUID postId, int page, int size) {
        communityPostRepository.findById(postId)
                .filter(p -> p.getStatus() == ContentStatus.VISIBLE)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết."));

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<PostComment> result = postCommentRepository.findByPost_IdAndStatus(postId, ContentStatus.VISIBLE, pageable);
        return PageResponse.from(result.map(CommunityPostServiceImpl::toCommentResponse));
    }

    @Override
    @Transactional
    public CommentResponse createComment(UUID memberId, UUID postId, CreateCommentRequest request) {
        if (!StringUtils.hasText(request.getContent())) {
            throw new BadRequestException("content là bắt buộc.");
        }

        CommunityPost post = communityPostRepository.findById(postId)
                .filter(p -> p.getStatus() == ContentStatus.VISIBLE)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết."));
        Account member = accountRepository.findById(memberId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));

        PostComment comment = PostComment.builder()
                .post(post)
                .member(member)
                .content(request.getContent())
                .build();
        postCommentRepository.save(comment);

        return toCommentResponse(comment);
    }

    @Override
    @Transactional
    public PostCreateResponse createPost(UUID organizationAccountId, CreatePostRequest request) {
        if (!StringUtils.hasText(request.getContent())) {
            throw new BadRequestException("content là bắt buộc.");
        }

        OrganizationProfile organization = organizationProfileRepository.findById(organizationAccountId)
                .orElseThrow(() -> new AppException(ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND));
        // Chỉ tổ chức đã xác minh mới được đăng bài (docs/OVERVIEW.md mục 2, Actor Organization)
        if (organization.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ForbiddenException("Hồ sơ tổ chức chưa được xác minh.");
        }

        CommunityPost post = CommunityPost.builder()
                .organization(organization)
                .content(request.getContent())
                .build();
        communityPostRepository.save(post);

        List<String> photoUrls = request.getPhotoUrls() == null ? List.of() : request.getPhotoUrls();
        List<CommunityPostPhoto> photos = photoUrls.stream()
                .filter(StringUtils::hasText)
                .map(url -> CommunityPostPhoto.builder().post(post).imageUrl(url.trim()).build())
                .toList();
        if (!photos.isEmpty()) {
            communityPostPhotoRepository.saveAll(photos);
        }

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

        CommunityPost post = requireOwnPost(organizationAccountId, postId,
                "Bạn không có quyền sửa bài viết này.");
        post.setContent(request.getContent());
        communityPostRepository.save(post);

        return PostUpdateResponse.builder()
                .id(post.getId().toString())
                .content(post.getContent())
                .build();
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
        Map<UUID, Long> commentCounts = toCountMap(
                postCommentRepository.countByPostIdsAndStatus(postIds, ContentStatus.VISIBLE));

        return PageResponse.from(result.map(post -> MyPostResponse.builder()
                .id(post.getId().toString())
                .content(post.getContent())
                .likeCount(likeCounts.getOrDefault(post.getId(), 0L))
                .commentCount(commentCounts.getOrDefault(post.getId(), 0L))
                .build()));
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

    private static PostSummaryResponse toSummary(CommunityPost post) {
        return PostSummaryResponse.builder()
                .id(post.getId().toString())
                .organizationId(post.getOrganization().getAccountId().toString())
                .content(post.getContent())
                .status(post.getStatus().name())
                .build();
    }

    private static CommentResponse toCommentResponse(PostComment comment) {
        return CommentResponse.builder()
                .id(comment.getId().toString())
                .content(comment.getContent())
                .status(comment.getStatus().name())
                .build();
    }
}
