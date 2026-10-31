package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.marketplace.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    long countByOrderNoStartingWith(String prefix);

    List<Order> findAllByStatusAndPaymentDeadlineBefore(OrderStatus status, Instant deadline);

    boolean existsByShippingAddress_Id(UUID shippingAddressId);

    Optional<Order> findByGhnTrackingCode(String ghnTrackingCode);

    // Khóa dòng để job tự trả tiền và request trả hàng không cùng xử lý một đơn
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    // F-064 gộp với job trả tiền người ký gửi: đơn đã giao quá hạn (DELIVERED) hoặc đã
    // chốt sớm bởi buyer (COMPLETED) mà chưa trả tiền
    List<Order> findAllByStatusInAndAutoCompleteDeadlineBeforeAndPayoutAtIsNull(
            List<OrderStatus> statuses, Instant deadline);

    Page<Order> findByBuyer_IdAndStatusIn(UUID buyerId, List<OrderStatus> statuses, Pageable pageable);

    @Query("select o.status, count(o) from Order o where o.buyer.id = :buyerId group by o.status")
    List<Object[]> countGroupedByStatusForBuyer(@Param("buyerId") UUID buyerId);

    // Đơn có chứa ít nhất một món do :sellerId ký gửi. distinct vì một đơn có thể gồm nhiều
    // món của cùng người bán. ORDER BY nằm trong câu query chứ không qua Pageable: root của
    // query là OrderItem nên Sort.by("createdAt") sẽ trỏ sai bảng.
    @Query(value = "select distinct o from OrderItem oi join oi.order o "
            + "where oi.product.member.id = :sellerId and o.status in :statuses "
            + "order by o.createdAt desc",
            countQuery = "select count(distinct o) from OrderItem oi join oi.order o "
            + "where oi.product.member.id = :sellerId and o.status in :statuses")
    Page<Order> findOrdersContainingSellerItems(@Param("sellerId") UUID sellerId,
                                                @Param("statuses") List<OrderStatus> statuses,
                                                Pageable pageable);

    // Đếm theo trạng thái cho thanh tab màn "Đơn đã bán". count(distinct o) vì một đơn
    // nhiều món của cùng người bán vẫn chỉ là một đơn.
    @Query("select o.status, count(distinct o) from OrderItem oi join oi.order o "
            + "where oi.product.member.id = :sellerId group by o.status")
    List<Object[]> countGroupedByStatusForSeller(@Param("sellerId") UUID sellerId);
}
