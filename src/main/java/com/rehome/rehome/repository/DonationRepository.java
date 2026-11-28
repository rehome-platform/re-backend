package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.DonationStatus;
import com.rehome.rehome.entity.marketplace.Donation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DonationRepository extends JpaRepository<Donation, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select d from Donation d
            where d.status = com.rehome.rehome.entity.enums.DonationStatus.PENDING_PICKUP
              and d.campaign.id in (select c.id from Campaign c where c.organization.accountId = :organizationId)
            order by d.id
            """)
    List<Donation> findPendingForOrganization(@Param("organizationId") UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Donation d where d.campaign.id = :campaignId and d.status = :status order by d.id")
    List<Donation> findForCampaignByStatus(@Param("campaignId") UUID campaignId, @Param("status") DonationStatus status);

    List<Donation> findByWarehouse_IdOrderByCreatedAtDesc(UUID warehouseId);

    List<Donation> findByWarehouse_IdAndStatusOrderByCreatedAtDesc(UUID warehouseId, DonationStatus status);

    List<Donation> findByWarehouse_IdAndCampaign_IdOrderByCreatedAtDesc(UUID warehouseId, UUID campaignId);

    List<Donation> findByWarehouse_IdAndCampaign_IdAndStatusOrderByCreatedAtDesc(
            UUID warehouseId, UUID campaignId, DonationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Donation d where d.id in :ids and d.warehouse.id = :warehouseId order by d.id")
    List<Donation> findByIdInAndWarehouse_Id(@Param("ids") Collection<UUID> ids, @Param("warehouseId") UUID warehouseId);

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

    @Query("select d from Donation d "
            + "where d.status = :status "
            + "and (:campaignId is null or d.campaign.id = :campaignId) "
            + "and (:warehouseId is null or d.warehouse.id = :warehouseId) "
            + "order by d.createdAt desc")
    Page<Donation> findByStatusWithFilters(@Param("status") DonationStatus status,
                                           @Param("campaignId") UUID campaignId,
                                           @Param("warehouseId") UUID warehouseId,
                                           Pageable pageable);

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

    long countByWarehouse_IdAndCampaign_IdAndStatus(UUID warehouseId, UUID campaignId, DonationStatus status);

    @Query("select d from Donation d "
            + "left join fetch d.product "
            + "left join fetch d.campaign c "
            + "left join fetch c.organization "
            + "where d.id in :ids")
    List<Donation> findAllWithDetailsByIdIn(@Param("ids") Collection<UUID> ids);
    @Query("select d from Donation d join fetch d.product p "
            + "left join fetch d.campaign c left join fetch c.organization "
            + "where p.id in :productIds order by d.createdAt desc")
    List<Donation> findForProducts(@Param("productIds") Collection<String> productIds);
}
