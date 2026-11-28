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

    @EntityGraph(attributePaths = {"topProduct", "topProduct.warehouse",
            "bottomProduct", "bottomProduct.warehouse",
            "dressProduct", "dressProduct.warehouse"})
    @org.springframework.data.jpa.repository.Query("select i from TryOnImage i where i.member.id = :memberId and i.deletedAt is null")
    Page<TryOnImage> findByMember_Id(UUID memberId, Pageable pageable);

    @EntityGraph(attributePaths = {"topProduct", "topProduct.warehouse",
            "bottomProduct", "bottomProduct.warehouse",
            "dressProduct", "dressProduct.warehouse"})
    @org.springframework.data.jpa.repository.Query("select i from TryOnImage i where i.id = :id and i.deletedAt is null")
    Optional<TryOnImage> findWithProductsById(UUID id);

    @EntityGraph(attributePaths = {"topProduct", "topProduct.warehouse",
            "bottomProduct", "bottomProduct.warehouse", "dressProduct", "dressProduct.warehouse"})
    Optional<TryOnImage> findFirstByMember_IdAndGenerationFingerprintAndDeletedAtIsNullOrderByCreatedAtDesc(
            UUID memberId, String generationFingerprint);

    @org.springframework.data.jpa.repository.Query("""
            select count(i) from TryOnImage i where i.member.id = :memberId
              and i.quotaCharged = true and i.createdAt >= :from and i.createdAt < :to
            """)
    long countByMember_IdAndCreatedAtBetween(UUID memberId, java.time.Instant from, java.time.Instant to);
}
