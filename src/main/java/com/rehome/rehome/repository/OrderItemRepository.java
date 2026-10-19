package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
}
