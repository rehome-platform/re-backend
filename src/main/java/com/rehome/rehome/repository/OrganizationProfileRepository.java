package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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


}
