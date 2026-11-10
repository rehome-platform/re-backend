package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ProductPriceChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ProductPriceChangeRepository extends JpaRepository<ProductPriceChange, UUID> {
    List<ProductPriceChange> findByProduct_IdOrderByChangedAtAsc(String productId);

    // Số lần đã đổi giá của sản phẩm trong khoảng [from, to), dùng để giới hạn số lần giảm giá mỗi ngày (API 23)
    long countByProduct_IdAndChangedAtGreaterThanEqualAndChangedAtLessThan(String productId, Instant from, Instant to);
}