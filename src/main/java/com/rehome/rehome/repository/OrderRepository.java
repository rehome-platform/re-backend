package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    long countByOrderNoStartingWith(String prefix);
}
