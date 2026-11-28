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

        for (Campaign campaign : pending) {
            campaign.setTitleNormalized(VietnameseText.normalize(campaign.getTitle()));
        }
        campaignRepository.saveAll(pending);

        log.info("[CampaignTitleNormalizeInitializer] Đã chuẩn hóa title cho {} chiến dịch.", pending.size());
    }
}
