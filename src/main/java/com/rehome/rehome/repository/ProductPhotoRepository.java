package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ProductPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductPhotoRepository extends JpaRepository<ProductPhoto, UUID> {
    List<ProductPhoto> findByProduct_IdInOrderByIdAsc(List<String> productIds);
}
