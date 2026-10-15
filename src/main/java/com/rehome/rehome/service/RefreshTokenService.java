package com.rehome.rehome.service;

import com.rehome.rehome.entity.RefreshToken;
import com.rehome.rehome.entity.Account;
import java.util.Optional;

public interface RefreshTokenService {

    RefreshToken createRefreshToken(Account account);

    Optional<RefreshToken> findByToken(String token);

    RefreshToken verifyExpiration(RefreshToken token);

    void revokeAllAccountTokens(Account account);

    void deleteByAccount(Account account);
}
