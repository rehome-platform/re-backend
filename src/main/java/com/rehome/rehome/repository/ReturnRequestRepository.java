package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.entity.marketplace.Warehouse;
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

    // Lịch sử các yêu cầu trả hàng khác (nếu có) của cùng đơn - để Moderator xem khi kiểm hàng
    List<ReturnRequest> findAllByOrder_IdAndIdNotOrderByCreatedAtDesc(UUID orderId, UUID excludeId);

    Page<ReturnRequest> findByStatusInOrderByCreatedAtAsc(List<ReturnRequestStatus> statuses, Pageable pageable);

    // Hàng đợi xử lý của Moderator, giới hạn theo kho phụ trách để tránh thấy chéo kho
    Page<ReturnRequest> findByOrder_Warehouse_IdAndStatusInOrderByCreatedAtAsc(
            UUID warehouseId, List<ReturnRequestStatus> statuses, Pageable pageable);

    // Thống kê hộp thư Moderator: số yêu cầu trả hàng đang xử lý theo người mua, gom nhiều Member 1 query
    @Query("select r.order.buyer.id, count(r) from ReturnRequest r "
            + "where r.order.buyer.id in :buyerIds and r.status in :statuses group by r.order.buyer.id")
    List<Object[]> countByOrderBuyerIdsInAndStatusIn(@Param("buyerIds") Collection<UUID> buyerIds,
                                                     @Param("statuses") Collection<ReturnRequestStatus> statuses);

    // Id các yêu cầu trả hàng đang PENDING của đơn thuộc kho này, dùng đồng bộ vào hàng đợi RETURN (API 28)
    @Query("select r.id from ReturnRequest r "
            + "where r.status = com.rehome.rehome.entity.enums.ReturnRequestStatus.PENDING "
            + "and r.order.warehouse = :warehouse")
    List<UUID> findPendingIdsByWarehouse(@Param("warehouse") Warehouse warehouse);

    List<ReturnRequest> findAllByStatusAndHandoverDeadlineBefore(ReturnRequestStatus status, Instant deadline);

    List<ReturnRequest> findAllByStatusAndPickupDeadlineBeforeAndResolvedAtIsNull(
            ReturnRequestStatus status, Instant deadline);

    // Member Tasks (API 27): trả hàng bị kết luận không hợp lệ, còn trong 7 ngày gần nhất
    @Query("select r from ReturnRequest r join fetch r.order o "
            + "where o.buyer.id = :memberId "
            + "and r.status = com.rehome.rehome.entity.enums.ReturnRequestStatus.INVALID "
            + "and r.createdAt >= :since order by r.createdAt desc")
    List<ReturnRequest> findRecentInvalidByBuyerId(@Param("memberId") UUID memberId, @Param("since") Instant since);
}
