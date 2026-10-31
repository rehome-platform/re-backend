package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ContentStatus;
import com.rehome.rehome.entity.marketplace.CommunityPost;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.UUID;

@Repository
public interface CommunityPostRepository extends JpaRepository<CommunityPost, UUID> {

    Page<CommunityPost> findByOrganization_AccountIdAndStatus(UUID organizationId, ContentStatus status, Pageable pageable);

    Page<CommunityPost> findByOrganization_AccountIdInAndStatus(Collection<UUID> organizationIds, ContentStatus status, Pageable pageable);

    // F-107: bài viết của chính tổ chức đang đăng nhập
    Page<CommunityPost> findByOrganization_AccountId(UUID organizationId, Pageable pageable);
}
