package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.RefreshToken;
import com.rehome.rehome.service.RefreshTokenService;

import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
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
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<RefreshToken> findByTokenForUpdate(String token) {
        return refreshTokenRepository.findByTokenForUpdate(token);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public RefreshToken rotateRefreshToken(RefreshToken token) {
        verifyExpiration(token);
        // Update only this session's locked row. Other devices' tokens remain valid.
        token.setToken(UUID.randomUUID().toString());
        token.setExpiryDate(Instant.now().plusMillis(refreshTokenExpirationMs));
        return refreshTokenRepository.save(token);
    }

    @Override
    public RefreshToken verifyExpiration(RefreshToken token) {
        if (token.isRevoked()) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_REVOKED);
        }
        if (token.getExpiryDate() == null || !token.getExpiryDate().isAfter(Instant.now())) {
            throw new AppException(ErrorCode.REFRESH_TOKEN_EXPIRED);
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
