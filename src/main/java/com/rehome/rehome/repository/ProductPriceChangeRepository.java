package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ProductPriceChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductPriceChangeRepository extends JpaRepository<ProductPriceChange, UUID> {
    List<ProductPriceChange> findByProduct_IdOrderByChangedAtAsc(String productId);
}
