package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.EndOfTermChoice;
import com.rehome.rehome.entity.enums.ReConsignStatus;
import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EndOfTermDecisionRepository extends JpaRepository<EndOfTermDecision, UUID> {

    // Dòng đang chờ Member chọn (decision null). Lấy dòng có hạn mới nhất phòng khi sản phẩm từng ký gửi lại.
    Optional<EndOfTermDecision> findFirstByProduct_IdAndDecisionIsNullOrderByDeadlineDesc(String productId);

    // Quyết định đã sinh ra biên nhận ký gửi lại này (để lấy sản phẩm cũ và giá trước đó)
    Optional<EndOfTermDecision> findByNewReceipt_Id(UUID receiptId);

    // Quyết định RE_CONSIGN đang chờ Moderator của kho chốt giá và điều khoản (chưa có biên nhận mới)
    @Query("select d from EndOfTermDecision d join fetch d.product p "
            + "where d.decision = com.rehome.rehome.entity.enums.EndOfTermChoice.RE_CONSIGN "
            + "and d.newReceipt is null and p.warehouse.id = :warehouseId "
            + "order by d.decidedAt asc")
    List<EndOfTermDecision> findPendingPricingByWarehouse(@Param("warehouseId") UUID warehouseId);

    // Quyết định RE_CONSIGN mà Member vừa đề xuất lại giá (COUNTER_OFFERED), chờ Moderator phản hồi
    @Query("select d from EndOfTermDecision d join fetch d.product p join fetch d.newReceipt "
            + "where d.reConsignStatus = com.rehome.rehome.entity.enums.ReConsignStatus.COUNTER_OFFERED "
            + "and p.warehouse.id = :warehouseId order by d.counteredAt asc")
    List<EndOfTermDecision> findCounterOfferedByWarehouse(@Param("warehouseId") UUID warehouseId);

    // Job: PROPOSED đã quá proposalDeadline -> xử lý như DECLINE (EXPIRED)
    List<EndOfTermDecision> findAllByReConsignStatusAndProposalDeadlineBefore(
            ReConsignStatus reConsignStatus, Instant proposalDeadline);

    // Job: nhắc Member trước khi hết hạn xác nhận/đề xuất lại, mỗi vòng chỉ nhắc 1 lần
    @Query("select d from EndOfTermDecision d join fetch d.product p "
            + "where d.reConsignStatus = com.rehome.rehome.entity.enums.ReConsignStatus.PROPOSED "
            + "and d.proposalDeadline is not null and d.proposalDeadline <= :reminderBefore "
            + "and d.proposalDeadline > :now and d.proposalReminderSentAt is null")
    List<EndOfTermDecision> findDueForProposalReminder(
            @Param("now") Instant now, @Param("reminderBefore") Instant reminderBefore);

    // Job: COUNTER_OFFERED quá hạn phản hồi của Moderator - chỉ để log cảnh báo, không tự xử lý
    List<EndOfTermDecision> findAllByReConsignStatusAndCounteredAtBefore(
            ReConsignStatus reConsignStatus, Instant counteredAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from EndOfTermDecision d where d.id = :id")
    Optional<EndOfTermDecision> findByIdForUpdate(@Param("id") UUID id);

    @Query("select d from EndOfTermDecision d join fetch d.product p "
            + "where p.id in :ids order by d.deadline desc")
    List<EndOfTermDecision> findByProductIds(@Param("ids") Collection<String> ids);

    // Member Tasks (API 27): quyết định TAKE_BACK đã chốt nhưng chưa đến quầy nhận hàng
    @Query("select d from EndOfTermDecision d join fetch d.product p "
            + "where p.member.id = :memberId "
            + "and d.decision = com.rehome.rehome.entity.enums.EndOfTermChoice.TAKE_BACK "
            + "and d.takenBackAt is null order by d.deadline asc")
    List<EndOfTermDecision> findPendingTakeBackByMemberId(@Param("memberId") UUID memberId);

    // Job quét TAKE_BACK chưa đến kho nhận hàng quá hạn (Điều 8.3) -> đánh dấu vô thừa nhận
    @Query("select d from EndOfTermDecision d join fetch d.product p "
            + "where d.decision = :decision and d.takenBackAt is null and d.decidedAt < :cutoff")
    List<EndOfTermDecision> findAllByDecisionAndTakenBackAtIsNullAndDecidedAtBefore(
            @Param("decision") EndOfTermChoice decision, @Param("cutoff") Instant cutoff);
}
