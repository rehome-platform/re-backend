package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.PlatformFund;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface PlatformFundRepository extends JpaRepository<PlatformFund, UUID> {
    // Chỉ có đúng một dòng quỹ; lock để tránh hai giao dịch cùng cộng/trừ đồng thời
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlatformFund> findTopByOrderByIdAsc();
}
