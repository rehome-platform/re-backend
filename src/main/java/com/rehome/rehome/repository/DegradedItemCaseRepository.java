package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.DegradedItemCase;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DegradedItemCaseRepository extends JpaRepository<DegradedItemCase, UUID> {

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

    // /products/mine: gắn ca đang chờ Member quyết định vào từng món, một query cho cả trang
    @Query("select c from DegradedItemCase c "
            + "where c.product.id in :productIds and c.choice is null")
    List<DegradedItemCase> findPendingByProductIds(@Param("productIds") List<String> productIds);
}
