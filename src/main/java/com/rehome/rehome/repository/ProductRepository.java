package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.TryOnSlot;
import com.rehome.rehome.entity.marketplace.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, String>, JpaSpecificationExecutor<Product> {

    @EntityGraph(attributePaths = {"category", "brand", "warehouse", "consignmentReceipt"})
    @Query("""
            select p from Product p where p.status = com.rehome.rehome.entity.enums.ProductStatus.ON_SALE
              and p.category.tryonSlot = :slot and p.id <> :selectedId and p.member.id <> :memberId
            order by p.createdAt desc, p.id asc
            """)
    List<Product> findMlOutfitCandidates(@Param("slot") TryOnSlot slot, @Param("selectedId") String selectedId,
            @Param("memberId") UUID memberId, Pageable pageable);
    @Override
    @EntityGraph(attributePaths = {"brand", "category", "warehouse", "consignmentReceipt"})
    Page<Product> findAll(org.springframework.data.jpa.domain.Specification<Product> specification,
                          Pageable pageable);

    boolean existsByCategory_Id(UUID categoryId);

    boolean existsByBrand_Id(UUID brandId);


    @Query("select count(p) from Product p where p.warehouse.id = :warehouseId and p.status in :statuses")
    long countByWarehouseAndStatuses(@Param("warehouseId") UUID warehouseId,
                                     @Param("statuses") Collection<ProductStatus> statuses);


    @EntityGraph(attributePaths = {"brand", "category", "warehouse", "consignmentReceipt"})
    Slice<Product> findByStatusAndCategory_TryonSlot(
            ProductStatus status, TryOnSlot slot, Pageable pageable);

    @EntityGraph(attributePaths = {"category", "warehouse"})
    List<Product> findWithCategoryByIdIn(Collection<String> ids);

    @EntityGraph(attributePaths = {"brand", "category", "warehouse", "consignmentReceipt"})
    Optional<Product> findWithDetailsById(String id);

    @EntityGraph(attributePaths = {"brand", "category", "warehouse", "consignmentReceipt"})
    @Query("""
        SELECT p FROM Product p
        WHERE p.status = com.rehome.rehome.entity.enums.ProductStatus.ON_SALE
          AND p.category.tryonSlot = :slot
          AND p.id <> :excludeProductId
        ORDER BY CASE WHEN p.warehouse.id = :preferredWarehouseId THEN 0 ELSE 1 END,
                 p.createdAt DESC
        """)
    List<Product> findTryOnSuggestions(@Param("slot") TryOnSlot slot,
                                       @Param("excludeProductId") String excludeProductId,
                                       @Param("preferredWarehouseId") UUID preferredWarehouseId,
                                       Pageable pageable);

    @Query("SELECT p.warehouse.id FROM Product p WHERE p.id = :id")
    Optional<UUID> findWarehouseIdById(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p " +
            "join fetch p.warehouse " +
            "join fetch p.consignmentReceipt " +
            "where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") String id);

    @Query("""
        SELECT p FROM Product p
        WHERE p.member.id = :memberId
          AND (:status IS NULL OR p.status = :status)
        """)
    Page<Product> findMine(@Param("memberId") UUID memberId,
                           @Param("status") ProductStatus status,
                           Pageable pageable);
    boolean existsByConsignmentReceiptId(UUID consignmentReceiptId);

    Optional<Product> findByConsignmentReceiptId(UUID consignmentReceiptId);

    interface FormProductId {
        UUID getFormId();
        String getProductId();
    }

    @Query("""
            select p.consignmentReceipt.consignmentForm.id as formId, min(p.id) as productId
            from Product p
            where p.consignmentReceipt.consignmentForm.id in :formIds
            group by p.consignmentReceipt.consignmentForm.id
            having count(p) = 1
            """)
    List<FormProductId> findProductIdsByFormIds(@Param("formIds") Collection<UUID> formIds);

    List<Product> findByConsignmentReceipt_IdIn(Collection<UUID> receiptIds);

    List<Product> findAllByNameNormalizedIsNull();

    @Query(value = "SELECT nextval('product_sku_seq')", nativeQuery = true)
    long nextSkuSequence();

    @EntityGraph(attributePaths = {"category", "consignmentReceipt"})
    @Query("""
    SELECT p FROM Product p
WHERE p.warehouse.id = :warehouseId
  AND (:status IS NULL OR p.status = :status)
  AND (:degradedOnly = false OR EXISTS (
        SELECT 1 FROM DegradedItemCase c WHERE c.product = p AND c.choice IS NULL))
""")
    Page<Product> findInventory(@Param("warehouseId") UUID warehouseId,
                                @Param("status") ProductStatus status,
                                @Param("degradedOnly") boolean degradedOnly,
                                Pageable pageable);

    @EntityGraph(attributePaths = {"brand", "warehouse", "consignmentReceipt"})
    @Query("""
    SELECT p FROM Product p
    WHERE p.member.id = :memberId
      AND (:status IS NULL OR p.status = :status)
      AND (:pendingDecision = false OR EXISTS (
            SELECT 1 FROM EndOfTermDecision d
            WHERE d.product = p AND d.decision IS NULL))
    """)
    Page<Product> findMine(@Param("memberId") UUID memberId,
                           @Param("status") ProductStatus status,
                           @Param("pendingDecision") boolean pendingDecision,
                           Pageable pageable);

    @Query("select p from Product p join fetch p.consignmentReceipt r "
            + "where p.status = com.rehome.rehome.entity.enums.ProductStatus.ON_SALE "
            + "and p.currentTerm <= r.maxRenewals")
    List<Product> findOnSaleNotYetFinalTerm();

    List<Product> findAllByStatusAndEndOfTermDeadlineBefore(ProductStatus status, Instant deadline);

    List<Product> findAllByMember_IdAndStatusIn(UUID memberId, Collection<ProductStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.featuredUntil = null, p.featuredRank = null where p.member.id = :memberId "
            + "and p.featuredUntil is not null")
    int clearFeaturedByMemberId(@Param("memberId") UUID memberId);

    @Query("select count(p) from Product p where p.member.id = :memberId "
            + "and p.featuredUntil is not null and p.featuredUntil > :now")
    long countFeaturedByMemberId(@Param("memberId") UUID memberId, @Param("now") Instant now);
}
