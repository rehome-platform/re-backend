package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.TryOnSlot;
import com.rehome.rehome.entity.marketplace.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, String>, JpaSpecificationExecutor<Product> {
    // DELETE /categories/{id}, /brands/{id}: chặn xóa khi còn sản phẩm tham chiếu
    boolean existsByCategory_Id(UUID categoryId);

    boolean existsByBrand_Id(UUID brandId);


    // API 144: còn hàng nào đang nằm ở kho theo các trạng thái này không
    @Query("select count(p) from Product p where p.warehouse.id = :warehouseId and p.status in :statuses")
    long countByWarehouseAndStatuses(@Param("warehouseId") UUID warehouseId,
                                     @Param("statuses") Collection<ProductStatus> statuses);


    // API 97: nạp luôn category (cần tryonSlot) để service kiểm slot mà không phụ thuộc session
    // Nạp luôn warehouse để TryOnImageResponse.ProductInfo.warehouseId không chạm lazy proxy sau khi hết session
    @EntityGraph(attributePaths = {"category", "warehouse"})
    List<Product> findWithCategoryByIdIn(Collection<String> ids);

    // API 96: món còn bán ở đúng slot, khác món đang xem. Cùng kho xếp trước (đỡ tách đơn), rồi mới nhất trước.
    // preferredWarehouseId luôn khác null: không biết kho thì truyền UUID không khớp kho nào, thứ tự rơi về createdAt.
    // Giới hạn số món bằng Pageable (không kèm Sort, thứ tự đã nằm trong query).
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

    // Kho của một sản phẩm, đọc thẳng cột FK nên không phải nạp entity
    @Query("SELECT p.warehouse.id FROM Product p WHERE p.id = :id")
    Optional<UUID> findWarehouseIdById(@Param("id") String id);

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

    // Lấy SKU để hiện trên biên nhận sau khi đã nhập kho
    Optional<Product> findByConsignmentReceiptId(UUID consignmentReceiptId);

    // Moderator: lấy sản phẩm của nhiều biên nhận trong một query
    List<Product> findByConsignmentReceipt_IdIn(Collection<UUID> receiptIds);

    List<Product> findAllByNameNormalizedIsNull();

    @Query(value = "SELECT nextval('product_sku_seq')", nativeQuery = true)
    long nextSkuSequence();

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

    // F-029: còn ON_SALE và chưa tới kỳ cuối (maxRenewals là snapshot trên biên nhận, không phải
    // cấu hình sống, nên phải join mới so sánh được) - lọc chính xác ngày đến hạn sang kỳ ở tầng
    // service vì term_length_days cũng là snapshot trên biên nhận
    @Query("select p from Product p join fetch p.consignmentReceipt r "
            + "where p.status = com.rehome.rehome.entity.enums.ProductStatus.ON_SALE "
            + "and p.currentTerm <= r.maxRenewals")
    List<Product> findOnSaleNotYetFinalTerm();

    // F-029: đã qua hạn kỳ cuối (ngày 90, cột endOfTermDeadline đã tính sẵn lúc nhập kho/ký gửi lại)
    // nhưng vẫn còn ON_SALE - job quét để gỡ khỏi sàn và mở EndOfTermDecision
    List<Product> findAllByStatusAndEndOfTermDeadlineBefore(ProductStatus status, Instant deadline);

    // ---- Ưu tiên hiển thị "Nổi bật" (quyền lợi gói VIP) - xem FeaturedListingManager ----

    // Hàng ký gửi của Member còn đang trong vòng đời bán: set featured_until cho đúng nhóm này.
    // Món đã SOLD/WITHDRAWN/... không lên danh sách sàn nữa nên không cần đụng tới.
    List<Product> findAllByMember_IdAndStatusIn(UUID memberId, Collection<ProductStatus> statuses);

    // Gói hết hạn/đổi hạng: xóa nhãn trên MỌI món của Member, kể cả món đã rời sàn, để không còn
    // dòng rác featured_until trong DB
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Product p set p.featuredUntil = null where p.member.id = :memberId "
            + "and p.featuredUntil is not null")
    int clearFeaturedByMemberId(@Param("memberId") UUID memberId);

    // Số món đang thực sự nổi bật của Member, cho quota.featuredListing.productCount ở
    // GET /memberships/me và màn M-09
    @Query("select count(p) from Product p where p.member.id = :memberId "
            + "and p.featuredUntil is not null and p.featuredUntil > :now")
    long countFeaturedByMemberId(@Param("memberId") UUID memberId, @Param("now") Instant now);
}