package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.EndOfTermDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EndOfTermDecisionRepository extends JpaRepository<EndOfTermDecision, UUID> {

    // Dòng đang chờ Member chọn (decision null). Lấy dòng có hạn mới nhất phòng khi sản phẩm từng ký gửi lại.
    Optional<EndOfTermDecision> findFirstByProduct_IdAndDecisionIsNullOrderByDeadlineDesc(String productId);
}
