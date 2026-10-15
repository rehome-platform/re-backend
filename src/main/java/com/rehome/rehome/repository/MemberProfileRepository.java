package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.MemberProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MemberProfileRepository extends JpaRepository<MemberProfile, UUID> {
    Optional<MemberProfile> findByAccountId(UUID accountId);
}