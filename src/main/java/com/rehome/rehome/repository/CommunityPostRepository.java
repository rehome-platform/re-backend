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

    Page<CommunityPost> findByOrganization_AccountIdAndStatus(UUID organizationId, ContentStatus status, Pageable pageable);

    Page<CommunityPost> findByOrganization_AccountIdInAndStatus(Collection<UUID> organizationIds, ContentStatus status, Pageable pageable);

    // F-107: bài viết của chính tổ chức đang đăng nhập
    Page<CommunityPost> findByOrganization_AccountId(UUID organizationId, Pageable pageable);

    // scope=ALL (newsfeed xuyên tổ chức, Guest xem được)
    Page<CommunityPost> findByStatus(ContentStatus status, Pageable pageable);
}
