package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.DonationStatus;
import com.rehome.rehome.entity.marketplace.Donation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    // Lọc theo chiến dịch (1 kho nhận cho nhiều chiến dịch của nhiều tổ chức)
    List<Donation> findByWarehouse_IdAndCampaign_IdOrderByCreatedAtDesc(UUID warehouseId, UUID campaignId);

    List<Donation> findByWarehouse_IdAndCampaign_IdAndStatusOrderByCreatedAtDesc(
            UUID warehouseId, UUID campaignId, DonationStatus status);

    // Bàn giao (F-165): chỉ lấy được lượt quyên góp thuộc đúng điểm ký gửi đang thao tác
    List<Donation> findByIdInAndWarehouse_Id(Collection<UUID> ids, UUID warehouseId);

    // F-166/OC-07: tổ chức thấy lượt đang chờ lấy ở mọi kho (ai đến cũng nhận được) cộng với
    // lịch sử những lượt chính họ đã nhận — không thấy lịch sử của tổ chức khác.
    // campaignId/warehouseId null thì không lọc theo tiêu chí đó.
    @Query("select d from Donation d "
            + "where (d.status = :pendingStatus or d.handedOverToOrganization.accountId = :organizationId) "
            + "and (:campaignId is null or d.campaign.id = :campaignId) "
            + "and (:warehouseId is null or d.warehouse.id = :warehouseId) "
            + "order by d.createdAt desc")
    Page<Donation> findVisibleForOrganization(@Param("organizationId") UUID organizationId,
                                              @Param("pendingStatus") DonationStatus pendingStatus,
                                              @Param("campaignId") UUID campaignId,
                                              @Param("warehouseId") UUID warehouseId,
                                              Pageable pageable);

    // status == PENDING_PICKUP: pool chung, không giới hạn theo tổ chức
    @Query("select d from Donation d "
            + "where d.status = :status "
            + "and (:campaignId is null or d.campaign.id = :campaignId) "
            + "and (:warehouseId is null or d.warehouse.id = :warehouseId) "
            + "order by d.createdAt desc")
    Page<Donation> findByStatusWithFilters(@Param("status") DonationStatus status,
                                           @Param("campaignId") UUID campaignId,
                                           @Param("warehouseId") UUID warehouseId,
                                           Pageable pageable);

    // status == HANDED_OVER: chỉ lịch sử của chính tổ chức đang gọi
    @Query("select d from Donation d "
            + "where d.handedOverToOrganization.accountId = :organizationId "
            + "and d.status = :status "
            + "and (:campaignId is null or d.campaign.id = :campaignId) "
            + "and (:warehouseId is null or d.warehouse.id = :warehouseId) "
            + "order by d.createdAt desc")
    Page<Donation> findHandedOverForOrganization(@Param("organizationId") UUID organizationId,
                                                 @Param("status") DonationStatus status,
                                                 @Param("campaignId") UUID campaignId,
                                                 @Param("warehouseId") UUID warehouseId,
                                                 Pageable pageable);

    // OC-02/OC-03: "n chờ lấy" hiển thị cho từng kho mà chiến dịch đã chọn, tính riêng cho
    // ĐÚNG chiến dịch đang xem - kho dùng chung giữa nhiều chiến dịch không được cộng dồn
    // lượt chờ lấy của chiến dịch khác vào đây
    long countByWarehouse_IdAndCampaign_IdAndStatus(UUID warehouseId, UUID campaignId, DonationStatus status);

    // Hàng đợi Moderator (API 28, nhánh DONATION_PICKUP): fetch sẵn product, chiến dịch và tổ chức theo lô id
    // để QueueServiceImpl dựng QueueItemResponse.fromDonationPickup không phát sinh N+1
    @Query("select d from Donation d "
            + "left join fetch d.product "
            + "left join fetch d.campaign c "
            + "left join fetch c.organization "
            + "where d.id in :ids")
    List<Donation> findAllWithDetailsByIdIn(@Param("ids") Collection<UUID> ids);
}