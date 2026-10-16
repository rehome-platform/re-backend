package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ShippingAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ShippingAddressRepository extends JpaRepository<ShippingAddress, UUID> {
    List<ShippingAddress> findByMember_IdOrderByIsDefaultDesc(UUID memberId);

    @Modifying
    @Query("update ShippingAddress a set a.isDefault = false where a.member.id = :memberId and a.isDefault = true")
    void clearDefaultForMember(@Param("memberId") UUID memberId);
}
