package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.CampaignStatus;
import com.rehome.rehome.entity.marketplace.Campaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    // Đếm chiến dịch đang chạy của 1 tổ chức, để chặn vượt hạn mức (F-099)
    long countByOrganization_AccountIdAndStatus(UUID organizationId, CampaignStatus status);

    // F-099 (mở rộng): "đang chạy" = OPEN, hoặc HIDDEN do vi phạm nhưng endTime chưa qua - tổ chức
    // không được lợi dụng bị ẩn để lách hạn mức và mở thêm chiến dịch mới trong lúc đang bị xử lý
    @Query("select count(c) from Campaign c where c.organization.accountId = :organizationId "
            + "and (c.status = com.rehome.rehome.entity.enums.CampaignStatus.OPEN "
            + "or (c.status = com.rehome.rehome.entity.enums.CampaignStatus.HIDDEN and c.endTime > :now))")
    long countActiveByOrganization(@Param("organizationId") UUID organizationId, @Param("now") Instant now);

    // F-102: chiến dịch còn mở nhưng đã qua thời gian nhận đồ
    List<Campaign> findAllByStatusAndEndTimeBefore(CampaignStatus status, Instant endTime);

    // F-103: chiến dịch của chính tổ chức đang đăng nhập
    Page<Campaign> findByOrganization_AccountId(UUID organizationId, Pageable pageable);

    Page<Campaign> findByOrganization_AccountIdAndStatus(UUID organizationId, CampaignStatus status,
                                                         Pageable pageable);

    // GET /api/organizations/nearby: chiến dịch đang mở của nhiều tổ chức cùng lúc, cho bản đồ cộng đồng
    List<Campaign> findAllByOrganization_AccountIdInAndStatus(List<UUID> organizationIds, CampaignStatus status);

    // Chiến dịch đã tồn tại trước khi có cột title_normalized - xem CampaignTitleNormalizeInitializer
    List<Campaign> findAllByTitleNormalizedIsNullAndTitleIsNotNull();

    // GET /warehouses/{id}/campaigns: chiến dịch OPEN, CHƯA hết hạn (endTime > now), không HIDDEN/CLOSED,
    // có nhận đồ tại kho này. Dùng chung điều kiện này cho cả ghi nhận trực tiếp lẫn quyên góp món ký gửi.
    @Query("select c from Campaign c where c.status = com.rehome.rehome.entity.enums.CampaignStatus.OPEN "
            + "and c.endTime > :now "
            + "and :warehouseId in (select w.id from c.warehouses w)")
    Page<Campaign> findOpenReceivingAtWarehouse(@Param("warehouseId") UUID warehouseId,
                                                @Param("now") Instant now, Pageable pageable);

    // GET /campaigns?search=&status=ACTIVE&warehouseId=: tìm theo title của chiến dịch hoặc tên tổ
    // chức. keyword "" -> không lọc theo từ khóa, chỉ còn status. warehouseId null -> không lọc
    // theo điểm nhận đồ (cũng dùng cho GET /warehouses/{id}/campaigns với warehouseId luôn có giá trị).
    @Query("select c from Campaign c where c.status = :status "
            + "and (c.titleNormalized like concat('%', :keyword, '%') "
            + "or c.organization.displayNameNormalized like concat('%', :keyword, '%')) "
            + "and (:warehouseId is null or :warehouseId in (select w.id from c.warehouses w))")
    Page<Campaign> search(@Param("status") CampaignStatus status, @Param("keyword") String keyword,
                           @Param("warehouseId") UUID warehouseId, Pageable pageable);
}
