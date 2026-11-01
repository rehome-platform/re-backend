package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.DonationStatus;
import com.rehome.rehome.entity.marketplace.Donation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // F-166: tổ chức thấy lượt đang chờ lấy ở mọi kho (ai đến cũng nhận được) cộng với
    // lịch sử những lượt chính họ đã nhận — không thấy lịch sử của tổ chức khác
    @Query("select d from Donation d "
            + "where d.status = :pendingStatus "
            + "or d.handedOverToOrganization.accountId = :organizationId "
            + "order by d.createdAt desc")
    List<Donation> findVisibleForOrganization(@Param("organizationId") UUID organizationId,
                                              @Param("pendingStatus") DonationStatus pendingStatus);

    List<Donation> findByStatusOrderByCreatedAtDesc(DonationStatus status);

    List<Donation> findByHandedOverToOrganization_AccountIdAndStatusOrderByCreatedAtDesc(
            UUID organizationId, DonationStatus status);

    // OC-02/OC-03: "n chờ lấy" hiển thị cho từng kho mà chiến dịch đã chọn
    long countByWarehouse_IdAndStatus(UUID warehouseId, DonationStatus status);
}
