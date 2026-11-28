package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ContentStatus;
import com.rehome.rehome.entity.marketplace.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface CommunityPostRepository extends JpaRepository<CommunityPost, UUID> {
    @Modifying(flushAutomatically = true)
    @Query("""
            update CommunityPost p set p.status = com.rehome.rehome.entity.enums.ContentStatus.REMOVED
            where p.organization.accountId = :organizationId
              and p.status <> com.rehome.rehome.entity.enums.ContentStatus.REMOVED
            """)
    int hidePostsOfBannedOrganization(@Param("organizationId") UUID organizationId);


    Page<CommunityPost> findByReviewStatus(com.rehome.rehome.entity.enums.ContentReviewStatus reviewStatus,
                                     Pageable pageable);

    @Query("select p from CommunityPost p where p.organization.accountId = :organizationId and p.status = :status "
            + "and p.organization.account.status <> com.rehome.rehome.entity.enums.AccountStatus.BANNED")
    Page<CommunityPost> findByOrganization_AccountIdAndStatus(@Param("organizationId") UUID organizationId,
                                                           @Param("status") ContentStatus status, Pageable pageable);

    @Query("select p from CommunityPost p where p.organization.accountId in :organizationIds and p.status = :status "
            + "and p.organization.account.status <> com.rehome.rehome.entity.enums.AccountStatus.BANNED")
    Page<CommunityPost> findByOrganization_AccountIdInAndStatus(@Param("organizationIds") Collection<UUID> organizationIds,
                                                             @Param("status") ContentStatus status, Pageable pageable);

    Page<CommunityPost> findByOrganization_AccountId(UUID organizationId, Pageable pageable);

    @Query("select p from CommunityPost p where p.status = :status "
            + "and p.organization.account.status <> com.rehome.rehome.entity.enums.AccountStatus.BANNED")
    Page<CommunityPost> findByStatus(@Param("status") ContentStatus status, Pageable pageable);
}
