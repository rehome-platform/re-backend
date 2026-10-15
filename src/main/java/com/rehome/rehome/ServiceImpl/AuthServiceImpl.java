package com.rehome.rehome.ServiceImpl;

import com.rehome.rehome.service.AuthService;
import com.rehome.rehome.service.OtpService;
import com.rehome.rehome.service.RefreshTokenService;

import com.rehome.rehome.dto.response.AuthResponse;
import com.rehome.rehome.dto.request.LoginRequest;
import com.rehome.rehome.dto.request.RegisterRequest;
import com.rehome.rehome.dto.response.OtpResponse;
import com.rehome.rehome.entity.OtpPurpose;
import org.springframework.security.authentication.DisabledException;
import com.rehome.rehome.entity.RefreshToken;
import com.rehome.rehome.entity.Role;
import com.rehome.rehome.entity.Account;
import com.rehome.rehome.entity.AccountStatus;
import com.rehome.rehome.exception.TokenRefreshException;
import com.rehome.rehome.exception.AccountAlreadyExistsException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final OtpService otpService;

    @Transactional
    @Override
    public OtpResponse register(RegisterRequest request) {
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new AccountAlreadyExistsException("Email đã tồn tại: " + request.getEmail());
        }

        Account account = Account.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(Role.MEMBER)
                .status(AccountStatus.ACTIVE)
                .emailVerified(false)
                .build();

        accountRepository.save(account);

        otpService.issue(account, OtpPurpose.REGISTER);
        return new OtpResponse("Đăng ký thành công. Vui lòng kiểm tra email để xác minh OTP.");
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        // Nếu sai email/password, AuthenticationManager sẽ ném BadCredentialsException
        // -> được xử lý ở GlobalExceptionHandler
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalStateException("Account không tồn tại sau khi authenticate"));

        return generateAuthResponse(account);
    }

    @Transactional
    @Override
    public AuthResponse refreshToken(String requestRefreshToken) {
        RefreshToken refreshToken = refreshTokenService.findByToken(requestRefreshToken)
                .orElseThrow(() -> new TokenRefreshException(requestRefreshToken, "Refresh token không tồn tại"));

        refreshTokenService.verifyExpiration(refreshToken);

        Account account = refreshToken.getAccount();
        if (!account.isEnabled()) {
            throw new DisabledException("Tài khoản chưa xác minh hoặc đã bị vô hiệu hóa.");
        }
        String newAccessToken = jwtService.generateAccessToken(account);

        // Rotate refresh token: xoá token cũ, cấp token mới để giảm rủi ro nếu token bị lộ
        refreshTokenService.deleteByAccount(account);
        RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(account);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.getToken())
                .build();
    }

    @Transactional
    @Override
    public void logout(String email) {
        Account account = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Account không tồn tại"));
        refreshTokenService.deleteByAccount(account);
    }

    private AuthResponse generateAuthResponse(Account account) {
        String accessToken = jwtService.generateAccessToken(account);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(account);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .build();
    }
}
