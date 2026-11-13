package com.rehome.rehome.repository;

import com.rehome.rehome.entity.tryon.TryOnImage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TryOnImageRepository extends JpaRepository<TryOnImage, UUID> {

    // Nạp luôn 3 món (quan hệ to-one) để map response không phát sinh N+1
    // Kèm warehouse của từng món để ProductInfo.warehouseId không phát sinh thêm query
    @EntityGraph(attributePaths = {"topProduct", "topProduct.warehouse",
            "bottomProduct", "bottomProduct.warehouse",
            "dressProduct", "dressProduct.warehouse"})
    Page<TryOnImage> findByMember_Id(UUID memberId, Pageable pageable);

    // Xem 1 ảnh: nạp cùng bộ quan hệ với danh sách
    @EntityGraph(attributePaths = {"topProduct", "topProduct.warehouse",
            "bottomProduct", "bottomProduct.warehouse",
            "dressProduct", "dressProduct.warehouse"})
    Optional<TryOnImage> findWithProductsById(UUID id);

    /**
     * Hạn mức phối đồ AI theo gói: đếm số LƯỢT SINH ẢNH trong khoảng [from, to) giờ VN, tức số
     * row đã tạo ra ở bảng này - KHÔNG phải số ảnh còn trong bộ sưu tập. Member xóa ảnh không
     * được hoàn lượt (đặc tả §8.8), nên nếu sau này có xóa mềm thì phải đếm cả dòng đã xóa.
     */
    long countByMember_IdAndCreatedAtBetween(UUID memberId, java.time.Instant from, java.time.Instant to);
}