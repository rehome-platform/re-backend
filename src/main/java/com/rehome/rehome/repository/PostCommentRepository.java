package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {

    Page<PostComment> findByReviewStatus(com.rehome.rehome.entity.enums.ContentReviewStatus reviewStatus,
                                     Pageable pageable);

    // Mọi bình luận của 1 bài, kể cả REMOVED (vẫn trả về, chỉ bỏ content ở tầng response - OB-05)
    long countByPost_Id(UUID postId);
    Page<PostComment> findByPost_Id(UUID postId, Pageable pageable);

    // F-107: số bình luận của từng bài, gom 1 query để tránh N+1
    @Query("select pc.post.id, count(pc) from PostComment pc where pc.post.id in :postIds group by pc.post.id")
    List<Object[]> countByPostIds(@Param("postIds") Collection<UUID> postIds);
}
