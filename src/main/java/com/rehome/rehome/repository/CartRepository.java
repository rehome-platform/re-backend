package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {
    Optional<Cart> findByMember_Id(UUID memberId);
}
