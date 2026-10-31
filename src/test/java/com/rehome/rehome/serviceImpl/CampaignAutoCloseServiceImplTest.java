package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.entity.enums.CampaignStatus;
import com.rehome.rehome.entity.marketplace.Campaign;
import com.rehome.rehome.repository.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignAutoCloseServiceImplTest {

    @Mock private CampaignRepository campaignRepository;

    @InjectMocks private CampaignAutoCloseServiceImpl service;

    private Campaign openCampaign(UUID id, Instant endTime) {
        return Campaign.builder()
                .id(id)
                .description("Thu áo ấm miền núi phía Bắc")
                .startTime(endTime.minusSeconds(86_400))
                .endTime(endTime)
                .locationName("Điểm nhận đồ Q.1")
                .status(CampaignStatus.OPEN)
                .build();
    }

    @Test
    void closeExpiredCampaigns_closesEachExpiredCampaign() {
        Campaign first = openCampaign(UUID.randomUUID(), Instant.parse("2026-10-01T00:00:00Z"));
        Campaign second = openCampaign(UUID.randomUUID(), Instant.parse("2026-10-02T00:00:00Z"));
        when(campaignRepository.findAllByStatusAndEndTimeBefore(eq(CampaignStatus.OPEN), any(Instant.class)))
                .thenReturn(List.of(first, second));

        service.closeExpiredCampaigns();

        assertThat(first.getStatus()).isEqualTo(CampaignStatus.CLOSED);
        assertThat(second.getStatus()).isEqualTo(CampaignStatus.CLOSED);
        verify(campaignRepository).save(first);
        verify(campaignRepository).save(second);
    }

    @Test
    void closeExpiredCampaigns_onlyScansOpenCampaignsPastEndTime() {
        when(campaignRepository.findAllByStatusAndEndTimeBefore(eq(CampaignStatus.OPEN), any(Instant.class)))
                .thenReturn(List.of());

        Instant before = Instant.now();
        service.closeExpiredCampaigns();
        Instant after = Instant.now();

        ArgumentCaptor<Instant> captor = ArgumentCaptor.forClass(Instant.class);
        verify(campaignRepository).findAllByStatusAndEndTimeBefore(eq(CampaignStatus.OPEN), captor.capture());
        // Mốc so sánh là thời điểm chạy job; chiến dịch đã CLOSED không bị quét lại
        assertThat(captor.getValue()).isBetween(before, after);
    }

    @Test
    void closeExpiredCampaigns_nothingExpired_savesNothing() {
        when(campaignRepository.findAllByStatusAndEndTimeBefore(eq(CampaignStatus.OPEN), any(Instant.class)))
                .thenReturn(List.of());

        service.closeExpiredCampaigns();

        verify(campaignRepository, never()).save(any());
    }
}
