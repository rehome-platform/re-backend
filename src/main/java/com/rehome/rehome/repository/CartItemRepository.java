package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {
    boolean existsByCart_IdAndProduct_Id(UUID cartId, String productId);

    Optional<CartItem> findByIdAndCart_Member_Id(UUID id, UUID memberId);

    @Query("select ci from CartItem ci " +
            "join fetch ci.product p " +
            "join fetch p.warehouse " +
            "join fetch p.consignmentReceipt " +
            "where ci.cart.id = :cartId " +
            "order by ci.addedAt desc")
    List<CartItem> findAllByCartIdWithProduct(@Param("cartId") UUID cartId);
}
