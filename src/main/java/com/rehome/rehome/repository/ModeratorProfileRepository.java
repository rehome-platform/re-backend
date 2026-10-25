package com.rehome.rehome.repository;

import com.rehome.rehome.entity.auth.ModeratorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ModeratorProfileRepository extends JpaRepository<ModeratorProfile, UUID> {

    @Query("""
        select mp from ModeratorProfile mp
        join fetch mp.warehouse
        where mp.accountId = :accountId
        """)
    Optional<ModeratorProfile> findWithWarehouseByAccountId(@Param("accountId") UUID accountId);
}
