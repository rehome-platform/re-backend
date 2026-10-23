package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.OrderTrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderTrackingEventRepository extends JpaRepository<OrderTrackingEvent, UUID> {
    List<OrderTrackingEvent> findAllByOrder_IdOrderByOccurredAtAsc(UUID orderId);
}
