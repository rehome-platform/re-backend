package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrder_Id(UUID orderId);

    List<Payment> findAllByVnpTxnRef(String vnpTxnRef);
}
