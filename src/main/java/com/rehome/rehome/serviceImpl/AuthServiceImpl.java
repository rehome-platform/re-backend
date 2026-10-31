
package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.*;
import com.rehome.rehome.dto.response.AccountBannedResult;
import com.rehome.rehome.dto.response.AuthResponse;
import com.rehome.rehome.dto.response.OtpResponse;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;

import com.rehome.rehome.entity.auth.RefreshToken;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.OtpPurpose;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.exception.*;

import com.rehome.rehome.mapper.OrganizationProfileMapper;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.MemberProfileRepository;

import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.security.GoogleTokenVerifier;
import com.rehome.rehome.security.JwtService;

import com.rehome.rehome.service.AuthService;
import com.rehome.rehome.service.OtpService;
import com.rehome.rehome.service.RefreshTokenService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;
    private final MemberProfileRepository memberProfileRepository;
    private final OrganizationProfileRepository organizationProfileRepository;
    private final OrganizationProfileMapper organizationProfileMapper;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final WalletRepository walletRepository;

    // ==================== REGISTER ====================

    @Transactional
    @Override
    public OtpResponse registerMember(RegisterRequest request) {

        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String phone = normalizePhone(request.getPhone());
        if (phone != null && accountRepository.existsByPhone(phone)) {
            throw new AppException(ErrorCode.PHONE_TAKEN);
        }

        Account account = Account.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.MEMBER)
                .status(AccountStatus.ACTIVE)
                .emailVerified(false)
                .phone(phone)
                .createdAt(Instant.now())
                .build();

        accountRepository.save(account);

        MemberProfile memberProfile = new MemberProfile();
        memberProfile.setAccount(account);
        memberProfile.setFullName(request.getFullName());

        memberProfileRepository.save(memberProfile);

        walletRepository.save(Wallet.builder().member(account).build());

        otpService.issue(account, OtpPurpose.REGISTER);

        return new OtpResponse(
                "Đăng ký thành công. Vui lòng kiểm tra email để xác minh OTP."
        );
    }

    @Transactional
    @Override
    public OtpResponse registerOrganization(RegisterRequest request) {

        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        String phone = normalizePhone(request.getPhone());
        if (phone != null && accountRepository.existsByPhone(phone)) {
            throw new AppException(ErrorCode.PHONE_TAKEN);
        }

        Account account = Account.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.ORGANIZATION)
                .status(AccountStatus.ACTIVE)
                .emailVerified(false)
                .phone(phone)
                .createdAt(Instant.now())
                .build();

        accountRepository.save(account);

        otpService.issue(account, OtpPurpose.REGISTER);

        return new OtpResponse(
                "Đăng ký thành công. Vui lòng kiểm tra email để xác minh OTP."
        );
    }

    // Rỗng/chỉ khoảng trắng -> null. Nếu không, hai tài khoản cùng để trống sẽ đụng ràng buộc unique của phone,
    // và existsByPhone(null) của Spring Data thành "phone is null" nên báo trùng sai.
    private String normalizePhone(String phone) {
        return StringUtils.hasText(phone) ? phone.trim() : null;
    }

    // 403 ACCOUNT_BANNED kèm lý do khoá trong result
    private AppException bannedException(Account account) {
        return new AppException(ErrorCode.ACCOUNT_BANNED, new AccountBannedResult(account.getBanReason()));
    }

    // ==================== LOGIN ====================

    @Override
    public AuthResponse login(LoginRequest request) {

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (DisabledException e) {              // phải catch trước AuthenticationException
            throw accountRepository.findByEmail(request.getEmail())
                    .map(this::bannedException)
                    .orElseGet(() -> new AppException(ErrorCode.ACCOUNT_BANNED));
        } catch (AuthenticationException e) {
            throw new AppException(ErrorCode.INVALID_CREDENTIALS);
        }

        Account account = accountRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw bannedException(account);
        }

        if (!account.isEmailVerified()) {
            throw new AppException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        return generateAuthResponse(account);
    }

    @Transactional
    @Override
    public AuthResponse loginWithGoogle(GoogleLoginRequest request) {

        GoogleTokenVerifier.GoogleUserInfo info =
                googleTokenVerifier.verify(request.getIdToken());

        Account account = accountRepository.findByEmail(info.email()).orElse(null);

        if (account == null) {
            // Tài khoản mới
            account = accountRepository.saveAndFlush(Account.builder()
                    .email(info.email())
                    .googleId(info.googleId())
                    .role(Role.MEMBER)
                    .status(AccountStatus.ACTIVE)
                    .emailVerified(true)
                    .build());

            MemberProfile memberProfile = new MemberProfile();
            memberProfile.setAccount(account);
            memberProfile.setFullName(info.name());
            memberProfileRepository.save(memberProfile);

            walletRepository.save(Wallet.builder().member(account).build());

        } else {
            // Tài khoản đã tồn tại
            if (account.getStatus() != AccountStatus.ACTIVE) {
                throw bannedException(account);
            }

            if (account.getGoogleId() == null) {
                account.setGoogleId(info.googleId());
            } else if (!account.getGoogleId().equals(info.googleId())) {
                throw new AppException(ErrorCode.GOOGLE_ACCOUNT_CONFLICT);
            }

            if (!account.isEmailVerified()) {
                // Xóa mật khẩu cũ để người đăng ký giả mạo trước đó không dùng được nữa
                account.setPasswordHash(null);
                account.setEmailVerified(true);
                refreshTokenService.revokeAllAccountTokens(account);
            }

            accountRepository.saveAndFlush(account);
        }

        return generateAuthResponse(account);
    }

    // ==================== REFRESH TOKEN ====================

    @Transactional
    @Override
    public AuthResponse refreshToken(String requestRefreshToken) {

        RefreshToken refreshToken = refreshTokenService
                .findByToken(requestRefreshToken)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        refreshTokenService.verifyExpiration(refreshToken);

        Account account = refreshToken.getAccount();

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw bannedException(account);
        }

        String newAccessToken =
                jwtService.generateAccessToken(account);

        // Rotate refresh token
        refreshTokenService.deleteByAccount(account);

        RefreshToken newRefreshToken =
                refreshTokenService.createRefreshToken(account);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .build();
    }

    // ==================== LOGOUT ====================

    @Transactional
    @Override
    public void logout(String email) {

        Account account = accountRepository
                .findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));

        refreshTokenService.deleteByAccount(account);
    }

    // ==================== GENERATE AUTH RESPONSE ====================

    private AuthResponse generateAuthResponse(Account account) {

        String accessToken =
                jwtService.generateAccessToken(account);

        RefreshToken refreshToken =
                refreshTokenService.createRefreshToken(account);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .role(account.getRole())
                .id(account.getId())
                .is_verified(account.isEmailVerified())
                .build();
    }

    @Override
    public void changePassword(Account account, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getNewPasswordRepeat())) {
            throw new AppException(ErrorCode.PASSWORD_CONFIRM_MISMATCH);
        }
        if (account.getPasswordHash() == null) {
            throw new AppException(ErrorCode.PASSWORD_NOT_SET);
        }
        if (!passwordEncoder.matches(request.getOldPassword(), account.getPassword())) {
            throw new AppException(ErrorCode.WRONG_PASSWORD);
        }
        if (passwordEncoder.matches(request.getNewPassword(), account.getPassword())) {
            throw new AppException(ErrorCode.NEW_PASSWORD_SAME_AS_OLD);
        }
        account.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        accountRepository.save(account);
        refreshTokenService.revokeAllAccountTokens(account);
    }

}
