package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemWithdrawalRequestRepository extends JpaRepository<ItemWithdrawalRequest, UUID> {
    List<ItemWithdrawalRequest> findByMember_IdAndStatusOrderByCreatedAtAsc(
            UUID memberId, ItemWithdrawalStatus status);

    boolean existsByProduct_IdAndStatus(String productId, ItemWithdrawalStatus status);

    // API 91 (F-033) - map IPN MoMo/VNPay về đúng yêu cầu rút hàng khi không khớp Payment nào
    Optional<ItemWithdrawalRequest> findByMomoOrderId(String momoOrderId);

    Optional<ItemWithdrawalRequest> findByVnpTxnRef(String vnpTxnRef);

    // API 90/F-035 - job quét yêu cầu quá hạn đến kho nhận hàng
    List<ItemWithdrawalRequest> findAllByStatusAndPickupDeadlineBefore(
            ItemWithdrawalStatus status, Instant deadline);

    // F-035: job quét yêu cầu quá hạn trả phí (ngắn hơn pickupDeadline) - chưa trả phí và đã
    // qua feePaymentDeadline. feePaymentDeadline null (miễn phí) thì không bao giờ khớp.
    @Query("select r from ItemWithdrawalRequest r "
            + "where r.status = :status and r.feePaidAt is null "
            + "and r.feePaymentDeadline is not null and r.feePaymentDeadline < :now")
    List<ItemWithdrawalRequest> findAllByStatusAndFeePaymentOverdue(
            @Param("status") ItemWithdrawalStatus status, @Param("now") Instant now);

    // Màn chi tiết của Member: fetch sẵn product + warehouse vì response có tên món và tên kho
    @Query("select r from ItemWithdrawalRequest r "
            + "join fetch r.product p join fetch p.warehouse "
            + "where r.id = :id")
    Optional<ItemWithdrawalRequest> findDetailById(@Param("id") UUID id);

    // Danh sách của Member, status = null thì lấy tất cả
    @Query("select r from ItemWithdrawalRequest r "
            + "join fetch r.product p join fetch p.warehouse "
            + "where r.member.id = :memberId and (:status is null or r.status = :status) "
            + "order by r.createdAt desc")
    List<ItemWithdrawalRequest> findDetailsByMember(
            @Param("memberId") UUID memberId, @Param("status") ItemWithdrawalStatus status);

    // /products/mine: gắn yêu cầu đang chờ nhận hàng vào từng món, một query cho cả trang.
    // Chỉ PENDING_PICKUP vì create() đã chặn 2 yêu cầu PENDING_PICKUP cho cùng một món
    @Query("select r from ItemWithdrawalRequest r "
            + "where r.product.id in :productIds and r.status = :status")
    List<ItemWithdrawalRequest> findByProductIdsAndStatus(
            @Param("productIds") List<String> productIds, @Param("status") ItemWithdrawalStatus status);

    // API 72: danh sách yêu cầu rút hàng đang chờ bàn giao tại quầy của một kho (warehouse đọc qua
    // product.warehouse vì request không giữ cột warehouse riêng)
    @Query("select r from ItemWithdrawalRequest r "
            + "where r.product.warehouse.id = :warehouseId and r.status = :status")
    List<ItemWithdrawalRequest> findAllByWarehouseIdAndStatus(
            @Param("warehouseId") UUID warehouseId, @Param("status") ItemWithdrawalStatus status);
}
