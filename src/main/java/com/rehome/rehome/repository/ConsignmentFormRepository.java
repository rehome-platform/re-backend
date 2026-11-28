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

    boolean existsByCategory_Id(UUID categoryId);

    boolean existsByBrand_Id(UUID brandId);

    boolean existsByWarehouse_IdAndStatusIn(UUID warehouseId, Collection<ConsignmentFormStatus> statuses);


    List<ConsignmentForm> findByMember_IdAndStatusInOrderByCreatedAtAsc(
            UUID memberId, Collection<ConsignmentFormStatus> statuses);

    List<ConsignmentForm> findByMember_IdInAndWarehouse_IdAndStatusInOrderByCreatedAtAsc(
            Collection<UUID> memberIds, UUID warehouseId, Collection<ConsignmentFormStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from ConsignmentForm f where f.id = :id")
    Optional<ConsignmentForm> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = "warehouse")
    Page<ConsignmentForm> findByMemberIdAndStatus(UUID memberId, ConsignmentFormStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "warehouse")
    Page<ConsignmentForm> findByMemberIdAndStatusNot(UUID memberId, ConsignmentFormStatus status, Pageable pageable);

    long countByMember_IdAndStatus(UUID memberId, ConsignmentFormStatus status);

    @Query("select f.member.id, count(f) from ConsignmentForm f "
            + "where f.member.id in :memberIds and f.status in ("
            + "com.rehome.rehome.entity.enums.ConsignmentFormStatus.PENDING, "
            + "com.rehome.rehome.entity.enums.ConsignmentFormStatus.APPROVED, "
            + "com.rehome.rehome.entity.enums.ConsignmentFormStatus.INSPECTING) "
            + "group by f.member.id")
    List<Object[]> countActiveFormsByMemberIds(@Param("memberIds") List<UUID> memberIds);

    @EntityGraph(attributePaths = {"warehouse", "category", "brand"})
    Optional<ConsignmentForm> findWithDetailsById(UUID id);

    @Query(value = "SELECT nextval('consignment_form_no_seq')", nativeQuery = true)
    long nextFormNo();

    String LATEST_RECEIPT_GUARD = "not exists (select 1 from ConsignmentReceipt r2 "
            + "where r2.consignmentForm = r.consignmentForm and r2.createdAt > r.createdAt)";

    String LATEST_RECEIPT_HAS_PRODUCT = "exists (select 1 from Product p join p.consignmentReceipt r "
            + "where r.consignmentForm = f and " + LATEST_RECEIPT_GUARD + ")";

    String LATEST_RECEIPT_STOCKED_BETWEEN = "exists (select 1 from Product p join p.consignmentReceipt r "
            + "where r.consignmentForm = f and p.createdAt >= :startAt and p.createdAt < :endAt and "
            + LATEST_RECEIPT_GUARD + ")";

    @EntityGraph(attributePaths = {"warehouse", "member"})
    @Query(value = "select f from ConsignmentForm f "
            + "where f.warehouse.id = :warehouseId and f.status <> :excluded",
            countQuery = "select count(f) from ConsignmentForm f "
                    + "where f.warehouse.id = :warehouseId and f.status <> :excluded")
    Page<ConsignmentForm> findForModeratorExcludingStatus(@Param("warehouseId") UUID warehouseId,
                                                          @Param("excluded") ConsignmentFormStatus excluded,
                                                          Pageable pageable);

    @EntityGraph(attributePaths = {"warehouse", "member"})
    @Query(value = "select f from ConsignmentForm f "
            + "where f.warehouse.id = :warehouseId and f.status = :status",
            countQuery = "select count(f) from ConsignmentForm f "
                    + "where f.warehouse.id = :warehouseId and f.status = :status")
    Page<ConsignmentForm> findForModeratorByStatus(@Param("warehouseId") UUID warehouseId,
                                                   @Param("status") ConsignmentFormStatus status,
                                                   Pageable pageable);

    @EntityGraph(attributePaths = {"warehouse", "member"})
    @Query(value = "select f from ConsignmentForm f "
            + "where f.warehouse.id = :warehouseId and f.status = :status and not " + LATEST_RECEIPT_HAS_PRODUCT,
            countQuery = "select count(f) from ConsignmentForm f "
                    + "where f.warehouse.id = :warehouseId and f.status = :status and not " + LATEST_RECEIPT_HAS_PRODUCT)
    Page<ConsignmentForm> findInspectionTabForModerator(@Param("warehouseId") UUID warehouseId,
                                                        @Param("status") ConsignmentFormStatus status,
                                                        Pageable pageable);

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