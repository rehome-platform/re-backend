package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WalletRepository extends JpaRepository<Wallet, UUID> {
    Optional<Wallet> findByMember_Id(UUID memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.member.id = :memberId")
    Optional<Wallet> findByMemberIdForUpdate(@Param("memberId") UUID memberId);
}
