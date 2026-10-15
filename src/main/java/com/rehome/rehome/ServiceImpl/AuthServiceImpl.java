
package com.rehome.rehome.ServiceImpl;

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
import com.rehome.rehome.exception.AccountAlreadyExistsException;
import com.rehome.rehome.exception.AccountNotVerifiedException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.TokenRefreshException;

import com.rehome.rehome.mapper.OrganizationProfileMapper;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.MemberProfileRepository;

import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.security.JwtService;

import com.rehome.rehome.service.AuthService;
import com.rehome.rehome.service.OtpService;
import com.rehome.rehome.service.RefreshTokenService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // ==================== REGISTER ====================

    @Transactional
    @Override
    public OtpResponse register(RegisterRequest request) {

        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new AccountAlreadyExistsException(
                    "Email đã tồn tại: " + request.getEmail()
            );
        }

        Account account = Account.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.MEMBER)
                .status(AccountStatus.ACTIVE)
                .emailVerified(false)
                .phone(request.getPhone())
                .createdAt(Instant.now())
                .build();

        accountRepository.save(account);

        MemberProfile memberProfile = new MemberProfile();
        memberProfile.setAccount(account);
        memberProfile.setMemberCode("MB000001");
        memberProfile.setFullName(request.getFullName());

        memberProfileRepository.save(memberProfile);

        otpService.issue(account, OtpPurpose.REGISTER);

        return new OtpResponse(
                "Đăng ký thành công. Vui lòng kiểm tra email để xác minh OTP."
        );
    }

    // ==================== LOGIN ====================

    @Override
    public AuthResponse login(LoginRequest request) {

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                )
        );

        Account account = accountRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException(
                        "Account không tồn tại sau khi authenticate"
                ));

        if(!account.isEmailVerified()){
            throw new AccountNotVerifiedException("Tài khoản chưa được xác thực. Vui lòng thử lại sau!!");
        }

        return generateAuthResponse(account);
    }

    // ==================== REFRESH TOKEN ====================

    @Transactional
    @Override
    public AuthResponse refreshToken(String requestRefreshToken) {

        RefreshToken refreshToken = refreshTokenService
                .findByToken(requestRefreshToken)
                .orElseThrow(() -> new TokenRefreshException(
                        requestRefreshToken,
                        "Refresh token không tồn tại"
                ));

        refreshTokenService.verifyExpiration(refreshToken);

        Account account = refreshToken.getAccount();

        if (!account.isEnabled()) {
            throw new DisabledException(
                    "Tài khoản chưa xác minh hoặc đã bị vô hiệu hóa."
            );
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
                .orElseThrow(() -> new IllegalStateException(
                        "Account không tồn tại"
                ));

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
                .build();
    }

    @Override
    @Transactional
    public RegisterOrganizationResponse registerOrganization(RegisterOrganizationRequest request) {
        String email = request.getContactEmail().trim().toLowerCase();

        if (accountRepository.existsByEmailIgnoreCase(email)) {
            throw new AccountAlreadyExistsException("Email đã được sử dụng!!");
        }
        if (organizationProfileRepository.existsByTaxCode(request.getTaxCode())) {
            throw new AccountAlreadyExistsException("Mã số thuế đã được sử dụng!!");
        }

        Account account = accountRepository.save(Account.builder()
                .email(email)
                .role(Role.ORGANIZATION)
                .status(AccountStatus.ACTIVE)
                .build());

        OrganizationProfile organization = organizationProfileMapper.toEntity(request);
        organization.setAccount(account);
        organization.setVerificationStatus(VerificationStatus.PENDING);

        organizationProfileRepository.save(organization);
        return organizationProfileMapper.toResponse(organization);
    }
}
