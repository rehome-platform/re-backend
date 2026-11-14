package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.DegradedItemCase;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DegradedItemCaseRepository extends JpaRepository<DegradedItemCase, UUID> {

    // decide() / job tự động: khóa dòng để Member bấm chọn và job quét quá hạn không cùng xử lý một ca
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from DegradedItemCase c where c.id = :id")
    Optional<DegradedItemCase> findByIdForUpdate(@Param("id") UUID id);

    // Màn chi tiết của Member: fetch sẵn product (tên món, chủ hàng để kiểm quyền) và
    // yêu cầu trả hàng (lý do, ghi chú của Moderator). left join vì returnRequest nullable
    @Query("select c from DegradedItemCase c "
            + "join fetch c.product p join fetch p.member "
            + "left join fetch c.returnRequest "
            + "where c.id = :id")
    Optional<DegradedItemCase> findDetailById(@Param("id") UUID id);

    /**
     * Danh sách ca của một Member. pendingOnly = true thì chỉ lấy ca chưa quyết định
     * (choice is null) — đúng cái app cần để hiện dải nhắc ở tab Ký gửi.
     */
    @Query("select c from DegradedItemCase c "
            + "join fetch c.product p join fetch p.member "
            + "left join fetch c.returnRequest "
            + "where p.member.id = :memberId "
            + "and (:pendingOnly = false or c.choice is null) "
            + "and (:decidedOnly = false or c.choice is not null) "
            + "order by c.createdAt desc")
    List<DegradedItemCase> findDetailsByMember(@Param("memberId") UUID memberId,
                                               @Param("pendingOnly") boolean pendingOnly,
                                               @Param("decidedOnly") boolean decidedOnly);

    // Màn Moderator: danh sách ca hàng xuống cấp thuộc kho của Moderator đang đăng nhập
    @Query("select c from DegradedItemCase c "
            + "join fetch c.product p join fetch p.member "
            + "left join fetch c.returnRequest "
            + "where p.warehouse.id = :warehouseId "
            + "and (:pendingOnly = false or c.choice is null) "
            + "and (:decidedOnly = false or c.choice is not null) "
            + "order by c.createdAt desc")
    List<DegradedItemCase> findDetailsByWarehouse(@Param("warehouseId") UUID warehouseId,
                                                   @Param("pendingOnly") boolean pendingOnly,
                                                   @Param("decidedOnly") boolean decidedOnly);

    // /products/mine: gắn ca đang chờ Member quyết định vào từng món, một query cho cả trang
    @Query("select c from DegradedItemCase c "
            + "where c.product.id in :productIds and c.choice is null")
    List<DegradedItemCase> findPendingByProductIds(@Param("productIds") List<String> productIds);

    @Query("select c from DegradedItemCase c where c.product.id in :productIds and "
            + "(c.choice is null or (c.choice = com.rehome.rehome.entity.enums.DegradedItemChoice.PARTIAL_WITH_ITEM_BACK "
            + "and c.pickedUpAt is null and c.itemDonated = false)) order by c.createdAt desc, c.id desc")
    List<DegradedItemCase> findOpenByProductIds(@Param("productIds") List<String> productIds);

    // Member Tasks (API 27): đã chọn PARTIAL_WITH_ITEM_BACK nhưng chưa đến quầy nhận hàng
    @Query("select c from DegradedItemCase c join fetch c.product p "
            + "where p.member.id = :memberId "
            + "and c.choice = com.rehome.rehome.entity.enums.DegradedItemChoice.PARTIAL_WITH_ITEM_BACK "
            + "and c.pickedUpAt is null and c.itemDonated = false order by c.decidedAt asc")
    List<DegradedItemCase> findPendingPickupByMemberId(@Param("memberId") UUID memberId);

    // Mở ca trực tiếp (API mới của Moderator): không mở trùng khi sản phẩm còn ca chưa quyết định
    Optional<DegradedItemCase> findFirstByProduct_IdOrderByCreatedAtDesc(String productId);

    boolean existsByProduct_IdAndChoiceIsNull(String productId);

    // Job quét quá hạn chọn phương án đền bù -> vô thừa nhận
    List<DegradedItemCase> findAllByChoiceIsNullAndDecisionDeadlineBefore(Instant deadline);

    // Job gửi nhắc nhở cách 7 ngày: còn PENDING_DECISION, chưa quá hạn, và (chưa nhắc lần nào mà
    // tạo ca đã quá reminderCutoff) hoặc (đã nhắc mà lần nhắc gần nhất cũng đã quá reminderCutoff)
    @Query("select c from DegradedItemCase c join fetch c.product p "
            + "where c.choice is null and c.decisionDeadline is not null and c.decisionDeadline > :now "
            + "and ((c.lastReminderSentAt is null and c.createdAt <= :reminderCutoff) "
            + "or (c.lastReminderSentAt is not null and c.lastReminderSentAt <= :reminderCutoff))")
    List<DegradedItemCase> findDueForReminder(
            @Param("now") Instant now, @Param("reminderCutoff") Instant reminderCutoff);
}
