package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.RefreshToken;
import com.rehome.rehome.service.RefreshTokenService;

import com.rehome.rehome.exception.TokenRefreshException;
import com.rehome.rehome.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh token được lưu ở DB dưới dạng UUID ngẫu nhiên (không phải JWT) để:
 * - Có thể revoke (thu hồi) bất kỳ lúc nào, ví dụ khi logout hoặc phát hiện bị đánh cắp.
 * - Không cần giải mã / verify chữ ký, chỉ cần tra DB.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.jwt.refresh-token-expiration-ms}")
    private long refreshTokenExpirationMs;

    @Transactional
    @Override
    public RefreshToken createRefreshToken(Account account) {
        RefreshToken refreshToken = RefreshToken.builder()
                .account(account)
                .token(UUID.randomUUID().toString())
                .expiryDate(Instant.now().plusMillis(refreshTokenExpirationMs))
                .revoked(false)
                .build();

        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    public Optional<RefreshToken> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    @Override
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.isRevoked()) {
            throw new TokenRefreshException(token.getToken(), "Token đã bị thu hồi, vui lòng đăng nhập lại");
        }
        if (token.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(token);
            throw new TokenRefreshException(token.getToken(), "Token đã hết hạn, vui lòng đăng nhập lại");
        }
        return token;
    }

    @Transactional
    @Override
    public void revokeAllAccountTokens(Account account) {
        refreshTokenRepository.revokeAllByAccount(account);
    }

    @Transactional
    @Override
    public void deleteByAccount(Account account) {
        refreshTokenRepository.deleteByAccount(account);
    }
}
