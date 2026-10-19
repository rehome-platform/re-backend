package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findAllByOrder_Id(UUID orderId);

    @Query("select oi from OrderItem oi " +
            "join fetch oi.product p " +
            "where oi.order.id in :orderIds " +
            "order by oi.id asc")
    List<OrderItem> findAllByOrder_IdInOrderByIdAsc(@Param("orderIds") List<UUID> orderIds);
}
