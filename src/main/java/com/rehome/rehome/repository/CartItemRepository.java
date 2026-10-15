package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    boolean existsByCart_IdAndProduct_Id(UUID cartId, String productId);
}
