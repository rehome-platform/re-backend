package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {

    Optional<ReturnRequest> findByGhnReturnTrackingCode(String ghnReturnTrackingCode);

    boolean existsByOrder_IdAndStatusIn(UUID orderId, List<ReturnRequestStatus> statuses);

    Optional<ReturnRequest> findFirstByOrder_IdOrderByCreatedAtDesc(UUID orderId);

    // Mỗi dòng: [orderId, returnRequestId], mới nhất trước - dùng cho danh sách đơn để tránh N+1
    @Query("select r.order.id, r.id from ReturnRequest r where r.order.id in :orderIds order by r.createdAt desc")
    List<Object[]> findOrderIdAndIdByOrderIds(@Param("orderIds") Collection<UUID> orderIds);

    Page<ReturnRequest> findByOrder_Buyer_IdOrderByCreatedAtDesc(UUID buyerId, Pageable pageable);

    Page<ReturnRequest> findByStatusInOrderByCreatedAtAsc(List<ReturnRequestStatus> statuses, Pageable pageable);

    List<ReturnRequest> findAllByStatusAndHandoverDeadlineBefore(ReturnRequestStatus status, Instant deadline);

    List<ReturnRequest> findAllByStatusAndPickupDeadlineBeforeAndResolvedAtIsNull(
            ReturnRequestStatus status, Instant deadline);
}
