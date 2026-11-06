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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {
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

    // API 135: tìm theo email, số điện thoại hoặc họ tên/tên tổ chức (:pattern đã là dạng %từ khoá% chữ thường,
    // "%" nếu không tìm; từ khoá giống số điện thoại đã được chuẩn hóa về dạng 0xxxxxxxxx).
    // role/status null = không lọc.
    @Query("""
        select a from Account a
        where (:role is null or a.role = :role)
          and (:status is null or a.status = :status)
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
                                 Pageable pageable);

    // API 139: danh sách Moderator (search/status/warehouse qua AccountSpecifications.moderators),
    // nạp luôn kho để không N+1
    @Override
    @EntityGraph(attributePaths = "warehouse")
    Page<Account> findAll(Specification<Account> spec, Pageable pageable);

    @EntityGraph(attributePaths = "warehouse")
    Page<Account> findByRole(Role role, Pageable pageable);

    @EntityGraph(attributePaths = "warehouse")
    Page<Account> findByRoleAndWarehouse_Id(Role role, UUID warehouseId, Pageable pageable);

    // Push chat (MEMBER_WAREHOUSE): tất cả Moderator đang phụ trách kho đó - hộp thư là chung,
    // không phải 1 người cụ thể, nên phải báo hết.
    List<Account> findByRoleAndWarehouse_Id(Role role, UUID warehouseId);

    // Audit log search: id các tài khoản có họ tên Member/Moderator hoặc tên tổ chức khớp :pattern
    // (dạng %từ khoá% chữ thường). Giới hạn bằng Pageable để từ khoá quá ngắn không kéo cả bảng.
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
