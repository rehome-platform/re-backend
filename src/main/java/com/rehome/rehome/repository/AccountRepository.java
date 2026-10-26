package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from Account u where u.email = :email")
    Optional<Account> findByEmailForUpdate(@org.springframework.data.repository.query.Param("email") String email);
    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByRole(Role role);
    long countByRole(Role role);
    Optional<Account> findByEmailIgnoreCase(String email);
    Optional<Account> findFirstByRole(Role role);
    boolean existsByPhone(String phone);

    @Query("""
        select a from Account a
        left join fetch a.warehouse
        where a.id = :accountId
        """)
    Optional<Account> findWithWarehouseById(@Param("accountId") UUID accountId);

    List<Account> findByRole(Role role);
}
