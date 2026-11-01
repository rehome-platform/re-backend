package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.NullNode;
import com.rehome.rehome.dto.request.CreateLegalChangeRequestRequest;
import com.rehome.rehome.dto.request.RejectOrganizationRequest;
import com.rehome.rehome.dto.request.UpdateOrganizationProfileRequest;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.LegalChangeRequestResponse;
import com.rehome.rehome.dto.response.OrganizationProfileResponse;
import com.rehome.rehome.dto.response.OrganizationProfileUpdateResponse;
import com.rehome.rehome.dto.response.OrganizationPublicProfileResponse;
import com.rehome.rehome.dto.response.OrganizationRejectResponse;
import com.rehome.rehome.dto.response.OrganizationVerificationResponse;
import com.rehome.rehome.dto.response.OrganizationVerifyResponse;
import com.rehome.rehome.dto.response.OrganizationResubmitResponse;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationDocument;
import com.rehome.rehome.entity.auth.OrganizationLegalChangeRequest;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.auth.OrganizationRepresentative;
import com.rehome.rehome.entity.marketplace.Follow;
import com.rehome.rehome.entity.enums.LegalChangeRequestStatus;
import com.rehome.rehome.entity.enums.OrganizationType;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationProfileServiceImplTest {

    @Mock private OrganizationProfileRepository organizationProfileRepository;
    @Mock private OrganizationProfileMapper organizationProfileMapper;
    @Mock private AccountRepository accountRepository;
    @Mock private QueueService queueService;
    @Mock private FollowRepository followRepository;
    @Mock private OrganizationLegalChangeRequestRepository organizationLegalChangeRequestRepository;

    @InjectMocks private OrganizationServiceImpl service;

    private static final String RAW_EMAIL = "  Org@Mail.COM ";
    private static final String NORMALIZED_EMAIL = "org@mail.com";
    private static final String TAX_CODE = "0123456789";

    // ---------- helpers ----------

    private Account account(UUID id, Role role) {
        return Account.builder().id(id).role(role).build();
    }

    private RegisterOrganizationRequest request() {
        RegisterOrganizationRequest r = new RegisterOrganizationRequest();
        r.setContactEmail(RAW_EMAIL);
        r.setTaxCode(TAX_CODE);
        return r;
    }

    private void assertAppError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(expected));
    }

    // =====================================================================
    // registerOrganization
    // =====================================================================

    @Test
    void register_success() {
        UUID id = UUID.randomUUID();
        Account account = account(id, Role.ORGANIZATION);
        RegisterOrganizationRequest req = request();
        OrganizationProfile entity = new OrganizationProfile();
        RegisterOrganizationResponse response = new RegisterOrganizationResponse();

        when(accountRepository.findById(id)).thenReturn(Optional.of(account));
        when(organizationProfileRepository.existsByAccountId(id)).thenReturn(false);
        when(organizationProfileRepository.existsByContactEmailIgnoreCase(NORMALIZED_EMAIL)).thenReturn(false);
        when(organizationProfileRepository.existsByTaxCode(TAX_CODE)).thenReturn(false);
        when(organizationProfileMapper.toEntity(req)).thenReturn(entity);
        when(organizationProfileMapper.toResponse(entity)).thenReturn(response);

        RegisterOrganizationResponse result = service.registerOrganization(id, req);

        assertThat(result).isSameAs(response);

        ArgumentCaptor<OrganizationProfile> captor = ArgumentCaptor.forClass(OrganizationProfile.class);
        verify(organizationProfileRepository).save(captor.capture());
        OrganizationProfile saved = captor.getValue();

        assertThat(saved.getAccount()).isSameAs(account);
        assertThat(saved.getContactEmail()).isEqualTo(NORMALIZED_EMAIL);
        assertThat(saved.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        verify(queueService).enqueue(QueueType.ORG_VERIFICATION, id.toString(), null);
    }

    @Test
    void register_accountNotFound_throwsUnauthorized() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.empty());

        assertAppError(() -> service.registerOrganization(id, request()), ErrorCode.UNAUTHORIZED);

        verifyNoInteractions(organizationProfileRepository, organizationProfileMapper);
    }

    @Test
    void register_accountIsNotOrganization_throws() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.of(account(id, Role.MEMBER)));

        assertAppError(() -> service.registerOrganization(id, request()), ErrorCode.NOT_ORGANIZATION_ACCOUNT);

        verifyNoInteractions(organizationProfileRepository, organizationProfileMapper);
    }

    @Test
    void register_profileAlreadyExists_throws() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.of(account(id, Role.ORGANIZATION)));
        when(organizationProfileRepository.existsByAccountId(id)).thenReturn(true);

        assertAppError(() -> service.registerOrganization(id, request()),
                ErrorCode.ORGANIZATION_PROFILE_ALREADY_EXISTS);

        verify(organizationProfileRepository, never()).save(any());
    }

    @Test
    void register_emailTaken_throws() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.of(account(id, Role.ORGANIZATION)));
        when(organizationProfileRepository.existsByAccountId(id)).thenReturn(false);
        when(organizationProfileRepository.existsByContactEmailIgnoreCase(NORMALIZED_EMAIL)).thenReturn(true);

        assertAppError(() -> service.registerOrganization(id, request()),
                ErrorCode.ORGANIZATION_EMAIL_TAKEN);

        verify(organizationProfileRepository, never()).save(any());
    }

    @Test
    void register_taxCodeTaken_throws() {
        UUID id = UUID.randomUUID();
        when(accountRepository.findById(id)).thenReturn(Optional.of(account(id, Role.ORGANIZATION)));
        when(organizationProfileRepository.existsByAccountId(id)).thenReturn(false);
        when(organizationProfileRepository.existsByContactEmailIgnoreCase(NORMALIZED_EMAIL)).thenReturn(false);
        when(organizationProfileRepository.existsByTaxCode(TAX_CODE)).thenReturn(true);

        assertAppError(() -> service.registerOrganization(id, request()),
                ErrorCode.ORGANIZATION_TAX_CODE_TAKEN);

        verify(organizationProfileRepository, never()).save(any());
    }

    // =====================================================================
    // resubmit
    // =====================================================================

    @Test
    void resubmit_success() {
        UUID id = UUID.randomUUID();
        Account account = account(id, Role.ORGANIZATION);
        RegisterOrganizationRequest req = request();

        OrganizationProfile profile = new OrganizationProfile();
        profile.setVerificationStatus(VerificationStatus.REJECTED);

        when(organizationProfileRepository.findByAccountId(id)).thenReturn(Optional.of(profile));
        when(organizationProfileRepository.existsByContactEmailIgnoreCaseAndAccountIdNot(NORMALIZED_EMAIL, id))
                .thenReturn(false);
        when(organizationProfileRepository.existsByTaxCodeAndAccountIdNot(TAX_CODE, id)).thenReturn(false);

        OrganizationResubmitResponse result = service.resubmit(account, req);

        assertThat(result).isNotNull();
        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        verify(organizationProfileMapper).updateEntity(req, profile);
        verify(organizationProfileRepository).save(profile);
        verify(queueService).enqueue(QueueType.ORG_VERIFICATION, id.toString(), null);
    }

    @Test
    void resubmit_profileNotFound_throws() {
        UUID id = UUID.randomUUID();
        when(organizationProfileRepository.findByAccountId(id)).thenReturn(Optional.empty());

        assertAppError(() -> service.resubmit(account(id, Role.ORGANIZATION), request()),
                ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND);

        verifyNoInteractions(organizationProfileMapper);
        verify(organizationProfileRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "REJECTED")
    void resubmit_statusNotRejected_throws(VerificationStatus status) {
        UUID id = UUID.randomUUID();
        OrganizationProfile profile = new OrganizationProfile();
        profile.setVerificationStatus(status);

        when(organizationProfileRepository.findByAccountId(id)).thenReturn(Optional.of(profile));

        assertAppError(() -> service.resubmit(account(id, Role.ORGANIZATION), request()),
                ErrorCode.ORGANIZATION_NOT_REJECTED);

        assertThat(profile.getVerificationStatus()).isEqualTo(status);   // không bị đổi
        verifyNoInteractions(organizationProfileMapper);
        verify(organizationProfileRepository, never()).save(any());
    }

    @Test
    void resubmit_emailTakenByAnother_throws() {
        UUID id = UUID.randomUUID();
        OrganizationProfile profile = new OrganizationProfile();
        profile.setVerificationStatus(VerificationStatus.REJECTED);

        when(organizationProfileRepository.findByAccountId(id)).thenReturn(Optional.of(profile));
        when(organizationProfileRepository.existsByContactEmailIgnoreCaseAndAccountIdNot(NORMALIZED_EMAIL, id))
                .thenReturn(true);

        assertAppError(() -> service.resubmit(account(id, Role.ORGANIZATION), request()),
                ErrorCode.ORGANIZATION_EMAIL_TAKEN);

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        verifyNoInteractions(organizationProfileMapper);
        verify(organizationProfileRepository, never()).save(any());
    }

    @Test
    void resubmit_taxCodeTakenByAnother_throws() {
        UUID id = UUID.randomUUID();
        OrganizationProfile profile = new OrganizationProfile();
        profile.setVerificationStatus(VerificationStatus.REJECTED);

        when(organizationProfileRepository.findByAccountId(id)).thenReturn(Optional.of(profile));
        when(organizationProfileRepository.existsByContactEmailIgnoreCaseAndAccountIdNot(NORMALIZED_EMAIL, id))
                .thenReturn(false);
        when(organizationProfileRepository.existsByTaxCodeAndAccountIdNot(TAX_CODE, id)).thenReturn(true);

        assertAppError(() -> service.resubmit(account(id, Role.ORGANIZATION), request()),
                ErrorCode.ORGANIZATION_TAX_CODE_TAKEN);

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        verifyNoInteractions(organizationProfileMapper);
        verify(organizationProfileRepository, never()).save(any());
    }

    // =====================================================================
    // getMyProfile  (MỚI)
    // =====================================================================

    private OrganizationProfile.OrganizationProfileBuilder baseProfile(UUID id) {
        return OrganizationProfile.builder()
                .accountId(id)
                .legalName("Hội Chia Sẻ")
                .displayName("Chia Sẻ")
                .orgType("NGO")
                .taxCode(TAX_CODE)
                .operationScope("PROVINCE")
                .address("1 Lê Lợi, Q1, TP.HCM")
                .contactPhone("0900000000")
                .contactEmail(NORMALIZED_EMAIL)
                .website("https://chiase.org")
                .description("Tổ chức thiện nguyện")
                .logoUrl("https://cdn/logo.png")
                .donationPointName("Điểm nhận Q1")
                .donationPointAddress("2 Nguyễn Huệ, Q1, TP.HCM")
                .donationPointLatitude(new BigDecimal("10.7769000"))
                .donationPointLongitude(new BigDecimal("106.7009000"))
                .donationPointReceivingHours("8:00-17:00 T2-T6")
                .donationPointContactPhone("0911111111");
    }

    @Test
    void getMyProfile_notSubmitted_returnsAllNull() {
        UUID id = UUID.randomUUID();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.empty());

        OrganizationProfileResponse res = service.getMyProfile(id);

        assertThat(res).isNotNull();
        assertThat(res.getVerificationStatus()).isNull();
        assertThat(res).hasAllNullFieldsOrProperties();
    }

    @Test
    void getMyProfile_pending_returnsSubmittedProfile() {
        UUID id = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(id)
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(profile));

        OrganizationProfileResponse res = service.getMyProfile(id);

        assertThat(res.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        assertThat(res.getRejectionReason()).isNull();
        assertThat(res.getVerifiedAt()).isNull();
        assertThat(res).extracting("displayName", "logoUrl", "legalName", "orgType", "taxCode",
                        "operationScope", "address", "contactPhone", "contactEmail", "website", "description")
                .containsExactly("Chia Sẻ", "https://cdn/logo.png", "Hội Chia Sẻ", "NGO", TAX_CODE,
                        "PROVINCE", "1 Lê Lợi, Q1, TP.HCM", "0900000000", NORMALIZED_EMAIL,
                        "https://chiase.org", "Tổ chức thiện nguyện");
    }

    @Test
    void getMyProfile_returnsDonationPointInfo() {
        UUID id = UUID.randomUUID();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(baseProfile(id).build()));

        OrganizationProfileResponse res = service.getMyProfile(id);

        assertThat(res.getDonationPointName()).isEqualTo("Điểm nhận Q1");
        assertThat(res.getDonationPointAddress()).isEqualTo("2 Nguyễn Huệ, Q1, TP.HCM");
        assertThat(res.getDonationPointLatitude()).isEqualByComparingTo("10.7769000");
        assertThat(res.getDonationPointLongitude()).isEqualByComparingTo("106.7009000");
        assertThat(res.getDonationPointReceivingHours()).isEqualTo("8:00-17:00 T2-T6");
        assertThat(res.getDonationPointContactPhone()).isEqualTo("0911111111");
    }

    @Test
    void getMyProfile_rejected_returnsRejectionReason() {
        UUID id = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(id)
                .verificationStatus(VerificationStatus.REJECTED)
                .rejectionReason("Ảnh giấy tờ bị mờ")
                .build();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(profile));

        OrganizationProfileResponse res = service.getMyProfile(id);

        assertThat(res.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(res.getRejectionReason()).isEqualTo("Ảnh giấy tờ bị mờ");
    }

    @Test
    void getMyProfile_verified_returnsVerifiedAt() {
        UUID id = UUID.randomUUID();
        Instant verifiedAt = Instant.parse("2026-10-01T00:00:00Z");
        OrganizationProfile profile = baseProfile(id)
                .verificationStatus(VerificationStatus.VERIFIED)
                .verifiedAt(verifiedAt)
                .build();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(profile));

        OrganizationProfileResponse res = service.getMyProfile(id);

        assertThat(res.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(res.getVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(res.getRejectionReason()).isNull();
    }

    @Test
    void getMyProfile_optionalFieldsNull_stillMaps() {
        UUID id = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(id)
                .taxCode(null)
                .website(null)
                .logoUrl(null)
                .donationPointContactPhone(null)
                .build();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(profile));

        OrganizationProfileResponse res = service.getMyProfile(id);

        assertThat(res.getTaxCode()).isNull();
        assertThat(res.getWebsite()).isNull();
        assertThat(res.getLogoUrl()).isNull();
        assertThat(res.getDonationPointContactPhone()).isNull();
        assertThat(res.getDisplayName()).isEqualTo("Chia Sẻ");
    }

    @Test
    void getMyProfile_onlyQueriesByGivenAccountId() {
        UUID id = UUID.randomUUID();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.empty());

        service.getMyProfile(id);

        verify(organizationProfileRepository).findById(id);
        verifyNoMoreInteractions(organizationProfileRepository);
        verifyNoInteractions(accountRepository, organizationProfileMapper);
    }

    // =====================================================================
    // getPublicProfile  (F-082)
    // =====================================================================

    @Test
    void getPublicProfile_verified_guest_returnsProfileWithFollowingFalse() {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(orgId)
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));

        OrganizationPublicProfileResponse res = service.getPublicProfile(orgId, null);

        assertThat(res.getId()).isEqualTo(orgId.toString());
        assertThat(res.getDisplayName()).isEqualTo("Chia Sẻ");
        assertThat(res.getDescription()).isEqualTo("Tổ chức thiện nguyện");
        assertThat(res.isFollowing()).isFalse();
        verifyNoInteractions(followRepository);
    }

    @Test
    void getPublicProfile_verified_memberFollowing_returnsTrue() {
        UUID orgId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(orgId)
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));
        when(followRepository.existsByMember_IdAndOrganization_AccountId(viewerId, orgId)).thenReturn(true);

        OrganizationPublicProfileResponse res = service.getPublicProfile(orgId, viewerId);

        assertThat(res.isFollowing()).isTrue();
    }

    @Test
    void getPublicProfile_verified_memberNotFollowing_returnsFalse() {
        UUID orgId = UUID.randomUUID();
        UUID viewerId = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(orgId)
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));
        when(followRepository.existsByMember_IdAndOrganization_AccountId(viewerId, orgId)).thenReturn(false);

        OrganizationPublicProfileResponse res = service.getPublicProfile(orgId, viewerId);

        assertThat(res.isFollowing()).isFalse();
    }

    @Test
    void getPublicProfile_notFound_throws() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicProfile(orgId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy tổ chức.");

        verifyNoInteractions(followRepository);
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "VERIFIED")
    void getPublicProfile_notVerified_throws(VerificationStatus status) {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(orgId)
                .verificationStatus(status)
                .build();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.getPublicProfile(orgId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy tổ chức.");

        verifyNoInteractions(followRepository);
    }

    // =====================================================================
    // follow / unfollow  (F-085)
    // =====================================================================

    @Test
    void follow_notYetFollowing_savesNewFollow() {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(orgId).verificationStatus(VerificationStatus.VERIFIED).build();
        Account member = account(memberId, Role.MEMBER);

        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));
        when(followRepository.existsByMember_IdAndOrganization_AccountId(memberId, orgId)).thenReturn(false);
        when(accountRepository.findById(memberId)).thenReturn(Optional.of(member));

        service.follow(memberId, orgId);

        ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);
        verify(followRepository).save(captor.capture());
        assertThat(captor.getValue().getMember()).isSameAs(member);
        assertThat(captor.getValue().getOrganization()).isSameAs(profile);
    }

    @Test
    void follow_alreadyFollowing_isIdempotent_doesNotSaveAgain() {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(orgId).verificationStatus(VerificationStatus.VERIFIED).build();

        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));
        when(followRepository.existsByMember_IdAndOrganization_AccountId(memberId, orgId)).thenReturn(true);

        service.follow(memberId, orgId);

        verify(followRepository, never()).save(any());
        verifyNoInteractions(accountRepository);
    }

    @Test
    void follow_organizationNotFound_throws() {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.follow(memberId, orgId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy tổ chức.");

        verifyNoInteractions(followRepository, accountRepository);
    }

    @ParameterizedTest
    @EnumSource(value = VerificationStatus.class, mode = EnumSource.Mode.EXCLUDE, names = "VERIFIED")
    void follow_organizationNotVerified_throws(VerificationStatus status) {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(orgId).verificationStatus(status).build();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.follow(memberId, orgId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy tổ chức.");

        verifyNoInteractions(followRepository, accountRepository);
    }

    @Test
    void unfollow_existingFollow_deletesIt() {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        Follow follow = Follow.builder().id(UUID.randomUUID()).build();

        when(organizationProfileRepository.existsById(orgId)).thenReturn(true);
        when(followRepository.findByMember_IdAndOrganization_AccountId(memberId, orgId))
                .thenReturn(Optional.of(follow));

        service.unfollow(memberId, orgId);

        verify(followRepository).delete(follow);
    }

    @Test
    void unfollow_notFollowing_isIdempotent_doesNotThrow() {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();

        when(organizationProfileRepository.existsById(orgId)).thenReturn(true);
        when(followRepository.findByMember_IdAndOrganization_AccountId(memberId, orgId))
                .thenReturn(Optional.empty());

        service.unfollow(memberId, orgId);

        verify(followRepository, never()).delete(any());
    }

    @Test
    void unfollow_organizationNotFound_throws() {
        UUID orgId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        when(organizationProfileRepository.existsById(orgId)).thenReturn(false);

        assertThatThrownBy(() -> service.unfollow(memberId, orgId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy tổ chức.");

        verifyNoInteractions(followRepository);
    }

    // =====================================================================
    // updateMyProfile  (F-096)
    // =====================================================================

    @Test
    void updateMyProfile_onlyProvidedFieldChanges() {
        UUID id = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(id).build();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(profile));

        OrganizationProfileUpdateResponse res = service.updateMyProfile(id,
                new UpdateOrganizationProfileRequest(null, "Mô tả mới", null, null));

        assertThat(profile.getDescription()).isEqualTo("Mô tả mới");
        // Các field không truyền phải giữ nguyên
        assertThat(profile.getLogoUrl()).isEqualTo("https://cdn/logo.png");
        assertThat(profile.getContactPhone()).isEqualTo("0900000000");
        assertThat(profile.getDonationPointReceivingHours()).isEqualTo("8:00-17:00 T2-T6");

        assertThat(res.getId()).isEqualTo(id.toString());
        assertThat(res.getDescription()).isEqualTo("Mô tả mới");
        verify(organizationProfileRepository).save(profile);
    }

    @Test
    void updateMyProfile_allFields_areUpdated() {
        UUID id = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(id).build();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(profile));

        OrganizationProfileUpdateResponse res = service.updateMyProfile(id,
                new UpdateOrganizationProfileRequest(
                        "https://cdn/logo-v2.png", "Mô tả mới", "0988888888", "9:00-18:00 T2-T7"));

        assertThat(res.getLogoUrl()).isEqualTo("https://cdn/logo-v2.png");
        assertThat(res.getDescription()).isEqualTo("Mô tả mới");
        assertThat(res.getContactPhone()).isEqualTo("0988888888");
        assertThat(res.getDonationPointReceivingHours()).isEqualTo("9:00-18:00 T2-T7");
    }

    @Test
    void updateMyProfile_blankValues_doNotOverwrite() {
        UUID id = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(id).build();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(profile));

        // description/contactPhone/giờ nhận là NOT NULL -> chuỗi rỗng coi như không truyền
        service.updateMyProfile(id, new UpdateOrganizationProfileRequest("  ", "", "   ", ""));

        assertThat(profile.getDescription()).isEqualTo("Tổ chức thiện nguyện");
        assertThat(profile.getContactPhone()).isEqualTo("0900000000");
        assertThat(profile.getDonationPointReceivingHours()).isEqualTo("8:00-17:00 T2-T6");
        assertThat(profile.getLogoUrl()).isEqualTo("https://cdn/logo.png");
    }

    @Test
    void updateMyProfile_emptyRequest_keepsEverything() {
        UUID id = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(id).build();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(profile));

        OrganizationProfileUpdateResponse res = service.updateMyProfile(id,
                new UpdateOrganizationProfileRequest(null, null, null, null));

        assertThat(res.getDescription()).isEqualTo("Tổ chức thiện nguyện");
        assertThat(res.getContactPhone()).isEqualTo("0900000000");
    }

    @Test
    void updateMyProfile_doesNotTouchLegalOrVerificationFields() {
        UUID id = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(id)
                .verificationStatus(VerificationStatus.VERIFIED)
                .build();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(profile));

        service.updateMyProfile(id, new UpdateOrganizationProfileRequest(null, "Mô tả mới", null, null));

        // Thông tin pháp lý đi qua luồng duyệt riêng (F-097), không đổi ở đây
        assertThat(profile.getLegalName()).isEqualTo("Hội Chia Sẻ");
        assertThat(profile.getTaxCode()).isEqualTo(TAX_CODE);
        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
    }

    @Test
    void updateMyProfile_profileNotFound_throws() {
        UUID id = UUID.randomUUID();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.empty());

        assertAppError(() -> service.updateMyProfile(id,
                        new UpdateOrganizationProfileRequest(null, "Mô tả mới", null, null)),
                ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND);

        verify(organizationProfileRepository, never()).save(any());
    }

    // =====================================================================
    // requestLegalChange  (F-097)
    // =====================================================================

    private JsonNode proposedChanges() {
        return JsonNodeFactory.instance.objectNode()
                .put("legalName", "Quỹ Chia Sẻ Yêu Thương 2");
    }

    @Test
    void requestLegalChange_success_savesPendingAndEnqueuesForModerator() {
        UUID id = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(id).build();
        JsonNode changes = proposedChanges();

        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(profile));
        when(organizationLegalChangeRequestRepository.save(any())).thenAnswer(invocation -> {
            OrganizationLegalChangeRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        LegalChangeRequestResponse res = service.requestLegalChange(id,
                new CreateLegalChangeRequestRequest(changes));

        ArgumentCaptor<OrganizationLegalChangeRequest> captor =
                ArgumentCaptor.forClass(OrganizationLegalChangeRequest.class);
        verify(organizationLegalChangeRequestRepository).save(captor.capture());
        OrganizationLegalChangeRequest saved = captor.getValue();

        assertThat(saved.getOrganization()).isSameAs(profile);
        assertThat(saved.getProposedChanges()).isSameAs(changes);
        assertThat(saved.getStatus()).isEqualTo(LegalChangeRequestStatus.PENDING);

        assertThat(res.getId()).isEqualTo(saved.getId().toString());
        assertThat(res.getStatus()).isEqualTo("PENDING");

        // Mỗi yêu cầu là 1 việc riêng trong hàng đợi, refId là id của yêu cầu
        verify(queueService).enqueue(QueueType.LEGAL_CHANGE, saved.getId().toString(), null);
    }

    @Test
    void requestLegalChange_doesNotApplyChangesImmediately() {
        UUID id = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(id).build();

        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(profile));
        when(organizationLegalChangeRequestRepository.save(any())).thenAnswer(invocation -> {
            OrganizationLegalChangeRequest saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        service.requestLegalChange(id, new CreateLegalChangeRequestRequest(proposedChanges()));

        // Khác API 117: thông tin pháp lý chỉ đổi sau khi Moderator duyệt
        assertThat(profile.getLegalName()).isEqualTo("Hội Chia Sẻ");
        verify(organizationProfileRepository, never()).save(any());
    }

    @Test
    void requestLegalChange_nullProposedChanges_throwsBadRequest() {
        assertThatThrownBy(() -> service.requestLegalChange(UUID.randomUUID(),
                new CreateLegalChangeRequestRequest(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("proposedChanges là bắt buộc.");

        verifyNoInteractions(organizationProfileRepository, organizationLegalChangeRequestRepository,
                queueService);
    }

    @Test
    void requestLegalChange_emptyObject_throwsBadRequest() {
        assertThatThrownBy(() -> service.requestLegalChange(UUID.randomUUID(),
                new CreateLegalChangeRequestRequest(JsonNodeFactory.instance.objectNode())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("proposedChanges là bắt buộc.");

        verifyNoInteractions(organizationProfileRepository, organizationLegalChangeRequestRepository,
                queueService);
    }

    @Test
    void requestLegalChange_jsonNull_throwsBadRequest() {
        assertThatThrownBy(() -> service.requestLegalChange(UUID.randomUUID(),
                new CreateLegalChangeRequestRequest(NullNode.getInstance())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("proposedChanges là bắt buộc.");

        verifyNoInteractions(organizationProfileRepository, organizationLegalChangeRequestRepository,
                queueService);
    }

    @Test
    void requestLegalChange_profileNotFound_throws() {
        UUID id = UUID.randomUUID();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.empty());

        assertAppError(() -> service.requestLegalChange(id,
                        new CreateLegalChangeRequestRequest(proposedChanges())),
                ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND);

        verifyNoInteractions(organizationLegalChangeRequestRepository, queueService);
    }

    // =====================================================================
    // getVerificationDetail  (F-137)
    // =====================================================================

    private List<String> checklistItems(OrganizationVerificationResponse res) {
        return res.getChecklist().stream()
                .map(OrganizationVerificationResponse.ChecklistItem::getItem)
                .toList();
    }

    private List<String> requiredChecklistItems(OrganizationVerificationResponse res) {
        return res.getChecklist().stream()
                .filter(OrganizationVerificationResponse.ChecklistItem::isRequired)
                .map(OrganizationVerificationResponse.ChecklistItem::getItem)
                .toList();
    }

    @Test
    void getVerificationDetail_returnsProfileAndDocuments() {
        UUID id = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(id)
                .orgType(OrganizationType.CHARITY_FUND.name())
                .representative(new OrganizationRepresentative(
                        "Nguyễn Văn B", "Giám đốc", "079123456789",
                        "https://cdn/cccd-front.jpg", "https://cdn/cccd-back.jpg"))
                .build();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(profile));

        OrganizationVerificationResponse res = service.getVerificationDetail(id);

        assertThat(res.getOrgType()).isEqualTo("CHARITY_FUND");
        assertThat(res.getLegalName()).isEqualTo("Hội Chia Sẻ");
        assertThat(res.getTaxCode()).isEqualTo(TAX_CODE);
        assertThat(res.getRepresentative().getFullName()).isEqualTo("Nguyễn Văn B");
        // F-139: ảnh CCCD cần cho bước đối chiếu tên với giấy phép, nhưng response chỉ trả
        // đường dẫn endpoint có kiểm quyền — URL storage không được lọt ra ngoài
        assertThat(res.getRepresentative().getCccdFrontUrl())
                .isEqualTo("/api/organizations/" + id + "/legal-files/cccd-front");
        assertThat(res.getRepresentative().getCccdBackUrl())
                .isEqualTo("/api/organizations/" + id + "/legal-files/cccd-back");
        assertThat(res.getDocuments()).isEmpty();
    }

    @Test
    void getVerificationDetail_hidesStorageUrls_andNullsLinkWhenImageAlreadyDeleted() {
        UUID id = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(id)
                .orgType(OrganizationType.CHARITY_FUND.name())
                // Ảnh CCCD đã bị job F-139 xóa khi hết hạn giữ, giấy tờ thì còn
                .representative(new OrganizationRepresentative(
                        "Nguyễn Văn B", "Giám đốc", "079123456789", null, null))
                .build();
        profile.setDocuments(new ArrayList<>(List.of(OrganizationDocument.builder()
                .id(documentId)
                .docType("FUND_LICENSE")
                .fileUrl("https://res.cloudinary.com/demo/image/upload/v1/organization-documents/a.jpg")
                .build())));
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(profile));

        OrganizationVerificationResponse res = service.getVerificationDetail(id);

        assertThat(res.getRepresentative().getCccdFrontUrl()).isNull();
        assertThat(res.getRepresentative().getCccdBackUrl()).isNull();
        assertThat(res.getDocuments().get(0).getFileUrl())
                .isEqualTo("/api/organizations/" + id + "/legal-files/documents/" + documentId);
        assertThat(res.getDocuments().get(0).getFileUrl()).doesNotContain("cloudinary");
    }

    @Test
    void getVerificationDetail_charityFund_requiresFundLicence() {
        UUID id = UUID.randomUUID();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(
                baseProfile(id).orgType(OrganizationType.CHARITY_FUND.name()).build()));

        OrganizationVerificationResponse res = service.getVerificationDetail(id);

        assertThat(checklistItems(res))
                .contains("Giấy phép thành lập và công nhận điều lệ quỹ");
    }

    @Test
    void getVerificationDetail_schoolClub_requiresThreeHostEntityDocuments() {
        UUID id = UUID.randomUUID();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(
                baseProfile(id).orgType(OrganizationType.SCHOOL_CLUB.name()).build()));

        OrganizationVerificationResponse res = service.getVerificationDetail(id);

        // CLB trực thuộc phải mượn pháp nhân của chủ quản (OVERVIEW mục 9)
        assertThat(checklistItems(res)).contains(
                "Giấy tờ pháp nhân của tổ chức chủ quản",
                "Quyết định thành lập câu lạc bộ",
                "Văn bản cử người phụ trách");
    }

    @Test
    void getVerificationDetail_socialProtectionCenter_requiresTwoLicences() {
        UUID id = UUID.randomUUID();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(
                baseProfile(id).orgType(OrganizationType.SOCIAL_PROTECTION_CENTER.name()).build()));

        OrganizationVerificationResponse res = service.getVerificationDetail(id);

        assertThat(checklistItems(res)).contains(
                "Giấy chứng nhận đăng ký thành lập",
                "Giấy phép hoạt động");
    }

    @Test
    void getVerificationDetail_unknownOrgType_fallsBackToGenericChecklist() {
        UUID id = UUID.randomUUID();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(
                baseProfile(id).orgType("MA_LA_KHONG_CO_TRONG_ENUM").build()));

        OrganizationVerificationResponse res = service.getVerificationDetail(id);

        // orgType lưu dạng String nên mã lạ vẫn phải ra checklist dùng được
        assertThat(checklistItems(res)).contains("Giấy tờ thành lập do cơ quan có thẩm quyền cấp");
    }

    @Test
    void getVerificationDetail_withTaxCode_requiresTaxLookupNotPhoneCall() {
        UUID id = UUID.randomUUID();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(
                baseProfile(id).orgType(OrganizationType.NGO.name()).taxCode(TAX_CODE).build()));

        OrganizationVerificationResponse res = service.getVerificationDetail(id);

        assertThat(requiredChecklistItems(res)).contains(
                "Đối chiếu tên, địa chỉ, tình trạng hoạt động theo mã số thuế trên tracuunnt.gdt.gov.vn");
        assertThat(requiredChecklistItems(res)).doesNotContain(
                "Gọi điện hoặc gửi email tới số và địa chỉ công khai của tổ chức để xác nhận đúng họ nộp hồ sơ");
    }

    @Test
    void getVerificationDetail_withoutTaxCode_requiresPhoneCallNotTaxLookup() {
        UUID id = UUID.randomUUID();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(
                baseProfile(id).orgType(OrganizationType.SCHOOL_CLUB.name()).taxCode(null).build()));

        OrganizationVerificationResponse res = service.getVerificationDetail(id);

        // Không có MST thì trọng số dồn sang gọi điện xác nhận (OVERVIEW mục 9, dòng 549)
        assertThat(requiredChecklistItems(res)).contains(
                "Gọi điện hoặc gửi email tới số và địa chỉ công khai của tổ chức để xác nhận đúng họ nộp hồ sơ");
        assertThat(requiredChecklistItems(res)).doesNotContain(
                "Đối chiếu tên, địa chỉ, tình trạng hoạt động theo mã số thuế trên tracuunnt.gdt.gov.vn");
    }

    @Test
    void getVerificationDetail_mediaCheckIsOptional() {
        UUID id = UUID.randomUUID();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.of(
                baseProfile(id).orgType(OrganizationType.NGO.name()).build()));

        OrganizationVerificationResponse res = service.getVerificationDetail(id);

        assertThat(requiredChecklistItems(res)).doesNotContain(
                "Tìm tên tổ chức trên báo chí và mạng xã hội xem có dấu hiệu lừa đảo");
        assertThat(checklistItems(res)).contains(
                "Tìm tên tổ chức trên báo chí và mạng xã hội xem có dấu hiệu lừa đảo");
    }

    @Test
    void getVerificationDetail_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(organizationProfileRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVerificationDetail(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy tổ chức.");
    }

    // =====================================================================
    // verifyOrganization  (F-138)
    // =====================================================================

    private OrganizationRepresentative representativeWithCccd() {
        return new OrganizationRepresentative("Nguyễn Văn B", "Giám đốc", "079123456789",
                "https://cdn/cccd-front.jpg", "https://cdn/cccd-back.jpg");
    }

    private List<OrganizationDocument> documents(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> OrganizationDocument.builder()
                        .id(UUID.randomUUID())
                        .docType("OPERATING_LICENSE_" + i)
                        .fileUrl("https://cdn/licence-" + i + ".pdf")
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    // CHARITY_FUND cần 1 giấy tờ (OVERVIEW mục 9)
    private OrganizationProfile completeCharityFundProfile(UUID id, VerificationStatus status) {
        return baseProfile(id)
                .orgType(OrganizationType.CHARITY_FUND.name())
                .verificationStatus(status)
                .representative(representativeWithCccd())
                .documents(documents(1))
                .build();
    }

    @Test
    void verifyOrganization_completeDossier_marksVerified() {
        UUID orgId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        Account moderator = account(moderatorId, Role.MODERATOR);
        OrganizationProfile profile = completeCharityFundProfile(orgId, VerificationStatus.PENDING);
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));

        OrganizationVerifyResponse res = service.verifyOrganization(moderator, orgId);

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(profile.getVerifiedBy()).isSameAs(moderator);
        assertThat(profile.getVerifiedAt()).isNotNull();

        assertThat(res.getId()).isEqualTo(orgId.toString());
        assertThat(res.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(res.getVerifiedBy()).isEqualTo(moderatorId.toString());
        assertThat(res.getVerifiedAt()).isNotNull();

        verify(organizationProfileRepository).save(profile);
        // Đóng việc trong hàng đợi để Moderator khác không nhận lại hồ sơ
        verify(queueService).dequeue(QueueType.ORG_VERIFICATION, orgId.toString());
    }

    @Test
    void verifyOrganization_rejectedProfile_canBeVerifiedAndReasonCleared() {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = completeCharityFundProfile(orgId, VerificationStatus.REJECTED);
        profile.setRejectionReason("Ảnh giấy tờ bị mờ");
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));

        service.verifyOrganization(account(UUID.randomUUID(), Role.MODERATOR), orgId);

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.VERIFIED);
        assertThat(profile.getRejectionReason()).isNull();
    }

    @Test
    void verifyOrganization_alreadyVerified_keepsOriginalAuditor() {
        UUID orgId = UUID.randomUUID();
        UUID firstModeratorId = UUID.randomUUID();
        Instant originalVerifiedAt = Instant.parse("2026-10-01T00:00:00Z");
        OrganizationProfile profile = completeCharityFundProfile(orgId, VerificationStatus.VERIFIED);
        profile.setVerifiedBy(account(firstModeratorId, Role.MODERATOR));
        profile.setVerifiedAt(originalVerifiedAt);
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));

        OrganizationVerifyResponse res =
                service.verifyOrganization(account(UUID.randomUUID(), Role.MODERATOR), orgId);

        // Không đóng dấu lại: giữ nguyên ai đã kết luận và lúc nào
        assertThat(res.getVerifiedBy()).isEqualTo(firstModeratorId.toString());
        assertThat(res.getVerifiedAt()).isEqualTo(originalVerifiedAt);
        verify(organizationProfileRepository, never()).save(any());
        verifyNoInteractions(queueService);
    }

    @Test
    void verifyOrganization_missingDocuments_throwsChecklistIncomplete() {
        UUID orgId = UUID.randomUUID();
        // SCHOOL_CLUB cần 3 giấy tờ nhưng chỉ nộp 1
        OrganizationProfile profile = baseProfile(orgId)
                .orgType(OrganizationType.SCHOOL_CLUB.name())
                .verificationStatus(VerificationStatus.PENDING)
                .representative(representativeWithCccd())
                .documents(documents(1))
                .build();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.verifyOrganization(account(UUID.randomUUID(), Role.MODERATOR), orgId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("CHECKLIST_INCOMPLETE: chưa hoàn tất checklist xác minh.")
                .satisfies(e -> assertThat(((ConflictException) e).getCode())
                        .isEqualTo("CHECKLIST_INCOMPLETE"));

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.PENDING);
        verify(organizationProfileRepository, never()).save(any());
        verifyNoInteractions(queueService);
    }

    @Test
    void verifyOrganization_noDocumentsAtAll_throwsChecklistIncomplete() {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(orgId)
                .orgType(OrganizationType.CHARITY_FUND.name())
                .verificationStatus(VerificationStatus.PENDING)
                .representative(representativeWithCccd())
                .build();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.verifyOrganization(account(UUID.randomUUID(), Role.MODERATOR), orgId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("CHECKLIST_INCOMPLETE: chưa hoàn tất checklist xác minh.");
    }

    @Test
    void verifyOrganization_missingCccdImages_throwsChecklistIncomplete() {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(orgId)
                .orgType(OrganizationType.CHARITY_FUND.name())
                .verificationStatus(VerificationStatus.PENDING)
                // Thiếu ảnh CCCD thì không đối chiếu được tên với giấy phép
                .representative(new OrganizationRepresentative(
                        "Nguyễn Văn B", "Giám đốc", "079123456789", null, null))
                .documents(documents(1))
                .build();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.verifyOrganization(account(UUID.randomUUID(), Role.MODERATOR), orgId))
                .isInstanceOf(ConflictException.class)
                .hasMessage("CHECKLIST_INCOMPLETE: chưa hoàn tất checklist xác minh.");

        verify(organizationProfileRepository, never()).save(any());
    }

    @Test
    void verifyOrganization_notFound_throws() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyOrganization(account(UUID.randomUUID(), Role.MODERATOR), orgId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy tổ chức.");

        verifyNoInteractions(queueService);
    }

    // =====================================================================
    // rejectOrganization  (F-138)
    // =====================================================================

    @Test
    void rejectOrganization_success_storesReasonAndStamps() {
        UUID orgId = UUID.randomUUID();
        UUID moderatorId = UUID.randomUUID();
        Account moderator = account(moderatorId, Role.MODERATOR);
        OrganizationProfile profile = baseProfile(orgId)
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));

        OrganizationRejectResponse res = service.rejectOrganization(moderator, orgId,
                new RejectOrganizationRequest("Giấy phép hoạt động đã hết hạn"));

        assertThat(profile.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(profile.getRejectionReason()).isEqualTo("Giấy phép hoạt động đã hết hạn");
        // "ai kết luận, lúc nào" áp dụng cho cả từ chối
        assertThat(profile.getVerifiedBy()).isSameAs(moderator);
        assertThat(profile.getVerifiedAt()).isNotNull();

        assertThat(res.getId()).isEqualTo(orgId.toString());
        assertThat(res.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
        assertThat(res.getRejectionReason()).isEqualTo("Giấy phép hoạt động đã hết hạn");

        verify(organizationProfileRepository).save(profile);
        verify(queueService).dequeue(QueueType.ORG_VERIFICATION, orgId.toString());
    }

    @Test
    void rejectOrganization_doesNotNeedCompleteDossier() {
        UUID orgId = UUID.randomUUID();
        // Hồ sơ thiếu giấy tờ vẫn phải từ chối được — đó mới là lý do để từ chối
        OrganizationProfile profile = baseProfile(orgId)
                .orgType(OrganizationType.SCHOOL_CLUB.name())
                .verificationStatus(VerificationStatus.PENDING)
                .build();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));

        OrganizationRejectResponse res = service.rejectOrganization(
                account(UUID.randomUUID(), Role.MODERATOR), orgId,
                new RejectOrganizationRequest("Thiếu quyết định thành lập câu lạc bộ"));

        assertThat(res.getVerificationStatus()).isEqualTo(VerificationStatus.REJECTED);
    }

    @Test
    void rejectOrganization_rejectAgain_overwritesReason() {
        UUID orgId = UUID.randomUUID();
        OrganizationProfile profile = baseProfile(orgId)
                .verificationStatus(VerificationStatus.REJECTED)
                .rejectionReason("Lý do cũ")
                .build();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(profile));

        service.rejectOrganization(account(UUID.randomUUID(), Role.MODERATOR), orgId,
                new RejectOrganizationRequest("Lý do mới"));

        assertThat(profile.getRejectionReason()).isEqualTo("Lý do mới");
    }

    @Test
    void rejectOrganization_blankReason_throwsBadRequest() {
        assertThatThrownBy(() -> service.rejectOrganization(
                account(UUID.randomUUID(), Role.MODERATOR), UUID.randomUUID(),
                new RejectOrganizationRequest("   ")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("reason là bắt buộc.");

        verifyNoInteractions(organizationProfileRepository, queueService);
    }

    @Test
    void rejectOrganization_nullReason_throwsBadRequest() {
        assertThatThrownBy(() -> service.rejectOrganization(
                account(UUID.randomUUID(), Role.MODERATOR), UUID.randomUUID(),
                new RejectOrganizationRequest(null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("reason là bắt buộc.");

        verifyNoInteractions(organizationProfileRepository, queueService);
    }

    @Test
    void rejectOrganization_notFound_throws() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rejectOrganization(
                account(UUID.randomUUID(), Role.MODERATOR), orgId,
                new RejectOrganizationRequest("Giấy phép đã hết hạn")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy tổ chức.");

        verify(organizationProfileRepository, never()).save(any());
        verifyNoInteractions(queueService);
    }
}