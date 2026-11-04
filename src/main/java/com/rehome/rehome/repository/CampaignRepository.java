package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.CampaignStatus;
import com.rehome.rehome.entity.marketplace.Campaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, UUID> {

    // Đếm chiến dịch đang chạy của 1 tổ chức, để chặn vượt hạn mức (F-099)
    long countByOrganization_AccountIdAndStatus(UUID organizationId, CampaignStatus status);

    // F-102: chiến dịch còn mở nhưng đã qua thời gian nhận đồ
    List<Campaign> findAllByStatusAndEndTimeBefore(CampaignStatus status, Instant endTime);

    // F-103: chiến dịch của chính tổ chức đang đăng nhập
    Page<Campaign> findByOrganization_AccountId(UUID organizationId, Pageable pageable);

    Page<Campaign> findByOrganization_AccountIdAndStatus(UUID organizationId, CampaignStatus status,
                                                         Pageable pageable);

    // Duyệt công khai xuyên tổ chức (GET /campaigns?status=ACTIVE)
    Page<Campaign> findByStatus(CampaignStatus status, Pageable pageable);

    // GET /api/organizations/nearby: chiến dịch đang mở của nhiều tổ chức cùng lúc, cho bản đồ cộng đồng
    List<Campaign> findAllByOrganization_AccountIdInAndStatus(List<UUID> organizationIds, CampaignStatus status);
}
