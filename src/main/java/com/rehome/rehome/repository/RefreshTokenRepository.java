package com.rehome.rehome.repository;


import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.swing.text.html.Option;
import java.util.Optional;
@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RefreshToken r where r.token = :token")
    Optional<RefreshToken> findByTokenForUpdate(@org.springframework.data.repository.query.Param("token") String token);

    Optional<RefreshToken> findByAccount(Account account);

    @Modifying
    @Transactional
    @Query("update RefreshToken r set r.revoked = true where r.account = :account")
    void revokeAllByAccount(Account account);

    void deleteByAccount(Account account);
}
