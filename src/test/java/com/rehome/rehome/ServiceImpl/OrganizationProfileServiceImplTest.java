package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.OrganizationResubmitResponse;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.VerificationStatus;
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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationProfileServiceImplTest {

    @Mock private OrganizationProfileRepository organizationProfileRepository;
    @Mock private OrganizationProfileMapper organizationProfileMapper;
    @Mock private AccountRepository accountRepository;

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
}