package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.marketplace.ConsignmentForm;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsignmentFormRepository extends JpaRepository<ConsignmentForm, UUID> {

    List<ConsignmentForm> findByMember_IdAndStatusInOrderByCreatedAtAsc(
            UUID memberId, Collection<ConsignmentFormStatus> statuses);

    // API 34, 35: khóa dòng để 2 Moderator không xử lý cùng lúc
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from ConsignmentForm f where f.id = :id")
    Optional<ConsignmentForm> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "warehouse")
    Page<ConsignmentForm> findByMemberId(UUID memberId, Pageable pageable);

    @EntityGraph(attributePaths = "warehouse")
    Page<ConsignmentForm> findByMemberIdAndStatus(UUID memberId, ConsignmentFormStatus status, Pageable pageable);

    // Danh sách "tất cả" không gồm nháp
    @EntityGraph(attributePaths = "warehouse")
    Page<ConsignmentForm> findByMemberIdAndStatusNot(UUID memberId, ConsignmentFormStatus status, Pageable pageable);

    long countByMember_IdAndStatus(UUID memberId, ConsignmentFormStatus status);

    @EntityGraph(attributePaths = {"warehouse", "category", "brand"})
    Optional<ConsignmentForm> findWithDetailsById(UUID id);

    @Query(value = "SELECT nextval('consignment_form_no_seq')", nativeQuery = true)
    long nextFormNo();

    // ================================================================ Moderator
    // Biên nhận "mới nhất" của form = biên nhận không có biên nhận nào của cùng form tạo sau nó.
    // Cùng quy tắc với ConsignmentReceiptRepository.findLatestByFormIds; alias r lấy từ subquery bao ngoài.
    String LATEST_RECEIPT_GUARD = "not exists (select 1 from ConsignmentReceipt r2 "
            + "where r2.consignmentForm = r.consignmentForm and r2.createdAt > r.createdAt)";

    // Biên nhận mới nhất của form đã có sản phẩm (đã nhập kho xong)
    String LATEST_RECEIPT_HAS_PRODUCT = "exists (select 1 from Product p join p.consignmentReceipt r "
            + "where r.consignmentForm = f and " + LATEST_RECEIPT_GUARD + ")";

    // Biên nhận mới nhất của form có sản phẩm được tạo trong [:startAt, :endAt)
    String LATEST_RECEIPT_STOCKED_BETWEEN = "exists (select 1 from Product p join p.consignmentReceipt r "
            + "where r.consignmentForm = f and p.createdAt >= :startAt and p.createdAt < :endAt and "
            + LATEST_RECEIPT_GUARD + ")";

    // Tất cả form của kho, trừ nháp
    @EntityGraph(attributePaths = {"warehouse", "member"})
    @Query(value = "select f from ConsignmentForm f "
            + "where f.warehouse.id = :warehouseId and f.status <> :excluded",
            countQuery = "select count(f) from ConsignmentForm f "
                    + "where f.warehouse.id = :warehouseId and f.status <> :excluded")
    Page<ConsignmentForm> findForModeratorExcludingStatus(@Param("warehouseId") UUID warehouseId,
                                                          @Param("excluded") ConsignmentFormStatus excluded,
                                                          Pageable pageable);

    // Tab theo đúng một trạng thái form (PENDING, APPROVED)
    @EntityGraph(attributePaths = {"warehouse", "member"})
    @Query(value = "select f from ConsignmentForm f "
            + "where f.warehouse.id = :warehouseId and f.status = :status",
            countQuery = "select count(f) from ConsignmentForm f "
                    + "where f.warehouse.id = :warehouseId and f.status = :status")
    Page<ConsignmentForm> findForModeratorByStatus(@Param("warehouseId") UUID warehouseId,
                                                   @Param("status") ConsignmentFormStatus status,
                                                   Pageable pageable);

    // Tab "Đang kiểm định": form INSPECTING mà biên nhận mới nhất chưa có sản phẩm. Gồm cả 3 trường hợp:
    // đang kiểm định, chờ Member xác nhận biên nhận, đã xác nhận nhưng chưa nhập kho.
    // Không dùng queue_items nên xóa/hoàn tất hàng đợi kiểm định không làm mất form.
    @EntityGraph(attributePaths = {"warehouse", "member"})
    @Query(value = "select f from ConsignmentForm f "
            + "where f.warehouse.id = :warehouseId and f.status = :status and not " + LATEST_RECEIPT_HAS_PRODUCT,
            countQuery = "select count(f) from ConsignmentForm f "
                    + "where f.warehouse.id = :warehouseId and f.status = :status and not " + LATEST_RECEIPT_HAS_PRODUCT)
    Page<ConsignmentForm> findInspectionTabForModerator(@Param("warehouseId") UUID warehouseId,
                                                        @Param("status") ConsignmentFormStatus status,
                                                        Pageable pageable);

    // Tab "Nhập kho hôm nay": lọc theo product.createdAt, không theo thời điểm tạo form/biên nhận
    @EntityGraph(attributePaths = {"warehouse", "member"})
    @Query(value = "select f from ConsignmentForm f "
            + "where f.warehouse.id = :warehouseId and f.status <> :excluded and " + LATEST_RECEIPT_STOCKED_BETWEEN,
            countQuery = "select count(f) from ConsignmentForm f "
                    + "where f.warehouse.id = :warehouseId and f.status <> :excluded and " + LATEST_RECEIPT_STOCKED_BETWEEN)
    Page<ConsignmentForm> findStockedBetweenForModerator(@Param("warehouseId") UUID warehouseId,
                                                         @Param("excluded") ConsignmentFormStatus excluded,
                                                         @Param("startAt") Instant startAt,
                                                         @Param("endAt") Instant endAt,
                                                         Pageable pageable);
}