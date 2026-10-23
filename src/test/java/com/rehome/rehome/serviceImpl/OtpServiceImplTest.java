package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.ForgotPasswordRequest;
import com.rehome.rehome.dto.request.OtpRequest;
import com.rehome.rehome.dto.request.ResetPasswordRequest;
import com.rehome.rehome.dto.request.VerifyOtpRequest;
import com.rehome.rehome.dto.response.OtpResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.EmailOtp;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.OtpPurpose;
import com.rehome.rehome.exception.OtpException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.EmailOtpRepository;
import com.rehome.rehome.service.OtpEmailService;
import com.rehome.rehome.service.RefreshTokenService;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mail.MailSendException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OtpServiceImplTest {

    private static final String INVALID = "Mã xác thực không hợp lệ, hết hạn hoặc đã sử dụng.";

    @Mock AccountRepository accounts;
    @Mock EmailOtpRepository otps;
    @Mock OtpEmailService otpEmailService;
    @Mock PasswordEncoder encoder;
    @Mock RefreshTokenService refreshTokens;

    private OtpServiceImpl service;
    private Account account;
    private final UUID id = UUID.randomUUID();


    @BeforeEach
    void setUp() {
        service = new OtpServiceImpl(accounts, otps, otpEmailService, encoder, refreshTokens);
        ReflectionTestUtils.setField(service, "ttl", 300L);
        ReflectionTestUtils.setField(service, "resendSeconds", 60L);
        ReflectionTestUtils.setField(service, "maxAttempts", 5);
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(service, "resetLinkTtl", 1800L);

        account = mock(Account.class);                                   // phải có trước
        when(account.getId()).thenReturn(id);
        when(account.getEmail()).thenReturn("a@x.com");
        when(account.getStatus()).thenReturn(AccountStatus.ACTIVE);      // thay cho isEnabled()
        when(account.isEmailVerified()).thenReturn(true);
        when(accounts.findByEmailForUpdate("a@x.com")).thenReturn(Optional.of(account));
        when(accounts.findByEmailIgnoreCase("a@x.com")).thenReturn(Optional.of(account));  // sau account = mock(...)
        when(encoder.encode(anyString())).thenReturn("hashed");
    }

    // ---------- helpers ----------

    private String key(OtpPurpose p) {
        return id + ":" + p;
    }

    private EmailOtp otp(Instant expiresAt, int attempts, boolean consumed) {
        EmailOtp o = new EmailOtp();
        o.setCodeHash("codeHash");
        o.setExpiresAt(expiresAt);
        o.setAttempts(attempts);
        o.setConsumed(consumed);
        return o;
    }

    private Instant future() {
        return Instant.now().plusSeconds(600);
    }

    private Instant past() {
        return Instant.now().minusSeconds(600);
    }

    private void givenOtp(OtpPurpose p, EmailOtp otp) {
        when(otps.findById(key(p))).thenReturn(Optional.of(otp));
    }

    private void assertInvalid(ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(OtpException.class).hasMessage(INVALID);
    }

    private void givenBanned() {
        when(account.getStatus()).thenReturn(AccountStatus.BANNED);
    }

    private void givenUnverified() {
        when(account.isEmailVerified()).thenReturn(false);
    }

    private OtpRequest otpRequest(String email, OtpPurpose purpose) {
        OtpRequest r = mock(OtpRequest.class);
        when(r.email()).thenReturn(email);
        when(r.purpose()).thenReturn(purpose);
        return r;
    }

    private VerifyOtpRequest verifyRequest(String email, String code, OtpPurpose purpose) {
        VerifyOtpRequest r = mock(VerifyOtpRequest.class);
        when(r.email()).thenReturn(email);
        when(r.otp()).thenReturn(code);
        when(r.purpose()).thenReturn(purpose);
        return r;
    }

    private ResetPasswordRequest resetRequest(String email, String newPw, String confirm) {
        ResetPasswordRequest r = mock(ResetPasswordRequest.class);
        when(r.email()).thenReturn(email);
        when(r.resetToken()).thenReturn("rt");
        when(r.newPassword()).thenReturn(newPw);
        when(r.confirmPassword()).thenReturn(confirm);
        return r;
    }

    private OtpServiceImpl spyWithNoopIssue() {
        OtpServiceImpl spy = Mockito.spy(service);
        doNothing().when(spy).issue(any(Account.class), any(OtpPurpose.class));
        return spy;
    }

    // ==================== issue ====================

    @Test
    void issue_newOtp_savesAndSendsEmail() {
        when(otps.findById(key(OtpPurpose.REGISTER))).thenReturn(Optional.empty());

        service.issue(account, OtpPurpose.REGISTER);

        ArgumentCaptor<EmailOtp> saved = ArgumentCaptor.forClass(EmailOtp.class);
        verify(otps).save(saved.capture());
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(otpEmailService).send(eq("a@x.com"), code.capture(), eq(OtpPurpose.REGISTER), eq(300L));

        assertThat(code.getValue()).matches("\\d{6}");
        verify(encoder).encode(code.getValue());

        EmailOtp o = saved.getValue();
        assertThat(o).extracting("id", "codeHash").containsExactly(key(OtpPurpose.REGISTER), "hashed");
        assertThat(o.getAttempts()).isZero();
        assertThat(o.isConsumed()).isFalse();
        assertThat(o.getResetTokenHash()).isNull();
        assertThat(o.getResetExpiresAt()).isNull();
        assertThat(o.getSentAt()).isNotNull();
        assertThat(o.getExpiresAt()).isAfter(o.getSentAt());
    }

    @Test
    void issue_sentRecently_throwsTooManyRequests() {
        EmailOtp existing = otp(future(), 0, false);
        existing.setSentAt(Instant.now().minusSeconds(10));
        givenOtp(OtpPurpose.REGISTER, existing);

        assertThatThrownBy(() -> service.issue(account, OtpPurpose.REGISTER))
                .isInstanceOf(OtpException.class)
                .hasMessage("Vui lòng chờ trước khi gửi lại OTP.");

        verify(otps, never()).save(any());
        verifyNoInteractions(otpEmailService);
    }

    @Test
    void issue_resendWindowPassed_resetsExistingOtp() {
        EmailOtp existing = otp(past(), 3, true);
        existing.setSentAt(Instant.now().minusSeconds(120));
        existing.setResetTokenHash("oldReset");
        existing.setResetExpiresAt(Instant.now());
        givenOtp(OtpPurpose.FORGOT_PASSWORD, existing);

        service.issue(account, OtpPurpose.FORGOT_PASSWORD);

        verify(otps).save(existing);
        verify(otpEmailService).send(eq("a@x.com"), anyString(), eq(OtpPurpose.FORGOT_PASSWORD), eq(300L));
        assertThat(existing.getAttempts()).isZero();
        assertThat(existing.isConsumed()).isFalse();
        assertThat(existing.getResetTokenHash()).isNull();
        assertThat(existing.getResetExpiresAt()).isNull();
        assertThat(existing.getExpiresAt()).isAfter(Instant.now());
    }

    // ==================== resend ====================

    private static final String RESEND_MSG = "Nếu tài khoản phù hợp, OTP đã được gửi đến email.";

    @Test
    void resend_accountNotFound_doesNotIssue() {
        OtpServiceImpl spy = spyWithNoopIssue();

        OtpResponse res = spy.resend(otpRequest("none@x.com", OtpPurpose.REGISTER));

        verify(spy, never()).issue(any(), any());
        assertThat(res).extracting("message").isEqualTo(RESEND_MSG);
    }

    @Test
    void resend_registerNotVerified_issues() {
        givenUnverified();
        OtpServiceImpl spy = spyWithNoopIssue();

        OtpResponse res = spy.resend(otpRequest("a@x.com", OtpPurpose.REGISTER));

        verify(spy).issue(account, OtpPurpose.REGISTER);
        assertThat(res).extracting("message").isEqualTo(RESEND_MSG);
    }

    @Test
    void resend_registerAlreadyVerified_doesNotIssue() {
        when(account.isEmailVerified()).thenReturn(true);
        OtpServiceImpl spy = spyWithNoopIssue();

        spy.resend(otpRequest("a@x.com", OtpPurpose.REGISTER));

        verify(spy, never()).issue(any(), any());
    }

    @Test
    void resend_forgotPasswordEligible_issues() {
        OtpServiceImpl spy = spyWithNoopIssue();

        spy.resend(otpRequest("a@x.com", OtpPurpose.FORGOT_PASSWORD));

        verify(spy).issue(account, OtpPurpose.FORGOT_PASSWORD);
    }

    @Test
    void resend_forgotPasswordBanned_doesNotIssue() {
        givenBanned();
        OtpServiceImpl spy = spyWithNoopIssue();

        spy.resend(otpRequest("a@x.com", OtpPurpose.FORGOT_PASSWORD));

        verify(spy, never()).issue(any(), any());
    }

    @Test
    void resend_forgotPasswordUnverified_doesNotIssue() {
        givenUnverified();
        OtpServiceImpl spy = spyWithNoopIssue();

        spy.resend(otpRequest("a@x.com", OtpPurpose.FORGOT_PASSWORD));

        verify(spy, never()).issue(any(), any());
    }

    // ==================== verify ====================

    @Test
    void verify_accountNotFound_invalid() {
        VerifyOtpRequest req = verifyRequest("none@x.com", "123456", OtpPurpose.REGISTER);
        assertInvalid(() -> service.verify(req));
    }

    @Test
    void verify_otpNotFound_invalid() {
        VerifyOtpRequest req = verifyRequest("a@x.com", "123456", OtpPurpose.REGISTER);
        assertInvalid(() -> service.verify(req));
    }

    @Test
    void verify_alreadyConsumed_invalid() {
        givenOtp(OtpPurpose.REGISTER, otp(future(), 0, true));
        VerifyOtpRequest req = verifyRequest("a@x.com", "123456", OtpPurpose.REGISTER);
        assertInvalid(() -> service.verify(req));
    }

    @Test
    void verify_expired_invalid() {
        givenOtp(OtpPurpose.REGISTER, otp(past(), 0, false));
        VerifyOtpRequest req = verifyRequest("a@x.com", "123456", OtpPurpose.REGISTER);
        assertInvalid(() -> service.verify(req));
    }

    @Test
    void verify_tooManyAttempts_invalid() {
        givenOtp(OtpPurpose.REGISTER, otp(future(), 5, false));
        VerifyOtpRequest req = verifyRequest("a@x.com", "123456", OtpPurpose.REGISTER);
        assertInvalid(() -> service.verify(req));
    }

    @Test
    void verify_accountWithoutEmail_throwsIllegalArgument() {
        Account noEmail = mock(Account.class);
        when(noEmail.getId()).thenReturn(id);
        when(noEmail.getEmail()).thenReturn(null);
        when(accounts.findByEmailForUpdate("x@x.com")).thenReturn(Optional.of(noEmail));
        givenOtp(OtpPurpose.REGISTER, otp(future(), 0, false));
        VerifyOtpRequest req = verifyRequest("x@x.com", "123456", OtpPurpose.REGISTER);

        assertThatThrownBy(() -> service.verify(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email này chưa được đăng ký!!");
    }

    @Test
    void verify_wrongCode_incrementsAttemptsAndThrows() {
        EmailOtp o = otp(future(), 0, false);
        givenOtp(OtpPurpose.REGISTER, o);
        when(encoder.matches("000000", "codeHash")).thenReturn(false);
        VerifyOtpRequest req = verifyRequest("a@x.com", "000000", OtpPurpose.REGISTER);

        assertInvalid(() -> service.verify(req));

        assertThat(o.getAttempts()).isEqualTo(1);
        assertThat(o.isConsumed()).isFalse();
    }

    @Test
    void verify_register_success() {
        EmailOtp o = otp(future(), 0, false);
        givenOtp(OtpPurpose.REGISTER, o);
        when(encoder.matches("123456", "codeHash")).thenReturn(true);

        OtpResponse res = service.verify(verifyRequest("a@x.com", "123456", OtpPurpose.REGISTER));

        assertThat(o.isConsumed()).isTrue();
        verify(account).setEmailVerified(true);
        assertThat(res).extracting("message").isEqualTo("Xác minh email thành công. Vui lòng đăng nhập.");
    }

    @Test
    void verify_forgotPassword_accountBanned_invalid() {
        givenBanned();
        givenOtp(OtpPurpose.FORGOT_PASSWORD, otp(future(), 0, false));
        when(encoder.matches("123456", "codeHash")).thenReturn(true);
        VerifyOtpRequest req = verifyRequest("a@x.com", "123456", OtpPurpose.FORGOT_PASSWORD);

        assertInvalid(() -> service.verify(req));
    }

    @Test
    void verify_forgotPassword_accountUnverified_invalid() {
        givenUnverified();
        givenOtp(OtpPurpose.FORGOT_PASSWORD, otp(future(), 0, false));
        when(encoder.matches("123456", "codeHash")).thenReturn(true);
        VerifyOtpRequest req = verifyRequest("a@x.com", "123456", OtpPurpose.FORGOT_PASSWORD);

        assertInvalid(() -> service.verify(req));
    }

    @Test
    void verify_forgotPassword_success_issuesResetToken() {
        EmailOtp o = otp(future(), 0, false);
        givenOtp(OtpPurpose.FORGOT_PASSWORD, o);
        when(encoder.matches("123456", "codeHash")).thenReturn(true);

        OtpResponse res = service.verify(verifyRequest("a@x.com", "123456", OtpPurpose.FORGOT_PASSWORD));

        ArgumentCaptor<String> rawToken = ArgumentCaptor.forClass(String.class);
        verify(encoder).encode(rawToken.capture());
        assertThat(UUID.fromString(rawToken.getValue())).isNotNull();
        assertThat(o.isConsumed()).isTrue();
        assertThat(o.getResetTokenHash()).isEqualTo("hashed");
        assertThat(o.getResetExpiresAt()).isAfter(Instant.now());
        assertThat(res).extracting("message", "resetToken")
                .containsExactly("OTP hợp lệ. Sử dụng resetToken để đặt lại mật khẩu.", rawToken.getValue());
    }

    // ==================== resetPassword ====================

    private EmailOtp resetOtp(String hash, Instant resetExpiresAt) {
        EmailOtp o = new EmailOtp();
        o.setResetTokenHash(hash);
        o.setResetExpiresAt(resetExpiresAt);
        return o;
    }

    @Test
    void resetPassword_confirmMismatch_throws() {
        ResetPasswordRequest req = resetRequest("a@x.com", "newPw", "other");

        assertThatThrownBy(() -> service.resetPassword(req))
                .isInstanceOf(OtpException.class)
                .hasMessage("Mật khẩu xác nhận không khớp.");
        verifyNoInteractions(accounts);
    }

    @Test
    void resetPassword_accountNotFound_invalid() {
        ResetPasswordRequest req = resetRequest("none@x.com", "newPw", "newPw");
        assertInvalid(() -> service.resetPassword(req));
    }

    @Test
    void resetPassword_otpNotFound_invalid() {
        ResetPasswordRequest req = resetRequest("a@x.com", "newPw", "newPw");
        assertInvalid(() -> service.resetPassword(req));
    }

    @Test
    void resetPassword_accountBanned_invalid() {
        givenBanned();
        givenOtp(OtpPurpose.FORGOT_PASSWORD, resetOtp("rtHash", future()));
        when(encoder.matches("rt", "rtHash")).thenReturn(true);
        ResetPasswordRequest req = resetRequest("a@x.com", "newPw", "newPw");

        assertInvalid(() -> service.resetPassword(req));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void resetPassword_accountUnverified_invalid() {
        givenUnverified();
        givenOtp(OtpPurpose.FORGOT_PASSWORD, resetOtp("rtHash", future()));
        when(encoder.matches("rt", "rtHash")).thenReturn(true);
        ResetPasswordRequest req = resetRequest("a@x.com", "newPw", "newPw");

        assertInvalid(() -> service.resetPassword(req));
        verifyNoInteractions(refreshTokens);
    }

    @Test
    void resetPassword_noResetTokenHash_invalid() {
        givenOtp(OtpPurpose.FORGOT_PASSWORD, resetOtp(null, future()));
        ResetPasswordRequest req = resetRequest("a@x.com", "newPw", "newPw");

        assertInvalid(() -> service.resetPassword(req));
    }

    @Test
    void resetPassword_noResetExpiry_invalid() {
        givenOtp(OtpPurpose.FORGOT_PASSWORD, resetOtp("rtHash", null));
        ResetPasswordRequest req = resetRequest("a@x.com", "newPw", "newPw");

        assertInvalid(() -> service.resetPassword(req));
    }

    @Test
    void resetPassword_resetTokenExpired_invalid() {
        givenOtp(OtpPurpose.FORGOT_PASSWORD, resetOtp("rtHash", past()));
        when(encoder.matches("rt", "rtHash")).thenReturn(true);
        ResetPasswordRequest req = resetRequest("a@x.com", "newPw", "newPw");

        assertInvalid(() -> service.resetPassword(req));
    }

    @Test
    void resetPassword_resetTokenMismatch_invalid() {
        givenOtp(OtpPurpose.FORGOT_PASSWORD, resetOtp("rtHash", future()));
        when(encoder.matches("rt", "rtHash")).thenReturn(false);
        ResetPasswordRequest req = resetRequest("a@x.com", "newPw", "newPw");

        assertInvalid(() -> service.resetPassword(req));
        verify(account, never()).setPasswordHash(any());
    }

    @Test
    void resetPassword_success() {
        EmailOtp o = resetOtp("rtHash", future());
        givenOtp(OtpPurpose.FORGOT_PASSWORD, o);
        when(encoder.matches("rt", "rtHash")).thenReturn(true);
        when(encoder.encode("newPw")).thenReturn("newHash");

        OtpResponse res = service.resetPassword(resetRequest("a@x.com", "newPw", "newPw"));

        verify(account).setPasswordHash("newHash");
        assertThat(o.getResetTokenHash()).isNull();
        assertThat(o.getResetExpiresAt()).isNull();
        verify(refreshTokens).deleteByAccount(account);
        assertThat(res).extracting("message")
                .isEqualTo("Đặt lại mật khẩu thành công. Vui lòng đăng nhập.");
    }

    // ==================== forgotPassword ====================

    private ForgotPasswordRequest forgotRequest(String email) {
        ForgotPasswordRequest r = mock(ForgotPasswordRequest.class);
        when(r.getEmail()).thenReturn(email);
        return r;
    }

    @Test
    void forgotPassword_accountNotFound_silentlyReturns() {
        service.forgotPassword(forgotRequest("none@x.com"));

        verifyNoInteractions(otps, otpEmailService);
    }

    @Test
    void forgotPassword_accountBanned_silentlyReturns() {
        givenBanned();

        service.forgotPassword(forgotRequest("a@x.com"));

        verifyNoInteractions(otps, otpEmailService);
    }

    @Test
    void forgotPassword_accountUnverified_silentlyReturns() {
        givenUnverified();

        service.forgotPassword(forgotRequest("a@x.com"));

        verifyNoInteractions(otps, otpEmailService);
    }

    @Test
    void forgotPassword_withinCooldown_silentlyReturns() {
        EmailOtp existing = otp(future(), 0, false);
        existing.setId(key(OtpPurpose.FORGOT_PASSWORD));
        existing.setSentAt(Instant.now().minusSeconds(10));
        givenOtp(OtpPurpose.FORGOT_PASSWORD, existing);

        service.forgotPassword(forgotRequest("a@x.com"));

        verify(otps, never()).save(any());
        verifyNoInteractions(otpEmailService);
    }

    @Test
    void forgotPassword_newRow_invalidatesOtpPartAndSendsLink() {
        when(otps.findById(key(OtpPurpose.FORGOT_PASSWORD))).thenReturn(Optional.empty());

        service.forgotPassword(forgotRequest("a@x.com"));

        ArgumentCaptor<EmailOtp> saved = ArgumentCaptor.forClass(EmailOtp.class);
        verify(otps).save(saved.capture());
        EmailOtp o = saved.getValue();
        assertThat(o.getId()).isEqualTo(key(OtpPurpose.FORGOT_PASSWORD));
        assertThat(o.isConsumed()).isTrue();
        assertThat(o.getAttempts()).isZero();
        assertThat(o.getCodeHash()).isEqualTo("hashed");
        assertThat(o.getResetTokenHash()).isEqualTo("hashed");
        assertThat(o.getSentAt()).isNotNull();
        assertThat(o.getResetExpiresAt()).isAfter(Instant.now());

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(otpEmailService).sendResetLink(eq("a@x.com"), link.capture(), eq(30L));
        assertThat(link.getValue())
                .startsWith("http://localhost:3000/reset-password?email=a%40x.com&token=");
    }

    @Test
    void forgotPassword_existingRowAfterCooldown_keepsIdAndSendsLink() {
        EmailOtp existing = otp(past(), 3, true);
        existing.setId(key(OtpPurpose.FORGOT_PASSWORD));
        existing.setCodeHash("keepMe");
        existing.setSentAt(Instant.now().minusSeconds(120));
        givenOtp(OtpPurpose.FORGOT_PASSWORD, existing);

        service.forgotPassword(forgotRequest("a@x.com"));

        verify(otps).save(existing);
        assertThat(existing.getCodeHash()).isEqualTo("keepMe");   // branch id != null: untouched
        assertThat(existing.getResetTokenHash()).isEqualTo("hashed");
        verify(otpEmailService).sendResetLink(eq("a@x.com"), anyString(), eq(30L));
    }

    @Test
    void forgotPassword_mailFails_exceptionSwallowed() {
        when(otps.findById(key(OtpPurpose.FORGOT_PASSWORD))).thenReturn(Optional.empty());
        doThrow(new MailSendException("smtp down"))
                .when(otpEmailService).sendResetLink(anyString(), anyString(), anyLong());

        service.forgotPassword(forgotRequest("a@x.com"));   // must not throw

        verify(otps).save(any(EmailOtp.class));
    }
}