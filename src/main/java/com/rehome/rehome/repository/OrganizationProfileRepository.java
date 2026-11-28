package com.rehome.rehome.repository;

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

    List<OrganizationProfile> findAllByVerificationStatusAndVerifiedAtBefore(
            VerificationStatus verificationStatus, Instant verifiedAt);

    List<OrganizationProfile> findAllByVerificationStatus(VerificationStatus verificationStatus);

    List<OrganizationProfile> findAllByDisplayNameNormalizedIsNull();

    @Query("select o from OrganizationProfile o where o.verificationStatus = :status "
            + "and (o.displayNameNormalized like concat('%', :keyword, '%') "
            + "or o.legalNameNormalized like concat('%', :keyword, '%'))")
    Page<OrganizationProfile> search(@Param("status") VerificationStatus status,
                                      @Param("keyword") String keyword, Pageable pageable);
}
