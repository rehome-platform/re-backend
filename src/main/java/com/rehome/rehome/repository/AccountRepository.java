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
    @Query("select u from Account u where u.email = :email")
    Optional<Account> findByEmailForUpdate(@Param("email") String email);
    // Khoá dòng Member để các thao tác gửi form của cùng một người chạy lần lượt (kiểm tra giới hạn chờ duyệt)
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Account u where u.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByRole(Role role);
    long countByRole(Role role);
    Optional<Account> findByEmailIgnoreCase(String email);
    Optional<Account> findFirstByRole(Role role);
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndIdNot(String phone, UUID id);

    @Query("""
        select a from Account a
        left join fetch a.warehouse
        where a.id = :accountId
        """)
    Optional<Account> findWithWarehouseById(@Param("accountId") UUID accountId);

    List<Account> findByRole(Role role);
}
