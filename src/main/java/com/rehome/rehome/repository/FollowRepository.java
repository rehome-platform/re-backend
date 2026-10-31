package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.Follow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FollowRepository extends JpaRepository<Follow, UUID> {

    boolean existsByMember_IdAndOrganization_AccountId(UUID memberId, UUID organizationId);
    Optional<Follow> findByMember_IdAndOrganization_AccountId(UUID memberId, UUID organizationId);

    // Newsfeed (F-086): id các tổ chức mà Member đang theo dõi
    @Query("select f.organization.accountId from Follow f where f.member.id = :memberId")
    List<UUID> findFollowedOrganizationIds(@Param("memberId") UUID memberId);
}
