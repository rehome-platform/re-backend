package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.CampaignStatus;
import com.rehome.rehome.entity.marketplace.Campaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    @Modifying(flushAutomatically = true)
    @Query("""
            update Campaign c set c.statusBeforeOrganizationBan = c.status, c.status = :hidden
            where c.organization.accountId = :organizationId and c.status <> :hidden
            """)
    int hideCampaignsByOrganization(@Param("organizationId") UUID organizationId,
                                    @Param("hidden") CampaignStatus hidden);

    List<Campaign> findByOrganization_AccountIdAndStatusBeforeOrganizationBanIsNotNull(UUID organizationId);

    long countByOrganization_AccountIdAndStatus(UUID organizationId, CampaignStatus status);

    @Query("select count(c) from Campaign c where c.organization.accountId = :organizationId "
            + "and (c.status = com.rehome.rehome.entity.enums.CampaignStatus.OPEN "
            + "or (c.status = com.rehome.rehome.entity.enums.CampaignStatus.HIDDEN and c.endTime > :now))")
    long countActiveByOrganization(@Param("organizationId") UUID organizationId, @Param("now") Instant now);

    List<Campaign> findAllByStatusAndEndTimeBefore(CampaignStatus status, Instant endTime);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Campaign c where c.id = :id")
    java.util.Optional<Campaign> findByIdForUpdate(@Param("id") UUID id);

    @Query("select c.organization.accountId from Campaign c where c.id = :id")
    java.util.Optional<UUID> findOrganizationId(@Param("id") UUID id);

    Page<Campaign> findByOrganization_AccountId(UUID organizationId, Pageable pageable);

    Page<Campaign> findByOrganization_AccountIdAndStatus(UUID organizationId, CampaignStatus status,
                                                         Pageable pageable);

    List<Campaign> findAllByOrganization_AccountIdInAndStatus(List<UUID> organizationIds, CampaignStatus status);

    List<Campaign> findAllByTitleNormalizedIsNullAndTitleIsNotNull();

    @Query("select c from Campaign c where c.status = com.rehome.rehome.entity.enums.CampaignStatus.OPEN "
            + "and c.organization.account.status <> com.rehome.rehome.entity.enums.AccountStatus.BANNED "
            + "and c.endTime > :now "
            + "and :warehouseId in (select w.id from c.warehouses w)")
    Page<Campaign> findOpenReceivingAtWarehouse(@Param("warehouseId") UUID warehouseId,
                                                @Param("now") Instant now, Pageable pageable);

    @Query("select c from Campaign c where c.status = :status "
            + "and c.organization.account.status <> com.rehome.rehome.entity.enums.AccountStatus.BANNED "
            + "and (c.titleNormalized like concat('%', :keyword, '%') "
            + "or c.organization.displayNameNormalized like concat('%', :keyword, '%')) "
            + "and (:warehouseId is null or :warehouseId in (select w.id from c.warehouses w))")
    Page<Campaign> search(@Param("status") CampaignStatus status, @Param("keyword") String keyword,
                           @Param("warehouseId") UUID warehouseId, Pageable pageable);
}
