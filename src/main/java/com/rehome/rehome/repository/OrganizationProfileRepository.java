package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrganizationProfileRepository extends JpaRepository<OrganizationProfile, Long> {
    boolean existsByTaxCode(String taxCode);


    // Cách 1: truyền cả entity Account
    boolean existsByAccount(Account account);

    // Cách 2: truyền id của Account (không cần load entity)
    boolean existsByAccountId(UUID accountId);
}
