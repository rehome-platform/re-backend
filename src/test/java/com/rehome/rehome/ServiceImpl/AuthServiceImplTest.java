package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.request.ChangePasswordRequest;
import com.rehome.rehome.dto.request.GoogleLoginRequest;
import com.rehome.rehome.dto.request.LoginRequest;
import com.rehome.rehome.dto.request.RegisterRequest;
import com.rehome.rehome.dto.request.organization.RegisterOrganizationRequest;
import com.rehome.rehome.dto.response.AuthResponse;
import com.rehome.rehome.dto.response.OtpResponse;
import com.rehome.rehome.dto.response.RegisterOrganizationResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.auth.RefreshToken;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.OtpPurpose;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.exception.*;
import com.rehome.rehome.mapper.OrganizationProfileMapper;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.security.GoogleTokenVerifier;
import com.rehome.rehome.security.JwtService;
import com.rehome.rehome.service.OtpService;
import com.rehome.rehome.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    @Mock AccountRepository accountRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtService jwtService;
    @Mock RefreshTokenService refreshTokenService;
    @Mock OtpService otpService;
    @Mock MemberProfileRepository memberProfileRepository;
    @Mock OrganizationProfileRepository organizationProfileRepository;
    @Mock OrganizationProfileMapper organizationProfileMapper;
    @Mock GoogleTokenVerifier googleTokenVerifier;

    @InjectMocks AuthServiceImpl service;

    // ==================== helper ====================

    private void assertAppError(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", expected);
    }

    // ==================== register ====================

    @Test
    void register_emailExists_throws() {
        RegisterRequest req = mock(RegisterRequest.class);
        when(req.getEmail()).thenReturn("a@x.com");
        when(accountRepository.existsByEmail("a@x.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register(req))
                .isInstanceOf(AccountAlreadyExistsException.class)
                .hasMessage("Email đã tồn tại: a@x.com");

        verify(accountRepository, never()).save(any());
        verifyNoInteractions(memberProfileRepository, otpService);
    }

    @Test
    void register_success() {
        RegisterRequest req = mock(RegisterRequest.class);
        when(req.getEmail()).thenReturn("a@x.com");
        when(req.getPassword()).thenReturn("pw");
        when(req.getPhone()).thenReturn("0909");
        when(req.getFullName()).thenReturn("Nguyen A");
        when(accountRepository.existsByEmail("a@x.com")).thenReturn(false);
        when(passwordEncoder.encode("pw")).thenReturn("hashed");

        OtpResponse res = service.register(req);

        ArgumentCaptor<Account> accCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accCaptor.capture());
        Account saved = accCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("a@x.com");
        assertThat(saved.getPasswordHash()).isEqualTo("hashed");
        assertThat(saved.getRole()).isEqualTo(Role.MEMBER);
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getPhone()).isEqualTo("0909");

        ArgumentCaptor<MemberProfile> mpCaptor = ArgumentCaptor.forClass(MemberProfile.class);
        verify(memberProfileRepository).save(mpCaptor.capture());
        assertThat(mpCaptor.getValue().getAccount()).isSameAs(saved);
        assertThat(mpCaptor.getValue().getFullName()).isEqualTo("Nguyen A");

        verify(otpService).issue(saved, OtpPurpose.REGISTER);
        assertThat(res).extracting("message")
                .isEqualTo("Đăng ký thành công. Vui lòng kiểm tra email để xác minh OTP.");
    }

    // ==================== login ====================

    @Test
    void login_success() {
        LoginRequest req = mock(LoginRequest.class);
        when(req.getEmail()).thenReturn("a@x.com");
        when(req.getPassword()).thenReturn("pw");
        Account account = mock(Account.class);
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.isEmailVerified()).thenReturn(true);
        when(accountRepository.findByEmail("a@x.com")).thenReturn(Optional.of(account));
        when(jwtService.generateAccessToken(account)).thenReturn("access");
        when(refreshTokenService.createRefreshToken(account))
                .thenReturn(RefreshToken.builder().token("refresh").build());

        AuthResponse res = service.login(req);

        ArgumentCaptor<Authentication> authCaptor = ArgumentCaptor.forClass(Authentication.class);
        verify(authenticationManager).authenticate(authCaptor.capture());
        assertThat(authCaptor.getValue().getPrincipal()).isEqualTo("a@x.com");
        assertThat(authCaptor.getValue().getCredentials()).isEqualTo("pw");
        assertThat(res).extracting("accessToken", "refreshToken").containsExactly("access", "refresh");
    }

    @Test
    void login_accountNotFound_throws() {
        LoginRequest req = mock(LoginRequest.class);
        when(req.getEmail()).thenReturn("a@x.com");
        when(accountRepository.findByEmail("a@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Account không tồn tại sau khi authenticate");
    }

    @Test
    void login_banned_throws() {
        LoginRequest req = mock(LoginRequest.class);
        when(req.getEmail()).thenReturn("a@x.com");
        Account account = mock(Account.class);
        when(account.getStatus()).thenReturn(AccountStatus.BANNED);
        when(accountRepository.findByEmail("a@x.com")).thenReturn(Optional.of(account));

        assertAppError(() -> service.login(req), ErrorCode.ACCOUNT_BANNED);

        verifyNoInteractions(jwtService, refreshTokenService);
    }

    @Test
    void login_statusNotActive_throwsBanned() {
        LoginRequest req = mock(LoginRequest.class);
        when(req.getEmail()).thenReturn("a@x.com");
        Account account = mock(Account.class);
        when(account.getStatus()).thenReturn(null); // status khác ACTIVE
        when(accountRepository.findByEmail("a@x.com")).thenReturn(Optional.of(account));

        assertAppError(() -> service.login(req), ErrorCode.ACCOUNT_BANNED);

        verifyNoInteractions(jwtService, refreshTokenService);
    }

    @Test
    void login_emailNotVerified_throws() {
        LoginRequest req = mock(LoginRequest.class);
        when(req.getEmail()).thenReturn("a@x.com");
        Account account = mock(Account.class);
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.isEmailVerified()).thenReturn(false);
        when(accountRepository.findByEmail("a@x.com")).thenReturn(Optional.of(account));

        assertAppError(() -> service.login(req), ErrorCode.EMAIL_NOT_VERIFIED);

        verifyNoInteractions(jwtService, refreshTokenService);
    }

    // ==================== loginWithGoogle ====================

    private GoogleLoginRequest googleReq() {
        GoogleLoginRequest r = mock(GoogleLoginRequest.class);
        when(r.getIdToken()).thenReturn("idToken");
        return r;
    }

    private GoogleTokenVerifier.GoogleUserInfo googleInfo(String email, String gid, String name) {
        GoogleTokenVerifier.GoogleUserInfo info = mock(GoogleTokenVerifier.GoogleUserInfo.class);
        when(info.email()).thenReturn(email);
        when(info.googleId()).thenReturn(gid);
        when(info.name()).thenReturn(name);
        when(googleTokenVerifier.verify("idToken")).thenReturn(info);
        return info;
    }

    private void stubTokens(Account account) {
        when(jwtService.generateAccessToken(account)).thenReturn("access");
        when(refreshTokenService.createRefreshToken(account))
                .thenReturn(RefreshToken.builder().token("refresh").build());
    }

    @Test
    void loginWithGoogle_newAccount_createsAccountAndProfile() {
        googleInfo("g@x.com", "gid-1", "Google User");
        when(accountRepository.findByEmail("g@x.com")).thenReturn(Optional.empty());
        when(accountRepository.saveAndFlush(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jwtService.generateAccessToken(any(Account.class))).thenReturn("access");
        when(refreshTokenService.createRefreshToken(any(Account.class)))
                .thenReturn(RefreshToken.builder().token("refresh").build());

        AuthResponse res = service.loginWithGoogle(googleReq());

        ArgumentCaptor<Account> accCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).saveAndFlush(accCaptor.capture());
        Account saved = accCaptor.getValue();
        assertThat(saved.getEmail()).isEqualTo("g@x.com");
        assertThat(saved.getGoogleId()).isEqualTo("gid-1");
        assertThat(saved.getRole()).isEqualTo(Role.MEMBER);
        assertThat(saved.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(saved.isEmailVerified()).isTrue();

        ArgumentCaptor<MemberProfile> mp = ArgumentCaptor.forClass(MemberProfile.class);
        verify(memberProfileRepository).save(mp.capture());
        assertThat(mp.getValue().getAccount()).isSameAs(saved);
        assertThat(mp.getValue().getFullName()).isEqualTo("Google User");
        assertThat(res).extracting("accessToken", "refreshToken").containsExactly("access", "refresh");
    }

    @Test
    void loginWithGoogle_existingBanned_throws() {
        googleInfo("g@x.com", "gid-1", "n");
        Account account = mock(Account.class);
        when(account.getStatus()).thenReturn(AccountStatus.BANNED);
        when(accountRepository.findByEmail("g@x.com")).thenReturn(Optional.of(account));

        assertAppError(() -> service.loginWithGoogle(googleReq()), ErrorCode.ACCOUNT_BANNED);

        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void loginWithGoogle_googleIdMismatch_throwsConflict() {
        googleInfo("g@x.com", "gid-NEW", "n");
        Account account = mock(Account.class);
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.getGoogleId()).thenReturn("gid-OLD");
        when(accountRepository.findByEmail("g@x.com")).thenReturn(Optional.of(account));

        assertAppError(() -> service.loginWithGoogle(googleReq()), ErrorCode.GOOGLE_ACCOUNT_CONFLICT);

        verify(accountRepository, never()).saveAndFlush(any());
    }

    @Test
    void loginWithGoogle_existingNoGoogleId_linksAndKeepsPassword() {
        googleInfo("g@x.com", "gid-1", "n");
        Account account = mock(Account.class);
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.getGoogleId()).thenReturn(null);
        when(account.isEmailVerified()).thenReturn(true);
        when(accountRepository.findByEmail("g@x.com")).thenReturn(Optional.of(account));
        stubTokens(account);

        AuthResponse res = service.loginWithGoogle(googleReq());

        verify(account).setGoogleId("gid-1");
        verify(account, never()).setPasswordHash(any());
        verify(accountRepository).saveAndFlush(account);
        assertThat(res.getAccessToken()).isEqualTo("access");
    }

    @Test
    void loginWithGoogle_existingSameGoogleId_unverified_clearsPasswordAndVerifies() {
        googleInfo("g@x.com", "gid-1", "n");
        Account account = mock(Account.class);
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        when(account.getGoogleId()).thenReturn("gid-1");
        when(account.isEmailVerified()).thenReturn(false);
        when(accountRepository.findByEmail("g@x.com")).thenReturn(Optional.of(account));
        stubTokens(account);

        service.loginWithGoogle(googleReq());

        verify(account, never()).setGoogleId(any());
        verify(account).setPasswordHash(null);
        verify(account).setEmailVerified(true);
        verify(refreshTokenService).revokeAllAccountTokens(account);
        verify(accountRepository).saveAndFlush(account);
    }

    // ==================== refreshToken ====================

    @Test
    void refreshToken_notFound_throws() {
        when(refreshTokenService.findByToken("t")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refreshToken("t"))
                .isInstanceOf(TokenRefreshException.class);
    }

    @Test
    void refreshToken_accountBanned_throws() {
        Account account = mock(Account.class);
        when(account.getStatus()).thenReturn(AccountStatus.BANNED);
        RefreshToken rt = RefreshToken.builder().token("t").account(account).build();
        when(refreshTokenService.findByToken("t")).thenReturn(Optional.of(rt));

        assertAppError(() -> service.refreshToken("t"), ErrorCode.ACCOUNT_BANNED);

        verify(refreshTokenService).verifyExpiration(rt);
        verify(refreshTokenService, never()).deleteByAccount(any());
    }

    @Test
    void refreshToken_success_rotatesToken() {
        Account account = mock(Account.class);
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);
        RefreshToken old = RefreshToken.builder().token("old").account(account).build();
        when(refreshTokenService.findByToken("old")).thenReturn(Optional.of(old));
        when(jwtService.generateAccessToken(account)).thenReturn("newAccess");
        when(refreshTokenService.createRefreshToken(account))
                .thenReturn(RefreshToken.builder().token("newRefresh").build());

        AuthResponse res = service.refreshToken("old");

        InOrder order = inOrder(refreshTokenService);
        order.verify(refreshTokenService).verifyExpiration(old);
        order.verify(refreshTokenService).deleteByAccount(account);
        order.verify(refreshTokenService).createRefreshToken(account);
        assertThat(res).extracting("accessToken", "refreshToken")
                .containsExactly("newAccess", "newRefresh");
    }

    // ==================== logout ====================

    @Test
    void logout_success() {
        Account account = mock(Account.class);
        when(accountRepository.findByEmail("a@x.com")).thenReturn(Optional.of(account));

        service.logout("a@x.com");

        verify(refreshTokenService).deleteByAccount(account);
    }

    @Test
    void logout_accountNotFound_throws() {
        when(accountRepository.findByEmail("a@x.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.logout("a@x.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Account không tồn tại");
        verifyNoInteractions(refreshTokenService);
    }

    // ==================== registerOrganization ====================

    private RegisterOrganizationRequest orgRequest(String email, String taxCode) {
        RegisterOrganizationRequest r = mock(RegisterOrganizationRequest.class);
        when(r.getContactEmail()).thenReturn(email);
        when(r.getTaxCode()).thenReturn(taxCode);
        return r;
    }

    private Account currentAccount() {
        return Account.builder()
                .email("member@chiase.org")
                .role(Role.MEMBER)
                .status(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    void registerOrganization_emailExists_throws() {
        Account current = currentAccount();
        RegisterOrganizationRequest req = orgRequest("  Contact@Chiase.ORG ", "0312345678");
        when(accountRepository.existsByEmailIgnoreCase("contact@chiase.org")).thenReturn(true);

        assertThatThrownBy(() -> service.registerOrganization(current, req))
                .isInstanceOf(AccountAlreadyExistsException.class)
                .hasMessage("Email đã được sử dụng!!");

        verifyNoInteractions(organizationProfileRepository);
        verify(accountRepository, never()).save(any());
    }

    @Test
    void registerOrganization_taxCodeExists_throws() {
        Account current = currentAccount();
        RegisterOrganizationRequest req = orgRequest("contact@chiase.org", "0312345678");
        when(accountRepository.existsByEmailIgnoreCase("contact@chiase.org")).thenReturn(false);
        when(organizationProfileRepository.existsByTaxCode("0312345678")).thenReturn(true);

        assertThatThrownBy(() -> service.registerOrganization(current, req))
                .isInstanceOf(AccountAlreadyExistsException.class)
                .hasMessage("Mã số thuế đã được sử dụng!!");

        verify(accountRepository, never()).save(any());
        verify(organizationProfileRepository, never()).save(any());
    }

    @Test
    void registerOrganization_success() {
        Account current = currentAccount();
        RegisterOrganizationRequest req = orgRequest("  Contact@Chiase.ORG ", "0312345678");
        when(accountRepository.existsByEmailIgnoreCase("contact@chiase.org")).thenReturn(false);
        when(organizationProfileRepository.existsByTaxCode("0312345678")).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenAnswer(inv -> inv.getArgument(0));

        OrganizationProfile org = new OrganizationProfile();
        when(organizationProfileMapper.toEntity(req)).thenReturn(org);
        RegisterOrganizationResponse expected = mock(RegisterOrganizationResponse.class);
        when(organizationProfileMapper.toResponse(org)).thenReturn(expected);

        RegisterOrganizationResponse result = service.registerOrganization(current, req);

        ArgumentCaptor<Account> accountCaptor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository).save(accountCaptor.capture());
        Account savedAccount = accountCaptor.getValue();
        assertThat(savedAccount).isNotSameAs(current);
        assertThat(savedAccount).extracting("email", "role", "status")
                .containsExactly("contact@chiase.org", Role.ORGANIZATION, AccountStatus.ACTIVE);

        assertThat(org).extracting("account", "verificationStatus")
                .containsExactly(savedAccount, VerificationStatus.PENDING);
        verify(organizationProfileRepository).save(org);
        assertThat(result).isSameAs(expected);
    }

    // ==================== changePassword ====================

    private ChangePasswordRequest changePwReq(String oldPw, String newPw, String repeat) {
        ChangePasswordRequest r = mock(ChangePasswordRequest.class);
        when(r.getOldPassword()).thenReturn(oldPw);
        when(r.getNewPassword()).thenReturn(newPw);
        when(r.getNewPasswordRepeat()).thenReturn(repeat);
        return r;
    }

    @Test
    void changePassword_confirmMismatch_throws() {
        Account account = mock(Account.class);
        ChangePasswordRequest req = changePwReq("old", "new1", "new2");

        assertAppError(() -> service.changePassword(account, req), ErrorCode.PASSWORD_CONFIRM_MISMATCH);

        verify(accountRepository, never()).save(any());
        verifyNoInteractions(refreshTokenService);
    }

    @Test
    void changePassword_noPasswordHash_throws() {
        Account account = mock(Account.class);
        when(account.getPasswordHash()).thenReturn(null);
        ChangePasswordRequest req = changePwReq("old", "new", "new");

        assertAppError(() -> service.changePassword(account, req), ErrorCode.PASSWORD_NOT_SET);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void changePassword_wrongOldPassword_throws() {
        Account account = mock(Account.class);
        when(account.getPasswordHash()).thenReturn("hash");
        when(account.getPassword()).thenReturn("hash");
        when(passwordEncoder.matches("old", "hash")).thenReturn(false);
        ChangePasswordRequest req = changePwReq("old", "new", "new");

        assertAppError(() -> service.changePassword(account, req), ErrorCode.WRONG_PASSWORD);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void changePassword_newSameAsOld_throws() {
        Account account = mock(Account.class);
        when(account.getPasswordHash()).thenReturn("hash");
        when(account.getPassword()).thenReturn("hash");
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);
        when(passwordEncoder.matches("new", "hash")).thenReturn(true); // mới trùng cũ
        ChangePasswordRequest req = changePwReq("old", "new", "new");

        assertAppError(() -> service.changePassword(account, req), ErrorCode.NEW_PASSWORD_SAME_AS_OLD);

        verify(accountRepository, never()).save(any());
    }

    @Test
    void changePassword_success_savesAndRevokesTokens() {
        Account account = mock(Account.class);
        when(account.getPasswordHash()).thenReturn("hash");
        when(account.getPassword()).thenReturn("hash");
        when(passwordEncoder.matches("old", "hash")).thenReturn(true);
        when(passwordEncoder.matches("new", "hash")).thenReturn(false);
        when(passwordEncoder.encode("new")).thenReturn("newHash");
        ChangePasswordRequest req = changePwReq("old", "new", "new");

        service.changePassword(account, req);

        verify(account).setPasswordHash("newHash");
        verify(accountRepository).save(account);
        verify(refreshTokenService).revokeAllAccountTokens(account);
    }
}