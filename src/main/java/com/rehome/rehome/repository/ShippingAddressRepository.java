package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.ShippingAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ShippingAddressRepository extends JpaRepository<ShippingAddress, UUID> {
    List<ShippingAddress> findByMember_IdOrderByIsDefaultDesc(UUID memberId);
}
