package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.marketplace.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    Page<Order> findByBuyer_IdAndStatusIn(UUID buyerId, List<OrderStatus> statuses, Pageable pageable);

    @Query("select o.status, count(o) from Order o where o.buyer.id = :buyerId group by o.status")
    List<Object[]> countGroupedByStatusForBuyer(@Param("buyerId") UUID buyerId);
}
