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

    List<CommunityPostPhoto> findByPost_Id(UUID postId);

    @Query("select p.post.id, p.imageUrl from CommunityPostPhoto p where p.post.id in :postIds")
    List<Object[]> findUrlsByPostIds(@Param("postIds") Collection<UUID> postIds);

    void deleteByPost_Id(UUID postId);
}
