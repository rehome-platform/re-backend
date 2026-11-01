package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.rehome.rehome.dto.request.CreateLegalChangeRequestRequest;
import com.rehome.rehome.dto.request.RejectOrganizationRequest;
import com.rehome.rehome.dto.request.UpdateOrganizationProfileRequest;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.LegalChangeRequestResponse;
import com.rehome.rehome.dto.response.OrganizationOptionsResponse;
import com.rehome.rehome.dto.response.OrganizationProfileResponse;
import com.rehome.rehome.dto.response.OrganizationProfileUpdateResponse;
import com.rehome.rehome.dto.response.OrganizationRejectResponse;
import com.rehome.rehome.dto.response.OrganizationPublicProfileResponse;
import com.rehome.rehome.dto.response.OrganizationResubmitResponse;
import com.rehome.rehome.dto.response.OrganizationVerificationResponse;
import com.rehome.rehome.dto.response.OrganizationVerifyResponse;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationLegalChangeRequest;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.auth.OrganizationRepresentative;
import com.rehome.rehome.entity.marketplace.Follow;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.OrganizationProfileMapper;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.FollowRepository;
import com.rehome.rehome.repository.OrganizationLegalChangeRequestRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationServiceImpl implements OrganizationService {

    private static final String ORGANIZATION_NOT_FOUND = "Không tìm thấy tổ chức.";

    private final OrganizationProfileRepository organizationProfileRepository;
    private final OrganizationProfileMapper organizationProfileMapper;
    private final AccountRepository accountRepository;
    private final QueueService queueService;
    private final FollowRepository followRepository;
    private final OrganizationLegalChangeRequestRepository organizationLegalChangeRequestRepository;

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
    public OrganizationPublicProfileResponse getPublicProfile(UUID organizationId, UUID viewerId) {
        OrganizationProfile profile = organizationProfileRepository.findById(organizationId)
                .filter(p -> p.getVerificationStatus() == VerificationStatus.VERIFIED)
                .orElseThrow(() -> new IllegalArgumentException(ORGANIZATION_NOT_FOUND));

        boolean isFollowing = viewerId != null
                && followRepository.existsByMember_IdAndOrganization_AccountId(viewerId, organizationId);

        return OrganizationPublicProfileResponse.builder()
                .id(profile.getAccountId().toString())
                .displayName(profile.getDisplayName())
                .description(profile.getDescription())
                .isFollowing(isFollowing)
                .build();
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

        // verifiedBy/verifiedAt là "ai kết luận, lúc nào" nên từ chối cũng đóng dấu
        profile.setVerificationStatus(VerificationStatus.REJECTED);
        profile.setRejectionReason(request.getReason());
        profile.setVerifiedBy(moderator);
        profile.setVerifiedAt(Instant.now());
        organizationProfileRepository.save(profile);

        // Việc đã kết luận; tổ chức nộp lại (F-003) sẽ tự enqueue lại
        queueService.dequeue(QueueType.ORG_VERIFICATION, organizationId.toString());

        return OrganizationRejectResponse.builder()
                .id(profile.getAccountId().toString())
                .verificationStatus(profile.getVerificationStatus())
                .rejectionReason(profile.getRejectionReason())
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
