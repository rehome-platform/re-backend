package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.VerificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationProfileRepository extends JpaRepository<OrganizationProfile, UUID> {

    boolean existsByAccountId(UUID accountId);
    boolean existsByContactEmailIgnoreCase(String contactEmail);
    boolean existsByTaxCode(String taxCode);

    boolean existsByContactEmailIgnoreCaseAndAccountIdNot(String contactEmail, UUID accountId);
    boolean existsByTaxCodeAndAccountIdNot(String taxCode, UUID accountId);
    Optional<OrganizationProfile> findByAccountId(UUID accountId);

    // F-139: hồ sơ đã DUYỆT quá hạn giữ ảnh giấy tờ. Lọc theo status để hồ sơ bị từ chối
    // (cũng có verifiedAt vì đó là mốc "kết luận") không bị xóa ảnh trước khi nộp lại
    List<OrganizationProfile> findAllByVerificationStatusAndVerifiedAtBefore(
            VerificationStatus verificationStatus, Instant verifiedAt);

    // Tổ chức gần vị trí: chỉ tổ chức đã VERIFIED mới công khai (F-082)
    List<OrganizationProfile> findAllByVerificationStatus(VerificationStatus verificationStatus);

    // Hồ sơ đã tồn tại trước khi có cột display_name_normalized - ddl-auto=update chỉ thêm cột
    // với giá trị null, cần initializer điền lại (xem OrganizationNameNormalizeInitializer)
    List<OrganizationProfile> findAllByDisplayNameNormalizedIsNull();

    // GET /api/organizations?search=: tìm theo displayName hoặc legalName, chỉ tổ chức VERIFIED.
    // keyword null -> không lọc theo tên, chỉ còn verificationStatus
    @Query("select o from OrganizationProfile o where o.verificationStatus = :status "
            + "and (:keyword is null or o.displayNameNormalized like concat('%', :keyword, '%') "
            + "or o.legalNameNormalized like concat('%', :keyword, '%'))")
    Page<OrganizationProfile> search(@Param("status") VerificationStatus status,
                                      @Param("keyword") String keyword, Pageable pageable);
}
