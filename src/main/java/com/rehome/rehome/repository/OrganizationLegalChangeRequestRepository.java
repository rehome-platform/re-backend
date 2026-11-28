package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.OrganizationLegalChangeRequest;
import com.rehome.rehome.entity.enums.LegalChangeRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrganizationLegalChangeRequestRepository
        extends JpaRepository<OrganizationLegalChangeRequest, UUID> {

    Optional<OrganizationLegalChangeRequest> findFirstByOrganization_AccountIdOrderByCreatedAtDesc(
            UUID organizationAccountId);

    boolean existsByOrganization_AccountIdAndStatus(UUID organizationAccountId, LegalChangeRequestStatus status);
}
