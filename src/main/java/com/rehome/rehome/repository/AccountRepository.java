package com.rehome.rehome.repository;

import com.rehome.rehome.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from Account u where u.email = :email")
    Optional<Account> findByEmailForUpdate(@org.springframework.data.repository.query.Param("email") String email);
    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);
}
