package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.marketplace.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, String>, JpaSpecificationExecutor<Product> {

    // Khóa dòng để 2 request checkout cùng sản phẩm không cùng vượt qua kiểm tra ON_SALE
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

    List<Product> findAllByNameNormalizedIsNull();

    @Query(value = "SELECT nextval('product_sku_seq')", nativeQuery = true)
    long nextSkuSequence();

    @Query("""
    SELECT p FROM Product p
WHERE p.warehouse.id = :warehouseId
  AND (:status IS NULL OR p.status = :status)
  AND (CAST(:shelfLocation AS string) IS NULL
       OR LOWER(p.shelfLocation) LIKE LOWER(CONCAT('%', CAST(:shelfLocation AS string), '%')))
""")
    Page<Product> findInventory(@Param("warehouseId") UUID warehouseId,
                                @Param("status") ProductStatus status,
                                @Param("shelfLocation") String shelfLocation,
                                Pageable pageable);

}
