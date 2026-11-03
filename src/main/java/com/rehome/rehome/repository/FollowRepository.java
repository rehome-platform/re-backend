package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.Follow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    // Trang công khai của tổ chức (F-082): số lượt theo dõi
    long countByOrganization_AccountId(UUID organizationId);

    // Như trên nhưng gom nhiều tổ chức 1 query, tránh N+1 khi dựng danh sách (nearby/following)
    @Query("select f.organization.accountId, count(f) from Follow f "
            + "where f.organization.accountId in :organizationIds group by f.organization.accountId")
    List<Object[]> countByOrganizationIds(@Param("organizationIds") Collection<UUID> organizationIds);

    // Tổ chức mà Member đang theo dõi, mới theo dõi gần đây trước
    Page<Follow> findByMember_IdOrderByCreatedAtDesc(UUID memberId, Pageable pageable);
}
