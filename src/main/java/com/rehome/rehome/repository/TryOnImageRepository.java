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
    @org.springframework.data.jpa.repository.Query("select i from TryOnImage i where i.member.id = :memberId and i.deletedAt is null")
    Page<TryOnImage> findByMember_Id(UUID memberId, Pageable pageable);

    // Xem 1 ảnh: nạp cùng bộ quan hệ với danh sách
    @EntityGraph(attributePaths = {"topProduct", "topProduct.warehouse",
            "bottomProduct", "bottomProduct.warehouse",
            "dressProduct", "dressProduct.warehouse"})
    @org.springframework.data.jpa.repository.Query("select i from TryOnImage i where i.id = :id and i.deletedAt is null")
    Optional<TryOnImage> findWithProductsById(UUID id);

    @EntityGraph(attributePaths = {"topProduct", "topProduct.warehouse",
            "bottomProduct", "bottomProduct.warehouse", "dressProduct", "dressProduct.warehouse"})
    Optional<TryOnImage> findFirstByMember_IdAndGenerationFingerprintAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID memberId, String generationFingerprint);

    /**
     * Hạn mức phối đồ AI theo gói: đếm số LƯỢT SINH ẢNH trong khoảng [from, to) giờ VN, tức số
     * row quotaCharged=true, kể cả dòng xóa mềm; loại row dùng lại tài nguyên chung.
     * Không tính theo số ảnh còn trong bộ sưu tập; xóa mục chung không hoàn lượt (đặc tả §8.8).
     */
    @org.springframework.data.jpa.repository.Query("""
            select count(i) from TryOnImage i where i.member.id = :memberId
              and i.quotaCharged = true and i.createdAt >= :from and i.createdAt < :to
            """)
    long countByMember_IdAndCreatedAtBetween(UUID memberId, java.time.Instant from, java.time.Instant to);
}
