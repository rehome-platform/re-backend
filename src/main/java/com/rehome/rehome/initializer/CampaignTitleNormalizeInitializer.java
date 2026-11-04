package com.rehome.rehome.initializer;

import com.rehome.rehome.entity.marketplace.Campaign;
import com.rehome.rehome.repository.CampaignRepository;
import com.rehome.rehome.util.VietnameseText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

// Điền title_normalized cho chiến dịch đã có trước khi cột này ra đời: ddl-auto chỉ thêm cột
// với giá trị null, mà null thì không khớp LIKE nào nên chiến dịch cũ sẽ biến mất khỏi kết quả
// tìm kiếm GET /campaigns?search=.
@Slf4j
@Component
@Order(103)
@RequiredArgsConstructor
public class CampaignTitleNormalizeInitializer implements ApplicationRunner {

    private final CampaignRepository campaignRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Campaign> pending = campaignRepository.findAllByTitleNormalizedIsNullAndTitleIsNotNull();

        if (pending.isEmpty()) {
            log.info("[CampaignTitleNormalizeInitializer] Mọi chiến dịch đã có title không dấu, bỏ qua.");
            return;
        }

        // Gán tay thay vì dựa vào @PreUpdate: entity không đổi gì thì Hibernate không phát UPDATE
        // và callback sẽ không chạy.
        for (Campaign campaign : pending) {
            campaign.setTitleNormalized(VietnameseText.normalize(campaign.getTitle()));
        }
        campaignRepository.saveAll(pending);

        log.info("[CampaignTitleNormalizeInitializer] Đã chuẩn hóa title cho {} chiến dịch.", pending.size());
    }
}
