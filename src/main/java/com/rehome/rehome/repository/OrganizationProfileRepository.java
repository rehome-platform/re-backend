package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.OrganizationProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationProfileRepository extends JpaRepository<OrganizationProfile, Long> {
    boolean existsByTaxCode(String taxCode);
}
