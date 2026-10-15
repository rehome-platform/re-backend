package com.rehome.rehome.repository;

import com.rehome.rehome.entity.RefreshToken;
import com.rehome.rehome.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Transactional
    @Query("update RefreshToken r set r.revoked = true where r.account = :account")
    void revokeAllByAccount(Account account);

    void deleteByAccount(Account account);
}
