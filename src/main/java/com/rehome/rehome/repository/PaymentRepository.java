package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrder_Id(UUID orderId);

    // Trả về danh sách chứ không phải Optional: một giao dịch cổng thanh toán có thể phủ nhiều đơn,
    // mỗi đơn một dòng payments chung mã giao dịch.
    List<Payment> findAllByMomoOrderId(String momoOrderId);

    List<Payment> findAllByVnpTxnRef(String vnpTxnRef);
}
