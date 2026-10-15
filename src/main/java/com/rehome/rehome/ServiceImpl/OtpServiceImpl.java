package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.dto.request.ForgotPasswordRequest;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.EmailOtp;
import com.rehome.rehome.entity.enums.OtpPurpose;
import com.rehome.rehome.service.OtpService;
import com.rehome.rehome.service.OtpEmailService;
import com.rehome.rehome.service.RefreshTokenService;

import com.rehome.rehome.dto.request.OtpRequest;
import com.rehome.rehome.dto.request.VerifyOtpRequest;
import com.rehome.rehome.dto.request.ResetPasswordRequest;
import com.rehome.rehome.dto.response.OtpResponse;
import com.rehome.rehome.exception.OtpException;
import com.rehome.rehome.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
@Slf4j
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {

    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.reset-link-ttl-seconds:1800}")
    private long resetLinkTtl;

    private final AccountRepository accounts;
    private final EmailOtpRepository otps;
    private final OtpEmailService otpEmailService;
    private final PasswordEncoder encoder;
    private final RefreshTokenService refreshTokens;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.otp.ttl-seconds:300}") private long ttl;
    @Value("${app.otp.resend-seconds:60}") private long resendSeconds;
    @Value("${app.otp.max-attempts:5}") private int maxAttempts;

    // Caller holds the account row lock (or is registering a new account).
    @Transactional
    @Override
    public void issue(Account account, OtpPurpose purpose) {
        String key = account.getId() + ":" + purpose;
        EmailOtp otp = otps.findById(key).orElseGet(EmailOtp::new);
        Instant now = Instant.now();
        if (otp.getSentAt() != null && now.isBefore(otp.getSentAt().plusSeconds(resendSeconds))) {
            throw new OtpException(HttpStatus.TOO_MANY_REQUESTS, "Vui lòng chờ trước khi gửi lại OTP.");
        }
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        otp.setId(key);
        otp.setCodeHash(encoder.encode(code));
        otp.setExpiresAt(now.plusSeconds(ttl));
        otp.setSentAt(now);
        otp.setAttempts(0);
        otp.setConsumed(false);
        otp.setResetTokenHash(null);
        otp.setResetExpiresAt(null);
        otps.save(otp);

        otpEmailService.send(account.getEmail(), code, purpose, ttl);
    }

    @Transactional
    @Override
    public OtpResponse resend(OtpRequest request) {
        accounts.findByEmailForUpdate(request.email()).ifPresent(account -> {
            if ((request.purpose() == OtpPurpose.REGISTER && !account.isEmailVerified())
                    || (request.purpose() == OtpPurpose.FORGOT_PASSWORD && account.isEnabled())) {
                issue(account, request.purpose());
            }
        });
        return new OtpResponse("Nếu tài khoản phù hợp, OTP đã được gửi đến email.");
    }

    // Preserve failed-attempt counters even when returning an invalid-code error.
    @Transactional(noRollbackFor = OtpException.class)
    @Override
    public OtpResponse verify(VerifyOtpRequest request) {
        Account account = accounts.findByEmailForUpdate(request.email()).orElseThrow(this::invalid);
        EmailOtp otp = otps.findById(account.getId() + ":" + request.purpose()).orElseThrow(this::invalid);
        if (otp.isConsumed() || !Instant.now().isBefore(otp.getExpiresAt()) || otp.getAttempts() >= maxAttempts) {
            throw invalid();
        }
        if(account.getEmail() == null){
            throw new IllegalArgumentException("Email này chưa được đăng ký!!");
        }

        if (!encoder.matches(request.otp(), otp.getCodeHash())) {
            otp.setAttempts(otp.getAttempts() + 1);
            throw invalid();
        }
        if (request.purpose() == OtpPurpose.REGISTER) {
            otp.setConsumed(true);
            account.setEmailVerified(true);
            return new OtpResponse("Xác minh email thành công. Vui lòng đăng nhập.");
        }
        if (!account.isEnabled()) throw invalid();
        otp.setConsumed(true);
        String resetToken = UUID.randomUUID().toString();
        otp.setResetTokenHash(encoder.encode(resetToken));
        otp.setResetExpiresAt(Instant.now().plusSeconds(ttl));
        return new OtpResponse("OTP hợp lệ. Sử dụng resetToken để đặt lại mật khẩu.", resetToken);
    }

    @Transactional
    @Override
    public OtpResponse resetPassword(ResetPasswordRequest request) {
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new OtpException(HttpStatus.BAD_REQUEST, "Mật khẩu xác nhận không khớp.");
        }
        Account account = accounts.findByEmailForUpdate(request.email()).orElseThrow(this::invalid);
        EmailOtp otp = otps.findById(account.getId() + ":" + OtpPurpose.FORGOT_PASSWORD).orElseThrow(this::invalid);
        if (!account.isEnabled() || otp.getResetTokenHash() == null || otp.getResetExpiresAt() == null
                || !Instant.now().isBefore(otp.getResetExpiresAt())
                || !encoder.matches(request.resetToken(), otp.getResetTokenHash())) throw invalid();
        account.setPasswordHash(encoder.encode(request.newPassword()));
        otp.setResetTokenHash(null);
        otp.setResetExpiresAt(null);
        refreshTokens.deleteByAccount(account);
        return new OtpResponse("Đặt lại mật khẩu thành công. Vui lòng đăng nhập.");
    }

    private OtpException invalid() {
        return new OtpException(HttpStatus.BAD_REQUEST, "Mã xác thực không hợp lệ, hết hạn hoặc đã sử dụng.");
    }


    @Override
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        Account account = accounts.findByEmailIgnoreCase(request.getEmail()).orElse(null);
        if (account == null) {
            log.warn("forgot: không tìm thấy account cho email {}", request.getEmail());
            return;
        }
        if (!account.isEnabled()) {
            log.warn("forgot: account {} chưa enabled", account.getEmail());
            return;
        }

        String key = account.getId() + ":" + OtpPurpose.FORGOT_PASSWORD;
        EmailOtp otp = otps.findById(key).orElseGet(EmailOtp::new);
        Instant now = Instant.now();

        // Đang trong thời gian chờ: im lặng bỏ qua, KHÔNG ném 429
        // (ném lỗi chỉ với email có thật sẽ lộ email đó tồn tại)
        if (otp.getSentAt() != null && now.isBefore(otp.getSentAt().plusSeconds(resendSeconds))) {
            log.warn("forgot: đang trong thời gian chờ {}s", resendSeconds);
            return;
        }

        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        if (otp.getId() == null) { // dòng mới: vô hiệu hóa phần OTP để không ai dùng được
            otp.setId(key);
            otp.setCodeHash(encoder.encode(UUID.randomUUID().toString()));
            otp.setExpiresAt(now);
            otp.setAttempts(0);
            otp.setConsumed(true);
        }
        otp.setSentAt(now);
        otp.setResetTokenHash(encoder.encode(token));
        otp.setResetExpiresAt(now.plusSeconds(resetLinkTtl));
        otps.save(otp);

        String link = frontendUrl + "/reset-password?email="
                + URLEncoder.encode(account.getEmail(), StandardCharsets.UTF_8)
                + "&token=" + token;

        try {
            otpEmailService.sendResetLink(account.getEmail(), link, resetLinkTtl / 60);
            log.info("forgot: đã gửi mail tới {}", account.getEmail());
        } catch (MailException e) {
            log.error("Không gửi được mail reset password", e);
        }
    }
}
