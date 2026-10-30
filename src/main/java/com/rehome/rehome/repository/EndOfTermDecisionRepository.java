package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EndOfTermDecisionRepository extends JpaRepository<EndOfTermDecision, UUID> {

    // Dòng đang chờ Member chọn (decision null). Lấy dòng có hạn mới nhất phòng khi sản phẩm từng ký gửi lại.
    Optional<EndOfTermDecision> findFirstByProduct_IdAndDecisionIsNullOrderByDeadlineDesc(String productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from EndOfTermDecision d where d.id = :id")
    Optional<EndOfTermDecision> findByIdForUpdate(@Param("id") UUID id);

    @Query("select d from EndOfTermDecision d join fetch d.product p "
            + "where p.id in :ids order by d.deadline desc")
    List<EndOfTermDecision> findByProductIds(@Param("ids") Collection<String> ids);

    @Query("select d from EndOfTermDecision d join fetch d.product p "
            + "where p.member.id = :memberId and d.decision is null order by d.deadline asc")
    List<EndOfTermDecision> findUndecidedByMemberId(@Param("memberId") UUID memberId);
}
