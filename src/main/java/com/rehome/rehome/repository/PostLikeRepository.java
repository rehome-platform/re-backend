package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

    long countByPost_Id(UUID postId);

    boolean existsByPost_IdAndMember_Id(UUID postId, UUID memberId);
    Optional<PostLike> findByPost_IdAndMember_Id(UUID postId, UUID memberId);

    // F-107: số like của từng bài, gom 1 query để tránh N+1 khi dựng danh sách bài viết
    @Query("select pl.post.id, count(pl) from PostLike pl where pl.post.id in :postIds group by pl.post.id")
    List<Object[]> countByPostIds(@Param("postIds") Collection<UUID> postIds);
}
