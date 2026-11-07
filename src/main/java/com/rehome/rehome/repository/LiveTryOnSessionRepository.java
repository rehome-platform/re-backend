package com.rehome.rehome.repository;

import com.rehome.rehome.entity.tryon.LiveTryOnSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LiveTryOnSessionRepository extends JpaRepository<LiveTryOnSession, UUID> {

    // Quota 2 lượt/ngày: đếm số phiên đã mint token trong khoảng [startOfDay, endOfDay) giờ VN.
    // Biết trước: đếm-rồi-lưu không atomic 100% nếu cùng 1 account bắn 2 request start gần như
    // đồng thời (race hiếm, lệch tối đa 1 lượt). Chấp nhận cho MVP thay vì thêm bảng counter riêng.
    long countByMember_IdAndCreatedAtBetween(UUID memberId, Instant startOfDay, Instant endOfDay);

    Optional<LiveTryOnSession> findByIdAndMember_Id(UUID id, UUID memberId);
}
