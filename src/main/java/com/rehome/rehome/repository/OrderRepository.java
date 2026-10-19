package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.marketplace.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    long countByOrderNoStartingWith(String prefix);

    List<Order> findAllByStatusAndPaymentDeadlineBefore(OrderStatus status, Instant deadline);
}
