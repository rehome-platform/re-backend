package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrder_Id(UUID orderId);

    Optional<Payment> findByMomoOrderId(String momoOrderId);

    Optional<Payment> findByVnpTxnRef(String vnpTxnRef);
}
