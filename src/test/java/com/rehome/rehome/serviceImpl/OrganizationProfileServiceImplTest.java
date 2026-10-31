package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.OrganizationProfileResponse;
import com.rehome.rehome.dto.response.OrganizationResubmitResponse;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.mapper.OrganizationProfileMapper;
import com.rehome.rehome.repository.AccountRepository;
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
}