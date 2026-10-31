package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.DonationStatus;
import com.rehome.rehome.entity.marketplace.Donation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DonationRepository extends JpaRepository<Donation, UUID> {

    List<Donation> findByWarehouse_IdOrderByCreatedAtDesc(UUID warehouseId);

    List<Donation> findByWarehouse_IdAndStatusOrderByCreatedAtDesc(UUID warehouseId, DonationStatus status);

    // Bàn giao (F-165): chỉ lấy được lượt quyên góp thuộc đúng điểm ký gửi đang thao tác
    List<Donation> findByIdInAndWarehouse_Id(Collection<UUID> ids, UUID warehouseId);
}
