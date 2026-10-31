package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.CampaignStatus;
import com.rehome.rehome.entity.marketplace.Campaign;
import com.rehome.rehome.repository.CampaignRepository;
import com.rehome.rehome.service.CampaignAutoCloseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

// F-102: hết thời gian nhận đồ mà tổ chức chưa tự đóng (F-101) -> System đóng chiến dịch
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignAutoCloseServiceImpl implements CampaignAutoCloseService {

    private final CampaignRepository campaignRepository;

    @Override
    @Scheduled(fixedRateString = "${app.campaign.auto-close-check-interval-ms}")
    @Transactional
    public void closeExpiredCampaigns() {

        List<Campaign> expired = campaignRepository
                .findAllByStatusAndEndTimeBefore(CampaignStatus.OPEN, Instant.now());

        for (Campaign campaign : expired) {
            campaign.setStatus(CampaignStatus.CLOSED);
            campaignRepository.save(campaign);

            log.info(">>>> Tu dong dong chien dich het han: campaignId={}, endTime={}",
                    campaign.getId(), campaign.getEndTime());
        }
    }
}
