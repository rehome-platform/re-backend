package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConsignmentReceiptRepository extends JpaRepository<ConsignmentReceipt, UUID> {
    // Một form có thể có nhiều biên nhận (lần đầu + các lần ký gửi lại): lấy biên nhận mới nhất
    Optional<ConsignmentReceipt> findFirstByConsignmentForm_IdOrderByCreatedAtDesc(UUID consignmentFormId);

    // Giữ nguyên tên cũ để các chỗ đang gọi không phải sửa
    default Optional<ConsignmentReceipt> findByConsignmentForm_Id(UUID consignmentFormId) {
        return findFirstByConsignmentForm_IdOrderByCreatedAtDesc(consignmentFormId);
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ConsignmentReceipt r where r.id = :id")
    Optional<ConsignmentReceipt> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByConsignmentFormId(UUID consignmentFormId);

    @Query(value = "SELECT nextval('receipt_no_seq')", nativeQuery = true)
    long nextReceiptSequence();

    boolean existsByConsignmentForm_Id(UUID consignmentFormId);

    // Moderator: biên nhận mới nhất của từng form trong một lần query (không N+1).
    // Cùng quy tắc "mới nhất = createdAt lớn nhất" với findFirstByConsignmentForm_IdOrderByCreatedAtDesc
    // và với LATEST_RECEIPT_GUARD ở ConsignmentFormRepository, nên danh sách và chi tiết luôn khớp nhau.
    @Query("select r from ConsignmentReceipt r "
            + "where r.consignmentForm.id in :formIds "
            + "and not exists (select 1 from ConsignmentReceipt r2 "
            + "where r2.consignmentForm = r.consignmentForm and r2.createdAt > r.createdAt)")
    List<ConsignmentReceipt> findLatestByFormIds(@Param("formIds") Collection<UUID> formIds);
}