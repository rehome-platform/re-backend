package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.VerificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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


}
