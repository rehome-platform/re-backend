package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.rehome.rehome.dto.request.CreateLegalChangeRequestRequest;
import com.rehome.rehome.dto.request.RejectLegalChangeRequest;
import com.rehome.rehome.dto.request.RejectOrganizationRequest;
import com.rehome.rehome.dto.request.UpdateOrganizationProfileRequest;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.LegalChangeRequestDetailResponse;
import com.rehome.rehome.dto.response.LegalChangeRequestResponse;
import com.rehome.rehome.dto.response.OrganizationNearbyCampaignResponse;
import com.rehome.rehome.dto.response.OrganizationNearbyResponse;
import com.rehome.rehome.dto.response.OrganizationOptionsResponse;
import com.rehome.rehome.dto.response.OrganizationProfileResponse;
import com.rehome.rehome.dto.response.OrganizationProfileUpdateResponse;
import com.rehome.rehome.dto.response.OrganizationRejectResponse;
import com.rehome.rehome.dto.response.OrganizationPublicProfileResponse;
import com.rehome.rehome.dto.response.OrganizationResubmitResponse;
import com.rehome.rehome.dto.response.OrganizationVerificationResponse;
import com.rehome.rehome.dto.response.OrganizationVerifyResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationLegalChangeRequest;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.auth.OrganizationRepresentative;
import com.rehome.rehome.entity.marketplace.Campaign;
import com.rehome.rehome.entity.marketplace.Follow;
import com.rehome.rehome.entity.auth.OrganizationDocument;
import com.rehome.rehome.entity.enums.CampaignStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.entity.enums.LegalChangeRequestStatus;
import com.rehome.rehome.entity.enums.NotificationTargetType;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.service.NotificationService;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.OrganizationLegalChangeMapper;
import com.rehome.rehome.mapper.OrganizationProfileMapper;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CampaignRepository;
import com.rehome.rehome.repository.FollowRepository;
import com.rehome.rehome.repository.OrganizationLegalChangeRequestRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.service.OrganizationService;
import com.rehome.rehome.util.VietnameseText;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private static final String ORGANIZATION_NOT_FOUND = "Không tìm thấy tổ chức.";
    private static final String LEGAL_CHANGE_REQUEST_NOT_FOUND = "Không tìm thấy yêu cầu đổi thông tin.";
    private static final int MAX_PAGE_SIZE = 50;

    private final OrganizationProfileRepository organizationProfileRepository;
    private final OrganizationProfileMapper organizationProfileMapper;
    private final AccountRepository accountRepository;
    private final QueueService queueService;
    private final FollowRepository followRepository;
    private final OrganizationLegalChangeRequestRepository organizationLegalChangeRequestRepository;
    private final OrganizationLegalChangeMapper organizationLegalChangeMapper;
    private final NotificationService notificationService;
    private final CampaignRepository campaignRepository;

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final int NEARBY_LIMIT = 20;

    @Override
    @Transactional
    public RegisterOrganizationResponse registerOrganization(UUID accountId, RegisterOrganizationRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));

        if (account.getRole() != Role.ORGANIZATION) {
            throw new AppException(ErrorCode.NOT_ORGANIZATION_ACCOUNT);
        }

        String email = request.getContactEmail().trim().toLowerCase();

        if (organizationProfileRepository.existsByAccountId(accountId)) {
            throw new AppException(ErrorCode.ORGANIZATION_PROFILE_ALREADY_EXISTS);
        }
        if (organizationProfileRepository.existsByContactEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.ORGANIZATION_EMAIL_TAKEN);
        }
        if (organizationProfileRepository.existsByTaxCode(request.getTaxCode())) {
            throw new AppException(ErrorCode.ORGANIZATION_TAX_CODE_TAKEN);
        }

        if (organizationProfileRepository.existsByContactEmailIgnoreCase(email)) {
            throw new AppException(ErrorCode.ORGANIZATION_EMAIL_TAKEN);
        }
        if (organizationProfileRepository.existsByTaxCode(request.getTaxCode())) {
            throw new AppException(ErrorCode.ORGANIZATION_TAX_CODE_TAKEN);
        }

        OrganizationProfile organization = organizationProfileMapper.toEntity(request);
        organization.setContactEmail(email);              // lưu bản đã chuẩn hóa
        organization.setAccount(account);                 // account giờ là managed
        organization.setVerificationStatus(VerificationStatus.PENDING);
        organization.setSubmittedAt(Instant.now());

        organizationProfileRepository.save(organization);
        queueService.enqueue(QueueType.ORG_VERIFICATION, account.getId().toString(), null);
        return organizationProfileMapper.toResponse(organization);
    }

    @Override
    @Transactional
    public OrganizationResubmitResponse resubmit(Account account, RegisterOrganizationRequest request) {
        OrganizationProfile profile = organizationProfileRepository.findByAccountId(account.getId())
                .orElseThrow(() -> new AppException(ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND));

        if (profile.getVerificationStatus() != VerificationStatus.REJECTED) {
            throw new AppException(ErrorCode.ORGANIZATION_NOT_REJECTED);
        }

        String email = request.getContactEmail().trim().toLowerCase();
        if (organizationProfileRepository.existsByContactEmailIgnoreCaseAndAccountIdNot(email, account.getId())) {
            throw new AppException(ErrorCode.ORGANIZATION_EMAIL_TAKEN);
        }
        if (organizationProfileRepository.existsByTaxCodeAndAccountIdNot(request.getTaxCode(), account.getId())) {
            throw new AppException(ErrorCode.ORGANIZATION_TAX_CODE_TAKEN);
        }

        organizationProfileMapper.updateEntity(request, profile);
        profile.setVerificationStatus(VerificationStatus.PENDING);
        profile.setSubmittedAt(Instant.now());
        organizationProfileRepository.save(profile);
        queueService.enqueue(QueueType.ORG_VERIFICATION, account.getId().toString(), null);

        return new OrganizationResubmitResponse(profile.getVerificationStatus());
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationProfileResponse getMyProfile(UUID accountId) {
        // OrganizationProfile dùng @MapsId nên accountId chính là khóa chính
        return organizationProfileRepository.findById(accountId)
                .map(OrganizationProfileResponse::from)
                .orElseGet(OrganizationProfileResponse::notSubmitted);
    }

    @Override
    public OrganizationOptionsResponse getOptions() {
        return OrganizationOptionsResponse.build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationPublicProfileResponse getPublicProfile(UUID organizationId, Account viewer) {
        boolean isModerator = viewer != null && viewer.getRole() == Role.MODERATOR;

        OrganizationProfile profile = organizationProfileRepository.findById(organizationId)
                .filter(p -> isModerator || p.getVerificationStatus() == VerificationStatus.VERIFIED)
                .orElseThrow(() -> new IllegalArgumentException(ORGANIZATION_NOT_FOUND));

        UUID viewerId = viewer != null ? viewer.getId() : null;
        boolean isFollowing = viewerId != null
                && followRepository.existsByMember_IdAndOrganization_AccountId(viewerId, organizationId);
        long followerCount = followRepository.countByOrganization_AccountId(organizationId);

        return toPublicProfileResponse(profile, isFollowing, followerCount);
    }

    private static OrganizationPublicProfileResponse toPublicProfileResponse(OrganizationProfile profile,
                                                                              boolean isFollowing,
                                                                              long followerCount) {
        return OrganizationPublicProfileResponse.builder()
                .id(profile.getAccountId().toString())
                .displayName(profile.getDisplayName())
                .description(profile.getDescription())
                .logoUrl(profile.getLogoUrl())
                .orgType(profile.getOrgType())
                .operationScope(profile.getOperationScope())
                .verifiedAt(profile.getVerifiedAt())
                .followerCount(followerCount)
                .donationPointName(profile.getDonationPointName())
                .donationPointAddress(profile.getDonationPointAddress())
                .donationPointLatitude(profile.getDonationPointLatitude())
                .donationPointLongitude(profile.getDonationPointLongitude())
                .donationPointReceivingHours(profile.getDonationPointReceivingHours())
                .isFollowing(isFollowing)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationNearbyResponse> findNearby(double lat, double lng) {
        if (Double.isNaN(lat) || Double.isNaN(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new AppException(ErrorCode.INVALID_COORDINATES);
        }

        List<OrganizationProfile> organizations = organizationProfileRepository
                .findAllByVerificationStatus(VerificationStatus.VERIFIED);

        List<UUID> organizationIds = organizations.stream().map(OrganizationProfile::getAccountId).toList();
        Map<UUID, List<OrganizationNearbyCampaignResponse>> activeCampaignsByOrgId = organizationIds.isEmpty()
                ? Map.of()
                : campaignRepository
                        .findAllByOrganization_AccountIdInAndStatus(organizationIds, CampaignStatus.OPEN).stream()
                        .collect(Collectors.groupingBy(
                                c -> c.getOrganization().getAccountId(),
                                Collectors.mapping(
                                        c -> new OrganizationNearbyCampaignResponse(c.getId(), c.getEndTime()),
                                        Collectors.toList())));

        return organizations.stream()
                .map(org -> new OrganizationNearbyResponse(
                        org.getAccountId(),
                        org.getDisplayName(),
                        org.getLogoUrl(),
                        org.getDonationPointAddress(),
                        org.getDonationPointLatitude(),
                        org.getDonationPointLongitude(),
                        org.getDonationPointReceivingHours(),
                        org.getOrgType(),
                        haversine(lat, lng,
                                org.getDonationPointLatitude().doubleValue(),
                                org.getDonationPointLongitude().doubleValue()),
                        activeCampaignsByOrgId.getOrDefault(org.getAccountId(), List.of())))
                .sorted(Comparator.comparingDouble(OrganizationNearbyResponse::distanceKm))
                .limit(NEARBY_LIMIT)
                .toList();
    }

    private static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.asin(Math.sqrt(a));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrganizationPublicProfileResponse> listFollowing(UUID memberId, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<Follow> follows = followRepository.findByMember_IdOrderByCreatedAtDesc(memberId, pageable);
        if (follows.isEmpty()) {
            return PageResponse.from(follows.map(f -> toPublicProfileResponse(f.getOrganization(), true, 0L)));
        }

        List<UUID> organizationIds = follows.getContent().stream()
                .map(f -> f.getOrganization().getAccountId()).toList();
        Map<UUID, Long> followerCounts = followRepository.countByOrganizationIds(organizationIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));

        return PageResponse.from(follows.map(f -> toPublicProfileResponse(
                f.getOrganization(), true, followerCounts.getOrDefault(f.getOrganization().getAccountId(), 0L))));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<OrganizationPublicProfileResponse> listPublicOrganizations(String search, UUID viewerId,
                                                                                     int page, int size) {
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "verifiedAt"));
        String keyword = StringUtils.hasText(search) ? VietnameseText.normalize(search) : "";

        Page<OrganizationProfile> result = organizationProfileRepository.search(
                VerificationStatus.VERIFIED, keyword, pageable);
        if (result.isEmpty()) {
            return PageResponse.from(result.map(p -> toPublicProfileResponse(p, false, 0L)));
        }

        List<UUID> organizationIds = result.getContent().stream().map(OrganizationProfile::getAccountId).toList();
        Map<UUID, Long> followerCounts = followRepository.countByOrganizationIds(organizationIds).stream()
                .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
        Set<UUID> followedOrganizationIds = viewerId == null
                ? Set.of()
                : Set.copyOf(followRepository.findFollowedOrganizationIds(viewerId));

        return PageResponse.from(result.map(p -> toPublicProfileResponse(p,
                followedOrganizationIds.contains(p.getAccountId()),
                followerCounts.getOrDefault(p.getAccountId(), 0L))));
    }

    @Override
    @Transactional
    public void follow(UUID memberId, UUID organizationId) {
        OrganizationProfile organization = organizationProfileRepository.findById(organizationId)
                .filter(p -> p.getVerificationStatus() == VerificationStatus.VERIFIED)
                .orElseThrow(() -> new IllegalArgumentException(ORGANIZATION_NOT_FOUND));

        if (!followRepository.existsByMember_IdAndOrganization_AccountId(memberId, organizationId)) {
            Account member = accountRepository.findById(memberId)
                    .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));
            followRepository.save(Follow.builder().member(member).organization(organization).build());
        }
    }

    @Override
    @Transactional
    public void unfollow(UUID memberId, UUID organizationId) {
        if (!organizationProfileRepository.existsById(organizationId)) {
            throw new IllegalArgumentException(ORGANIZATION_NOT_FOUND);
        }
        followRepository.findByMember_IdAndOrganization_AccountId(memberId, organizationId)
                .ifPresent(followRepository::delete);
    }

    @Override
    @Transactional
    public OrganizationProfileUpdateResponse updateMyProfile(UUID accountId,
                                                             UpdateOrganizationProfileRequest request) {
        OrganizationProfile profile = organizationProfileRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND));

        // PATCH: bỏ trống field nào thì giữ nguyên giá trị cũ. description/contactPhone/
        // donationPointReceivingHours là NOT NULL nên không cho ghi đè bằng chuỗi rỗng.
        // displayName khác legalName (pháp lý, đi qua F-097) nên sửa thẳng ở đây được (OP-02)
        if (StringUtils.hasText(request.getDisplayName())) {
            profile.setDisplayName(request.getDisplayName());
        }
        if (StringUtils.hasText(request.getLogoUrl())) {
            profile.setLogoUrl(request.getLogoUrl());
        }
        if (StringUtils.hasText(request.getDescription())) {
            profile.setDescription(request.getDescription());
        }
        if (StringUtils.hasText(request.getContactPhone())) {
            profile.setContactPhone(request.getContactPhone());
        }
        if (StringUtils.hasText(request.getDonationPointReceivingHours())) {
            profile.setDonationPointReceivingHours(request.getDonationPointReceivingHours());
        }
        organizationProfileRepository.save(profile);

        return OrganizationProfileUpdateResponse.builder()
                .id(profile.getAccountId().toString())
                .displayName(profile.getDisplayName())
                .logoUrl(profile.getLogoUrl())
                .description(profile.getDescription())
                .contactPhone(profile.getContactPhone())
                .donationPointReceivingHours(profile.getDonationPointReceivingHours())
                .build();
    }

    @Override
    @Transactional
    public LegalChangeRequestResponse requestLegalChange(UUID accountId,
                                                         CreateLegalChangeRequestRequest request) {
        JsonNode proposedChanges = request.getProposedChanges();
        if (proposedChanges == null || proposedChanges.isNull() || proposedChanges.isEmpty()) {
            throw new BadRequestException("proposedChanges là bắt buộc.");
        }
        // Kiểm trường lạ ngay lúc nộp, không để tới lúc Moderator duyệt mới phát hiện
        organizationLegalChangeMapper.validate(proposedChanges);

        // Wireframe OP-05: mỗi lần chỉ 1 yêu cầu đang chờ duyệt
        if (organizationLegalChangeRequestRepository.existsByOrganization_AccountIdAndStatus(
                accountId, LegalChangeRequestStatus.PENDING)) {
            throw new AppException(ErrorCode.LEGAL_CHANGE_REQUEST_ALREADY_PENDING);
        }

        OrganizationProfile profile = organizationProfileRepository.findById(accountId)
                .orElseThrow(() -> new AppException(ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND));

        OrganizationLegalChangeRequest legalChangeRequest = OrganizationLegalChangeRequest.builder()
                .organization(profile)
                .proposedChanges(proposedChanges)
                .build();
        organizationLegalChangeRequestRepository.save(legalChangeRequest);

        // Vào hàng đợi để Moderator duyệt (API 132); refId là id của chính yêu cầu vì mỗi
        // lần xin đổi là một việc riêng cần xem xét
        queueService.enqueue(QueueType.LEGAL_CHANGE, legalChangeRequest.getId().toString(), null);

        return LegalChangeRequestResponse.builder()
                .id(legalChangeRequest.getId().toString())
                .status(legalChangeRequest.getStatus().name())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public LegalChangeRequestDetailResponse getLatestLegalChangeRequest(UUID accountId) {
        OrganizationLegalChangeRequest r = organizationLegalChangeRequestRepository
                .findFirstByOrganization_AccountIdOrderByCreatedAtDesc(accountId)
                .orElseThrow(() -> new IllegalArgumentException(LEGAL_CHANGE_REQUEST_NOT_FOUND));

        return toLegalChangeDetailResponse(r);
    }

    @Override
    @Transactional(readOnly = true)
    public LegalChangeRequestDetailResponse getLegalChangeRequestDetail(UUID requestId) {
        OrganizationLegalChangeRequest r = organizationLegalChangeRequestRepository
                .findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException(LEGAL_CHANGE_REQUEST_NOT_FOUND));

        return toLegalChangeDetailResponse(r);
    }

    private static LegalChangeRequestDetailResponse toLegalChangeDetailResponse(OrganizationLegalChangeRequest r) {
        return LegalChangeRequestDetailResponse.builder()
                .id(r.getId().toString())
                .status(r.getStatus().name())
                .proposedChanges(r.getProposedChanges())
                .rejectReason(r.getRejectReason())
                .createdAt(r.getCreatedAt())
                .decidedAt(r.getReviewedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OrganizationVerificationResponse getVerificationDetail(UUID organizationId) {
        // OrganizationProfile dùng @MapsId nên organizationId chính là khóa chính
        return organizationProfileRepository.findById(organizationId)
                .map(OrganizationVerificationResponse::from)
                .orElseThrow(() -> new IllegalArgumentException(ORGANIZATION_NOT_FOUND));
    }

    @Override
    @Transactional
    public OrganizationVerifyResponse verifyOrganization(Account moderator, UUID organizationId) {
        OrganizationProfile profile = organizationProfileRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException(ORGANIZATION_NOT_FOUND));

        // Duyệt lại hồ sơ đã VERIFIED thì giữ nguyên người và mốc đã kết luận trước đó
        if (profile.getVerificationStatus() == VerificationStatus.VERIFIED) {
            return toVerifyResponse(profile);
        }

        requireCompleteDossier(profile);

        profile.setVerificationStatus(VerificationStatus.VERIFIED);
        profile.setVerifiedBy(moderator);
        profile.setVerifiedAt(Instant.now());
        profile.setRejectionReason(null);
        organizationProfileRepository.save(profile);

        // Đóng việc trong hàng đợi để Moderator khác không nhận lại hồ sơ này
        queueService.dequeue(QueueType.ORG_VERIFICATION, organizationId.toString());

        notificationService.notify(
                profile.getAccount(),
                NotificationType.VERIFICATION_RESULT,
                "Hồ sơ đã được xác minh",
                "Hồ sơ tổ chức của bạn đã được xác minh. Bạn có thể bắt đầu sử dụng đầy đủ các chức năng.",
                NotificationTargetType.ORGANIZATION,
                profile.getAccountId(),
                null);

        return toVerifyResponse(profile);
    }

    @Override
    @Transactional
    public OrganizationRejectResponse rejectOrganization(Account moderator, UUID organizationId,
                                                          RejectOrganizationRequest request) {
        if (!StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("reason là bắt buộc.");
        }

        OrganizationProfile profile = organizationProfileRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException(ORGANIZATION_NOT_FOUND));

        applyDocumentDecisions(profile, request.getDocuments());

        Instant now = Instant.now();
        // verifiedBy/verifiedAt là "ai kết luận, lúc nào" nên từ chối cũng đóng dấu; rejectedAt
        // tách riêng vì verifiedAt sẽ bị ghi đè ở lần xác minh/nộp lại kế tiếp (mobile cần mốc
        // từ chối ổn định để hiển thị lịch sử)
        profile.setVerificationStatus(VerificationStatus.REJECTED);
        profile.setRejectionReason(request.getReason());
        profile.setVerifiedBy(moderator);
        profile.setVerifiedAt(now);
        profile.setRejectedAt(now);
        organizationProfileRepository.save(profile);

        // Việc đã kết luận; tổ chức nộp lại (F-003) sẽ tự enqueue lại
        queueService.dequeue(QueueType.ORG_VERIFICATION, organizationId.toString());

        notificationService.notify(
                profile.getAccount(),
                NotificationType.VERIFICATION_RESULT,
                "Hồ sơ bị từ chối",
                "Hồ sơ tổ chức của bạn đã bị từ chối: " + request.getReason(),
                NotificationTargetType.ORGANIZATION,
                profile.getAccountId(),
                null);

        return OrganizationRejectResponse.builder()
                .id(profile.getAccountId().toString())
                .verificationStatus(profile.getVerificationStatus())
                .rejectionReason(profile.getRejectionReason())
                .rejectedAt(profile.getRejectedAt())
                .build();
    }

    /**
     * Chấm từng giấy tờ đạt/chưa đạt khi từ chối hồ sơ, để tổ chức biết đúng giấy tờ nào
     * cần sửa thay vì chỉ có 1 lý do chung cho cả hồ sơ.
     */
    private void applyDocumentDecisions(OrganizationProfile profile,
                                        List<RejectOrganizationRequest.DocumentDecision> decisions) {
        if (decisions == null || decisions.isEmpty()) {
            return;
        }

        Map<UUID, OrganizationDocument> documentsById = profile.getDocuments().stream()
                .collect(Collectors.toMap(OrganizationDocument::getId, d -> d));

        for (RejectOrganizationRequest.DocumentDecision decision : decisions) {
            OrganizationDocument document = documentsById.get(decision.getDocumentId());
            if (document == null) {
                throw new BadRequestException(
                        "Không tìm thấy giấy tờ " + decision.getDocumentId() + " trong hồ sơ này.");
            }
            document.setAccepted(decision.getAccepted());
        }
    }

    @Override
    @Transactional
    public LegalChangeRequestResponse approveLegalChange(Account moderator, UUID requestId) {
        OrganizationLegalChangeRequest changeRequest = requirePendingRequest(requestId);
        OrganizationProfile profile = changeRequest.getOrganization();

        boolean imagesReplaced = organizationLegalChangeMapper.apply(
                changeRequest.getProposedChanges(), profile);

        // Mã số thuế là định danh duy nhất giữa các tổ chức, đổi sang mã của tổ chức khác thì chặn
        if (StringUtils.hasText(profile.getTaxCode())
                && organizationProfileRepository.existsByTaxCodeAndAccountIdNot(
                        profile.getTaxCode(), profile.getAccountId())) {
            throw new AppException(ErrorCode.ORGANIZATION_TAX_CODE_TAKEN);
        }

        Instant now = Instant.now();
        if (imagesReplaced) {
            // F-139 tính thời hạn giữ ảnh từ verifiedAt. Không đặt lại mốc thì ảnh giấy tờ vừa
            // nộp sẽ bị job dọn xóa ngay, vì mốc đang là của lần xác minh trước.
            profile.setVerifiedBy(moderator);
            profile.setVerifiedAt(now);
        }
        organizationProfileRepository.save(profile);

        changeRequest.setStatus(LegalChangeRequestStatus.APPROVED);
        changeRequest.setReviewedBy(moderator);
        changeRequest.setReviewedAt(now);
        organizationLegalChangeRequestRepository.save(changeRequest);

        queueService.dequeue(QueueType.LEGAL_CHANGE, requestId.toString());

        notificationService.notify(
                profile.getAccount(),
                NotificationType.VERIFICATION_RESULT,
                "Yêu cầu đổi thông tin pháp lý đã được duyệt",
                "Yêu cầu đổi thông tin pháp lý của bạn đã được duyệt và áp dụng vào hồ sơ.",
                NotificationTargetType.LEGAL_CHANGE,
                changeRequest.getId(),
                null);

        return toLegalChangeResponse(changeRequest);
    }

    @Override
    @Transactional
    public LegalChangeRequestResponse rejectLegalChange(Account moderator, UUID requestId,
                                                       RejectLegalChangeRequest request) {
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("reason là bắt buộc khi từ chối.");
        }

        OrganizationLegalChangeRequest changeRequest = requirePendingRequest(requestId);

        // Hồ sơ không bị đụng tới: tổ chức vẫn giữ thông tin pháp lý cũ đã được xác minh
        changeRequest.setStatus(LegalChangeRequestStatus.REJECTED);
        changeRequest.setRejectReason(request.getReason());
        changeRequest.setReviewedBy(moderator);
        changeRequest.setReviewedAt(Instant.now());
        organizationLegalChangeRequestRepository.save(changeRequest);

        queueService.dequeue(QueueType.LEGAL_CHANGE, requestId.toString());

        notificationService.notify(
                changeRequest.getOrganization().getAccount(),
                NotificationType.VERIFICATION_RESULT,
                "Yêu cầu đổi thông tin pháp lý bị từ chối",
                "Yêu cầu đổi thông tin pháp lý của bạn đã bị từ chối: " + request.getReason(),
                NotificationTargetType.LEGAL_CHANGE,
                changeRequest.getId(),
                null);

        return toLegalChangeResponse(changeRequest);
    }

    /**
     * Yêu cầu đã kết luận thì không duyệt/từ chối lại được — nếu không, bấm hai lần sẽ áp
     * proposedChanges lần nữa lên hồ sơ (lúc này có thể đã đổi tiếp bằng yêu cầu khác).
     */
    private OrganizationLegalChangeRequest requirePendingRequest(UUID requestId) {
        OrganizationLegalChangeRequest changeRequest = organizationLegalChangeRequestRepository
                .findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException(LEGAL_CHANGE_REQUEST_NOT_FOUND));

        if (changeRequest.getStatus() != LegalChangeRequestStatus.PENDING) {
            throw new ConflictException(
                    "ALREADY_REVIEWED: yêu cầu đã được " + changeRequest.getStatus().name() + ".",
                    "ALREADY_REVIEWED");
        }
        return changeRequest;
    }

    private static LegalChangeRequestResponse toLegalChangeResponse(OrganizationLegalChangeRequest r) {
        return LegalChangeRequestResponse.builder()
                .id(r.getId().toString())
                .status(r.getStatus().name())
                .build();
    }

    /**
     * Phần checklist (F-137) mà server kiểm được từ dữ liệu: đủ số giấy tờ theo loại tổ chức
     * và có ảnh CCCD người đại diện để đối chiếu tên. Các bước phán đoán của con người
     * (gọi điện, tra mã số thuế, soi ảnh) thì FE chặn trước khi cho bấm Xác thực.
     */
    private void requireCompleteDossier(OrganizationProfile profile) {
        int requiredDocuments = OrganizationVerificationResponse.requiredDocumentCount(profile.getOrgType());
        int submittedDocuments = profile.getDocuments() == null ? 0 : profile.getDocuments().size();

        OrganizationRepresentative representative = profile.getRepresentative();
        boolean hasCccdImages = representative != null
                && StringUtils.hasText(representative.getCccdFrontUrl())
                && StringUtils.hasText(representative.getCccdBackUrl());

        if (submittedDocuments < requiredDocuments || !hasCccdImages) {
            throw new ConflictException(
                    "CHECKLIST_INCOMPLETE: chưa hoàn tất checklist xác minh.",
                    "CHECKLIST_INCOMPLETE");
        }
    }

    private static OrganizationVerifyResponse toVerifyResponse(OrganizationProfile profile) {
        return OrganizationVerifyResponse.builder()
                .id(profile.getAccountId().toString())
                .verificationStatus(profile.getVerificationStatus())
                .verifiedBy(profile.getVerifiedBy() == null ? null : profile.getVerifiedBy().getId().toString())
                .verifiedAt(profile.getVerifiedAt())
                .build();
    }

}
