package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.CommunityPostPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface CommunityPostPhotoRepository extends JpaRepository<CommunityPostPhoto, UUID> {

    // OB-04: ảnh của 1 bài viết
    List<CommunityPostPhoto> findByPost_Id(UUID postId);

    // OB-01: ảnh của nhiều bài viết, gom 1 query để tránh N+1 khi dựng danh sách
    @Query("select p.post.id, p.imageUrl from CommunityPostPhoto p where p.post.id in :postIds")
    List<Object[]> findUrlsByPostIds(@Param("postIds") Collection<UUID> postIds);

    // OB-03: sửa bài thay toàn bộ ảnh cũ bằng danh sách mới
    void deleteByPost_Id(UUID postId);
}
