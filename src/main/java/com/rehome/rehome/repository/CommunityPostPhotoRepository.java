package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.CommunityPostPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CommunityPostPhotoRepository extends JpaRepository<CommunityPostPhoto, UUID> {
}
