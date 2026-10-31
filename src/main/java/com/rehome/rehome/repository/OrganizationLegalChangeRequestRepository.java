package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.OrganizationLegalChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrganizationLegalChangeRequestRepository
        extends JpaRepository<OrganizationLegalChangeRequest, UUID> {
}
