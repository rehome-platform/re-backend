package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ContentStatus;
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

    long countByPost_IdAndStatus(UUID postId, ContentStatus status);
    Page<PostComment> findByPost_IdAndStatus(UUID postId, ContentStatus status, Pageable pageable);

    // F-107: số bình luận còn hiển thị của từng bài, gom 1 query để tránh N+1
    @Query("select pc.post.id, count(pc) from PostComment pc "
            + "where pc.post.id in :postIds and pc.status = :status group by pc.post.id")
    List<Object[]> countByPostIdsAndStatus(@Param("postIds") Collection<UUID> postIds,
                                           @Param("status") ContentStatus status);
}
