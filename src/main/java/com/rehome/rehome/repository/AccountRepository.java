package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Account u where u.email = :email")
    Optional<Account> findByEmailForUpdate(@Param("email") String email);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from Account u where u.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
    @EntityGraph(attributePaths = {"warehouse"})
    Optional<Account> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByRole(Role role);
    long countByRole(Role role);
    @EntityGraph(attributePaths = {"warehouse"})
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

    @Query("""
        select a from Account a
        where (:role is null or a.role = :role)
          and (:status is null or a.status = :status)
          and a.createdAt >= :from and a.createdAt < :to
          and (lower(a.email) like :pattern
               or a.phone like :pattern
               or exists (select 1 from MemberProfile m
                          where m.accountId = a.id and lower(m.fullName) like :pattern)
               or exists (select 1 from ModeratorProfile mo
                          where mo.accountId = a.id and lower(mo.fullName) like :pattern)
               or exists (select 1 from OrganizationProfile o
                          where o.accountId = a.id
                            and (lower(o.displayName) like :pattern or lower(o.legalName) like :pattern)))
        """)
    Page<Account> searchForAdmin(@Param("role") Role role,
                                 @Param("status") AccountStatus status,
                                 @Param("pattern") String pattern,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to,
                                 Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "warehouse")
    Page<Account> findAll(Specification<Account> spec, Pageable pageable);

    @EntityGraph(attributePaths = "warehouse")
    Page<Account> findByRole(Role role, Pageable pageable);

    @EntityGraph(attributePaths = "warehouse")
    Page<Account> findByRoleAndWarehouse_Id(Role role, UUID warehouseId, Pageable pageable);

    List<Account> findByRoleAndWarehouse_Id(Role role, UUID warehouseId);

    @Query("""
        select a.id from Account a
        where exists (select 1 from MemberProfile m
                      where m.accountId = a.id and lower(m.fullName) like :pattern)
           or exists (select 1 from ModeratorProfile mo
                      where mo.accountId = a.id and lower(mo.fullName) like :pattern)
           or exists (select 1 from OrganizationProfile o
                      where o.accountId = a.id
                        and (lower(o.displayName) like :pattern or lower(o.legalName) like :pattern))
        """)
    List<UUID> findIdsByProfileNameLike(@Param("pattern") String pattern, Pageable pageable);
}
