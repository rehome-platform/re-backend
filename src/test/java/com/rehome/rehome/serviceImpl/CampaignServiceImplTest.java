package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.request.CreateCampaignRequest;
import com.rehome.rehome.dto.response.CampaignStatusResponse;
import com.rehome.rehome.dto.response.CampaignPublicResponse;
import com.rehome.rehome.dto.response.CampaignSummaryResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.CampaignStatus;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.entity.marketplace.Campaign;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.repository.CampaignRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.service.SystemConfigReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignServiceImplTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private OrganizationProfileRepository organizationProfileRepository;
    @Mock private SystemConfigReader systemConfigReader;

    @InjectMocks private CampaignServiceImpl service;

    private Campaign.CampaignBuilder baseCampaign(UUID id) {
        return Campaign.builder()
                .id(id)
                .description("Thu áo ấm miền núi phía Bắc")
                .startTime(Instant.parse("2026-10-01T00:00:00Z"))
                .endTime(Instant.parse("2026-11-01T00:00:00Z"))
                .locationName("Điểm nhận đồ Q.1")
                .latitude(new BigDecimal("10.7769000"))
                .longitude(new BigDecimal("106.7009000"));
    }

    @Test
    void getPublicDetail_open_mapsToActiveStatus() {
        UUID id = UUID.randomUUID();
        Campaign campaign = baseCampaign(id).status(CampaignStatus.OPEN).build();
        when(campaignRepository.findById(id)).thenReturn(Optional.of(campaign));

        CampaignPublicResponse res = service.getPublicDetail(id);

        assertThat(res.getId()).isEqualTo(id.toString());
        assertThat(res.getDescription()).isEqualTo("Thu áo ấm miền núi phía Bắc");
        assertThat(res.getStartTime()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(res.getEndTime()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(res.getLocationName()).isEqualTo("Điểm nhận đồ Q.1");
        assertThat(res.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void getPublicDetail_closed_mapsToClosedStatus() {
        UUID id = UUID.randomUUID();
        Campaign campaign = baseCampaign(id).status(CampaignStatus.CLOSED).build();
        when(campaignRepository.findById(id)).thenReturn(Optional.of(campaign));

        CampaignPublicResponse res = service.getPublicDetail(id);

        assertThat(res.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    void getPublicDetail_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(campaignRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicDetail(id))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");
    }

    // =====================================================================
    // createCampaign  (F-098/F-099)
    // =====================================================================

    private static final Instant START = Instant.parse("2026-10-15T00:00:00Z");
    private static final Instant END = Instant.parse("2026-11-15T00:00:00Z");

    private CreateCampaignRequest createRequest() {
        return new CreateCampaignRequest(
                "Thu áo ấm miền núi phía Bắc", START, END, "Điểm nhận đồ Q.1",
                new BigDecimal("10.762"), new BigDecimal("106.682"), List.of("Áo ấm", "Chăn"));
    }

    private OrganizationProfile verifiedOrganization(UUID id) {
        return OrganizationProfile.builder().accountId(id)
                .verificationStatus(VerificationStatus.VERIFIED).build();
    }

    private void stubVerifiedOrgAndLimit(UUID orgId, int max, long running) {
        when(organizationProfileRepository.findById(orgId))
                .thenReturn(Optional.of(verifiedOrganization(orgId)));
        when(systemConfigReader.getInt(SystemConfigKeys.MAX_CONCURRENT_CAMPAIGNS,
                SystemConfigKeys.DEFAULT_MAX_CONCURRENT_CAMPAIGNS)).thenReturn(max);
        when(campaignRepository.countByOrganization_AccountIdAndStatus(orgId, CampaignStatus.OPEN))
                .thenReturn(running);
    }

    private void stubSaveAssignsId() {
        when(campaignRepository.save(any())).thenAnswer(invocation -> {
            Campaign saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void createCampaign_success_savesAndReturnsActiveStatus() {
        UUID orgId = UUID.randomUUID();
        stubVerifiedOrgAndLimit(orgId, 3, 1);
        stubSaveAssignsId();

        CampaignStatusResponse res = service.createCampaign(orgId, createRequest());

        assertThat(res.getId()).isNotNull();
        assertThat(res.getStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
        verify(campaignRepository).save(captor.capture());
        Campaign saved = captor.getValue();
        assertThat(saved.getOrganization().getAccountId()).isEqualTo(orgId);
        assertThat(saved.getDescription()).isEqualTo("Thu áo ấm miền núi phía Bắc");
        assertThat(saved.getStartTime()).isEqualTo(START);
        assertThat(saved.getEndTime()).isEqualTo(END);
        assertThat(saved.getLocationName()).isEqualTo("Điểm nhận đồ Q.1");
        assertThat(saved.getLatitude()).isEqualByComparingTo("10.762");
        assertThat(saved.getLongitude()).isEqualByComparingTo("106.682");
        assertThat(saved.getStatus()).isEqualTo(CampaignStatus.OPEN);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void createCampaign_joinsPreferredItemTypesIntoOneColumn() {
        UUID orgId = UUID.randomUUID();
        stubVerifiedOrgAndLimit(orgId, 3, 0);
        stubSaveAssignsId();

        service.createCampaign(orgId, createRequest());

        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
        verify(campaignRepository).save(captor.capture());
        assertThat(captor.getValue().getPreferredItemTypes()).isEqualTo("Áo ấm, Chăn");
    }

    @Test
    void createCampaign_withoutPreferredItemTypes_storesNull() {
        UUID orgId = UUID.randomUUID();
        stubVerifiedOrgAndLimit(orgId, 3, 0);
        stubSaveAssignsId();

        CreateCampaignRequest request = createRequest();
        request.setPreferredItemTypes(List.of());
        service.createCampaign(orgId, request);

        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
        verify(campaignRepository).save(captor.capture());
        assertThat(captor.getValue().getPreferredItemTypes()).isNull();
    }

    @Test
    void createCampaign_atLimit_throwsConflict() {
        UUID orgId = UUID.randomUUID();
        stubVerifiedOrgAndLimit(orgId, 3, 3);

        assertThatThrownBy(() -> service.createCampaign(orgId, createRequest()))
                .isInstanceOf(ConflictException.class)
                .hasMessage("CAMPAIGN_LIMIT_REACHED: đã đạt số chiến dịch đang chạy tối đa.")
                .satisfies(e -> assertThat(((ConflictException) e).getCode())
                        .isEqualTo("CAMPAIGN_LIMIT_REACHED"));

        verify(campaignRepository, never()).save(any());
    }

    @Test
    void createCampaign_limitComesFromSystemConfig() {
        UUID orgId = UUID.randomUUID();
        // Admin hạ hạn mức xuống 1 -> 1 chiến dịch đang chạy là đã chặn
        stubVerifiedOrgAndLimit(orgId, 1, 1);

        assertThatThrownBy(() -> service.createCampaign(orgId, createRequest()))
                .isInstanceOf(ConflictException.class);

        verify(campaignRepository, never()).save(any());
    }

    @Test
    void createCampaign_onlyCountsOpenCampaigns() {
        UUID orgId = UUID.randomUUID();
        stubVerifiedOrgAndLimit(orgId, 3, 2);
        stubSaveAssignsId();

        service.createCampaign(orgId, createRequest());

        // Chiến dịch đã đóng không tính vào hạn mức
        verify(campaignRepository).countByOrganization_AccountIdAndStatus(orgId, CampaignStatus.OPEN);
        verify(campaignRepository).save(any());
    }

    @Test
    void createCampaign_missingDescription_throwsBadRequest() {
        CreateCampaignRequest request = createRequest();
        request.setDescription("  ");

        assertThatThrownBy(() -> service.createCampaign(UUID.randomUUID(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("description, startTime, endTime là bắt buộc.");

        verifyNoInteractions(organizationProfileRepository, campaignRepository, systemConfigReader);
    }

    @Test
    void createCampaign_missingTimes_throwsBadRequest() {
        CreateCampaignRequest request = createRequest();
        request.setEndTime(null);

        assertThatThrownBy(() -> service.createCampaign(UUID.randomUUID(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("description, startTime, endTime là bắt buộc.");
    }

    @Test
    void createCampaign_missingLocation_throwsBadRequest() {
        CreateCampaignRequest request = createRequest();
        request.setLatitude(null);

        assertThatThrownBy(() -> service.createCampaign(UUID.randomUUID(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("locationName, latitude, longitude là bắt buộc.");

        verifyNoInteractions(organizationProfileRepository, campaignRepository);
    }

    @Test
    void createCampaign_endTimeNotAfterStartTime_throwsBadRequest() {
        CreateCampaignRequest request = createRequest();
        request.setEndTime(START);

        assertThatThrownBy(() -> service.createCampaign(UUID.randomUUID(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("endTime phải sau startTime.");

        verifyNoInteractions(organizationProfileRepository, campaignRepository);
    }

    @Test
    void createCampaign_organizationProfileNotFound_throws() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCampaign(orgId, createRequest()))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND));

        verify(campaignRepository, never()).save(any());
    }

    @Test
    void createCampaign_organizationNotVerified_throwsForbidden() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(
                OrganizationProfile.builder().accountId(orgId)
                        .verificationStatus(VerificationStatus.PENDING).build()));

        assertThatThrownBy(() -> service.createCampaign(orgId, createRequest()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Hồ sơ tổ chức chưa được xác minh.");

        verify(campaignRepository, never()).save(any());
    }

    // =====================================================================
    // updateCampaign / closeCampaign  (F-100, F-101)
    // =====================================================================

    private Campaign ownedCampaign(UUID campaignId, UUID orgId, CampaignStatus status) {
        return baseCampaign(campaignId)
                .organization(OrganizationProfile.builder().accountId(orgId).build())
                .status(status)
                .build();
    }

    // Chỉ gửi description + endTime, giống sample của API 120
    private CreateCampaignRequest partialUpdate(String description, Instant endTime) {
        return new CreateCampaignRequest(description, null, endTime, null, null, null, null);
    }

    @Test
    void updateCampaign_partialBody_keepsUntouchedFields() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = ownedCampaign(campaignId, orgId, CampaignStatus.OPEN);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        Instant newEnd = Instant.parse("2026-12-01T00:00:00Z");
        CampaignPublicResponse res = service.updateCampaign(orgId, campaignId,
                partialUpdate("Thu áo ấm miền núi phía Bắc (mở rộng)", newEnd));

        assertThat(campaign.getDescription()).isEqualTo("Thu áo ấm miền núi phía Bắc (mở rộng)");
        assertThat(campaign.getEndTime()).isEqualTo(newEnd);
        // Không gửi thì giữ nguyên — PUT toàn phần sẽ xoá mất các cột NOT NULL này
        assertThat(campaign.getStartTime()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(campaign.getLocationName()).isEqualTo("Điểm nhận đồ Q.1");
        assertThat(campaign.getLatitude()).isEqualByComparingTo("10.7769000");

        assertThat(res.getId()).isEqualTo(campaignId.toString());
        assertThat(res.getEndTime()).isEqualTo(newEnd);
        assertThat(res.getStatus()).isEqualTo("ACTIVE");
        verify(campaignRepository).save(campaign);
    }

    @Test
    void updateCampaign_updatesPreferredItemTypes() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = ownedCampaign(campaignId, orgId, CampaignStatus.OPEN);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        CreateCampaignRequest request = partialUpdate(null, null);
        request.setPreferredItemTypes(List.of("Áo khoác", "Giày"));
        service.updateCampaign(orgId, campaignId, request);

        assertThat(campaign.getPreferredItemTypes()).isEqualTo("Áo khoác, Giày");
    }

    @Test
    void updateCampaign_notFound_throws() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateCampaign(UUID.randomUUID(), campaignId,
                partialUpdate("mới", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");
    }

    @Test
    void updateCampaign_closedCampaign_throwsConflict() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId))
                .thenReturn(Optional.of(ownedCampaign(campaignId, orgId, CampaignStatus.CLOSED)));

        assertThatThrownBy(() -> service.updateCampaign(orgId, campaignId, partialUpdate("mới", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Chỉ sửa được chiến dịch đang mở.");

        verify(campaignRepository, never()).save(any());
    }

    @Test
    void updateCampaign_otherOrganization_throwsForbidden() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(
                ownedCampaign(campaignId, UUID.randomUUID(), CampaignStatus.OPEN)));

        assertThatThrownBy(() -> service.updateCampaign(UUID.randomUUID(), campaignId,
                partialUpdate("mới", null)))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Chiến dịch không thuộc tổ chức của bạn.");

        verify(campaignRepository, never()).save(any());
    }

    @Test
    void updateCampaign_newEndTimeBeforeExistingStartTime_throwsBadRequest() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = ownedCampaign(campaignId, orgId, CampaignStatus.OPEN);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        // Chỉ gửi endTime -> phải so với startTime đang lưu, không phải startTime trong request
        assertThatThrownBy(() -> service.updateCampaign(orgId, campaignId,
                partialUpdate(null, Instant.parse("2026-09-01T00:00:00Z"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("endTime phải sau startTime.");

        verify(campaignRepository, never()).save(any());
    }

    @Test
    void closeCampaign_open_marksClosed() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = ownedCampaign(campaignId, orgId, CampaignStatus.OPEN);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        CampaignStatusResponse res = service.closeCampaign(orgId, campaignId);

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.CLOSED);
        assertThat(res.getId()).isEqualTo(campaignId.toString());
        assertThat(res.getStatus()).isEqualTo("CLOSED");
        verify(campaignRepository).save(campaign);
    }

    @Test
    void closeCampaign_alreadyClosed_isIdempotent() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId))
                .thenReturn(Optional.of(ownedCampaign(campaignId, orgId, CampaignStatus.CLOSED)));

        CampaignStatusResponse res = service.closeCampaign(orgId, campaignId);

        assertThat(res.getStatus()).isEqualTo("CLOSED");
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void closeCampaign_notFound_throws() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.closeCampaign(UUID.randomUUID(), campaignId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");
    }

    @Test
    void closeCampaign_otherOrganization_throwsForbidden() {
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(
                ownedCampaign(campaignId, UUID.randomUUID(), CampaignStatus.OPEN)));

        assertThatThrownBy(() -> service.closeCampaign(UUID.randomUUID(), campaignId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Chiến dịch không thuộc tổ chức của bạn.");

        verify(campaignRepository, never()).save(any());
    }

    // =====================================================================
    // listMyCampaigns  (F-103)
    // =====================================================================

    @Test
    void listMyCampaigns_noFilter_returnsEveryStatus() {
        UUID orgId = UUID.randomUUID();
        UUID openId = UUID.randomUUID();
        UUID closedId = UUID.randomUUID();
        when(campaignRepository.findByOrganization_AccountId(eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        ownedCampaign(openId, orgId, CampaignStatus.OPEN),
                        ownedCampaign(closedId, orgId, CampaignStatus.CLOSED))));

        PageResponse<CampaignSummaryResponse> result = service.listMyCampaigns(orgId, null, 0, 20);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getId()).isEqualTo(openId.toString());
        assertThat(result.getContent().get(0).getDescription()).isEqualTo("Thu áo ấm miền núi phía Bắc");
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getContent().get(1).getStatus()).isEqualTo("CLOSED");
        verify(campaignRepository, never())
                .findByOrganization_AccountIdAndStatus(any(), any(), any());
    }

    @Test
    void listMyCampaigns_filterActive_queriesOpenCampaigns() {
        UUID orgId = UUID.randomUUID();
        when(campaignRepository.findByOrganization_AccountIdAndStatus(
                eq(orgId), eq(CampaignStatus.OPEN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        ownedCampaign(UUID.randomUUID(), orgId, CampaignStatus.OPEN))));

        PageResponse<CampaignSummaryResponse> result = service.listMyCampaigns(orgId, "ACTIVE", 0, 20);

        assertThat(result.getContent()).hasSize(1);
        verify(campaignRepository, never()).findByOrganization_AccountId(any(), any());
    }

    @Test
    void listMyCampaigns_filterOpen_isAlsoAccepted() {
        UUID orgId = UUID.randomUUID();
        when(campaignRepository.findByOrganization_AccountIdAndStatus(
                eq(orgId), eq(CampaignStatus.OPEN), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        // Tài liệu API viết "OPEN" nên vẫn nhận, dù response trả "ACTIVE"
        service.listMyCampaigns(orgId, "open", 0, 20);

        verify(campaignRepository).findByOrganization_AccountIdAndStatus(
                eq(orgId), eq(CampaignStatus.OPEN), any(Pageable.class));
    }

    @Test
    void listMyCampaigns_filterClosed_queriesClosedCampaigns() {
        UUID orgId = UUID.randomUUID();
        when(campaignRepository.findByOrganization_AccountIdAndStatus(
                eq(orgId), eq(CampaignStatus.CLOSED), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listMyCampaigns(orgId, "CLOSED", 0, 20);

        verify(campaignRepository).findByOrganization_AccountIdAndStatus(
                eq(orgId), eq(CampaignStatus.CLOSED), any(Pageable.class));
    }

    @Test
    void listMyCampaigns_invalidStatus_throwsBadRequest() {
        assertThatThrownBy(() -> service.listMyCampaigns(UUID.randomUUID(), "NOT_A_STATUS", 0, 20))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("status không hợp lệ.");

        verifyNoInteractions(campaignRepository);
    }

    @Test
    void listMyCampaigns_sortedNewestFirstAndSizeClamped() {
        UUID orgId = UUID.randomUUID();
        when(campaignRepository.findByOrganization_AccountId(eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listMyCampaigns(orgId, null, 0, 500);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(campaignRepository).findByOrganization_AccountId(eq(orgId), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(50);
        assertThat(captor.getValue().getSort().getOrderFor("createdAt").isDescending()).isTrue();
    }
}
