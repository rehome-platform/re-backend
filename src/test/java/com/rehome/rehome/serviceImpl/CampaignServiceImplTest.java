package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.request.CreateCampaignRequest;
import com.rehome.rehome.dto.response.CampaignStatusResponse;
import com.rehome.rehome.dto.response.CampaignPublicResponse;
import com.rehome.rehome.dto.response.CampaignSummaryResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.CampaignStatus;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.enums.DonationStatus;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.entity.marketplace.Campaign;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.repository.CampaignRepository;
import com.rehome.rehome.repository.DonationRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.repository.WarehouseRepository;
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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CampaignServiceImplTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private OrganizationProfileRepository organizationProfileRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private DonationRepository donationRepository;
    @Mock private SystemConfigReader systemConfigReader;

    @InjectMocks private CampaignServiceImpl service;

    private Warehouse warehouse(UUID id, String name) {
        return Warehouse.builder()
                .id(id)
                .name(name)
                .address("123 Lê Lợi, Q.1")
                .openingHours("8h-17h")
                .status(CatalogStatus.ACTIVE)
                .build();
    }

    private OrganizationProfile organizationWithDonationPoint(UUID id) {
        return OrganizationProfile.builder()
                .accountId(id)
                .verificationStatus(VerificationStatus.VERIFIED)
                .donationPointName("Điểm nhận đồ ABC")
                .donationPointAddress("45 Nguyễn Huệ, Q.1")
                .donationPointLatitude(new BigDecimal("10.7769000"))
                .donationPointLongitude(new BigDecimal("106.7009000"))
                .donationPointReceivingHours("9h-18h")
                .donationPointContactPhone("0900000000")
                .build();
    }

    private Campaign.CampaignBuilder baseCampaign(UUID id) {
        return Campaign.builder()
                .id(id)
                .title("Thu áo ấm miền núi phía Bắc")
                .description("Thu áo ấm miền núi phía Bắc")
                .startTime(Instant.parse("2026-10-01T00:00:00Z"))
                .endTime(Instant.parse("2026-11-01T00:00:00Z"))
                .receiveAtOwnAddress(true)
                .warehouses(new HashSet<>());
    }

    @Test
    void getPublicDetail_open_mapsToActiveStatus() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Campaign campaign = baseCampaign(id)
                .organization(organizationWithDonationPoint(orgId))
                .status(CampaignStatus.OPEN)
                .build();
        when(campaignRepository.findById(id)).thenReturn(Optional.of(campaign));

        CampaignPublicResponse res = service.getPublicDetail(id);

        assertThat(res.getId()).isEqualTo(id.toString());
        assertThat(res.getTitle()).isEqualTo("Thu áo ấm miền núi phía Bắc");
        assertThat(res.getDescription()).isEqualTo("Thu áo ấm miền núi phía Bắc");
        assertThat(res.getStartTime()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(res.getEndTime()).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
        assertThat(res.getStatus()).isEqualTo("ACTIVE");
        assertThat(res.isReceiveAtOwnAddress()).isTrue();
        assertThat(res.getOwnAddress().getName()).isEqualTo("Điểm nhận đồ ABC");
        assertThat(res.getOwnAddress().getAddress()).isEqualTo("45 Nguyễn Huệ, Q.1");
        assertThat(res.getWarehouses()).isEmpty();
    }

    @Test
    void getPublicDetail_includesWarehousesWithPendingCount() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Campaign campaign = baseCampaign(id)
                .organization(organizationWithDonationPoint(orgId))
                .receiveAtOwnAddress(false)
                .warehouses(Set.of(warehouse(warehouseId, "Kho Quận 7")))
                .status(CampaignStatus.OPEN)
                .build();
        when(campaignRepository.findById(id)).thenReturn(Optional.of(campaign));
        when(donationRepository.countByWarehouse_IdAndStatus(warehouseId, DonationStatus.PENDING_PICKUP))
                .thenReturn(3L);

        CampaignPublicResponse res = service.getPublicDetail(id);

        assertThat(res.isReceiveAtOwnAddress()).isFalse();
        assertThat(res.getOwnAddress()).isNull();
        assertThat(res.getWarehouses()).hasSize(1);
        assertThat(res.getWarehouses().get(0).getId()).isEqualTo(warehouseId.toString());
        assertThat(res.getWarehouses().get(0).getName()).isEqualTo("Kho Quận 7");
        assertThat(res.getWarehouses().get(0).getPendingCount()).isEqualTo(3L);
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
    // createCampaign  (F-098/F-099, OC-02)
    // =====================================================================

    private static final Instant START = Instant.parse("2026-10-15T00:00:00Z");
    private static final Instant END = Instant.parse("2026-11-15T00:00:00Z");

    private CreateCampaignRequest createRequest() {
        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setTitle("Thu áo ấm miền núi phía Bắc");
        request.setDescription("Thu áo ấm miền núi phía Bắc");
        request.setStartTime(START);
        request.setEndTime(END);
        request.setCoverUrl("https://files.rehome.vn/campaign-covers/abc.jpg");
        request.setReceiveAtOwnAddress(true);
        request.setPreferredItemTypes(List.of("Áo ấm", "Chăn"));
        return request;
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
        assertThat(saved.getTitle()).isEqualTo("Thu áo ấm miền núi phía Bắc");
        assertThat(saved.getDescription()).isEqualTo("Thu áo ấm miền núi phía Bắc");
        assertThat(saved.getCoverUrl()).isEqualTo("https://files.rehome.vn/campaign-covers/abc.jpg");
        assertThat(saved.getStartTime()).isEqualTo(START);
        assertThat(saved.getEndTime()).isEqualTo(END);
        assertThat(saved.getReceiveAtOwnAddress()).isTrue();
        assertThat(saved.getWarehouses()).isEmpty();
        assertThat(saved.getStatus()).isEqualTo(CampaignStatus.OPEN);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void createCampaign_withWarehouseIds_resolvesActiveWarehouses() {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        stubVerifiedOrgAndLimit(orgId, 3, 0);
        stubSaveAssignsId();
        when(warehouseRepository.findByIdInAndStatus(anyCollection(), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(warehouse(warehouseId, "Kho Quận 7")));

        CreateCampaignRequest request = createRequest();
        request.setReceiveAtOwnAddress(false);
        request.setWarehouseIds(List.of(warehouseId.toString()));
        service.createCampaign(orgId, request);

        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
        verify(campaignRepository).save(captor.capture());
        assertThat(captor.getValue().getWarehouses()).hasSize(1);
        assertThat(captor.getValue().getReceiveAtOwnAddress()).isFalse();
    }

    @Test
    void createCampaign_canChooseBothOwnAddressAndWarehouses() {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        stubVerifiedOrgAndLimit(orgId, 3, 0);
        stubSaveAssignsId();
        when(warehouseRepository.findByIdInAndStatus(anyCollection(), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(warehouse(warehouseId, "Kho Quận 7")));

        CreateCampaignRequest request = createRequest();
        request.setReceiveAtOwnAddress(true);
        request.setWarehouseIds(List.of(warehouseId.toString()));
        service.createCampaign(orgId, request);

        ArgumentCaptor<Campaign> captor = ArgumentCaptor.forClass(Campaign.class);
        verify(campaignRepository).save(captor.capture());
        assertThat(captor.getValue().getReceiveAtOwnAddress()).isTrue();
        assertThat(captor.getValue().getWarehouses()).hasSize(1);
    }

    @Test
    void createCampaign_neitherOwnAddressNorWarehouses_throwsBadRequest() {
        CreateCampaignRequest request = createRequest();
        request.setReceiveAtOwnAddress(false);

        assertThatThrownBy(() -> service.createCampaign(UUID.randomUUID(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Phải chọn nhận tại địa chỉ riêng hoặc chọn ít nhất 1 kho.");

        verifyNoInteractions(organizationProfileRepository, campaignRepository);
    }

    @Test
    void createCampaign_warehouseIdNotFoundOrNotActive_throwsBadRequest() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findByIdInAndStatus(anyCollection(), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of());

        CreateCampaignRequest request = createRequest();
        request.setReceiveAtOwnAddress(false);
        request.setWarehouseIds(List.of(warehouseId.toString()));

        assertThatThrownBy(() -> service.createCampaign(UUID.randomUUID(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("warehouseIds không hợp lệ.");

        verifyNoInteractions(organizationProfileRepository, campaignRepository);
    }

    @Test
    void createCampaign_malformedWarehouseId_throwsBadRequest() {
        CreateCampaignRequest request = createRequest();
        request.setReceiveAtOwnAddress(false);
        request.setWarehouseIds(List.of("not-a-uuid"));

        assertThatThrownBy(() -> service.createCampaign(UUID.randomUUID(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("warehouseIds không hợp lệ.");

        verifyNoInteractions(organizationProfileRepository, campaignRepository, warehouseRepository);
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
    void createCampaign_missingTitle_throwsBadRequest() {
        CreateCampaignRequest request = createRequest();
        request.setTitle("  ");

        assertThatThrownBy(() -> service.createCampaign(UUID.randomUUID(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("title, description, startTime, endTime là bắt buộc.");

        verifyNoInteractions(organizationProfileRepository, campaignRepository, systemConfigReader);
    }

    @Test
    void createCampaign_missingDescription_throwsBadRequest() {
        CreateCampaignRequest request = createRequest();
        request.setDescription("  ");

        assertThatThrownBy(() -> service.createCampaign(UUID.randomUUID(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("title, description, startTime, endTime là bắt buộc.");

        verifyNoInteractions(organizationProfileRepository, campaignRepository, systemConfigReader);
    }

    @Test
    void createCampaign_missingTimes_throwsBadRequest() {
        CreateCampaignRequest request = createRequest();
        request.setEndTime(null);

        assertThatThrownBy(() -> service.createCampaign(UUID.randomUUID(), request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("title, description, startTime, endTime là bắt buộc.");
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
    // updateCampaign / closeCampaign  (F-100, F-101, OC-02)
    // =====================================================================

    private Campaign ownedCampaign(UUID campaignId, UUID orgId, CampaignStatus status) {
        return baseCampaign(campaignId)
                .organization(organizationWithDonationPoint(orgId))
                .status(status)
                .build();
    }

    // Chỉ gửi description + endTime, giống sample của API 120
    private CreateCampaignRequest partialUpdate(String description, Instant endTime) {
        CreateCampaignRequest request = new CreateCampaignRequest();
        request.setDescription(description);
        request.setEndTime(endTime);
        return request;
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
        // Không gửi thì giữ nguyên — PUT toàn phần sẽ xoá mất title/startTime (NOT NULL ở app) này
        assertThat(campaign.getTitle()).isEqualTo("Thu áo ấm miền núi phía Bắc");
        assertThat(campaign.getStartTime()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(campaign.getReceiveAtOwnAddress()).isTrue();

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
    void updateCampaign_addsWarehouses() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Campaign campaign = ownedCampaign(campaignId, orgId, CampaignStatus.OPEN);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(warehouseRepository.findByIdInAndStatus(anyCollection(), eq(CatalogStatus.ACTIVE)))
                .thenReturn(List.of(warehouse(warehouseId, "Kho Quận 7")));

        CreateCampaignRequest request = partialUpdate(null, null);
        request.setWarehouseIds(List.of(warehouseId.toString()));
        service.updateCampaign(orgId, campaignId, request);

        assertThat(campaign.getWarehouses()).hasSize(1);
    }

    @Test
    void updateCampaign_removesWarehouseWithoutPendingPickup_succeeds() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Campaign campaign = ownedCampaign(campaignId, orgId, CampaignStatus.OPEN);
        campaign.setReceiveAtOwnAddress(true);
        campaign.setWarehouses(Set.of(warehouse(warehouseId, "Kho Quận 7")));
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(donationRepository.countByWarehouse_IdAndStatus(warehouseId, DonationStatus.PENDING_PICKUP))
                .thenReturn(0L);

        CreateCampaignRequest request = partialUpdate(null, null);
        request.setWarehouseIds(List.of());
        service.updateCampaign(orgId, campaignId, request);

        assertThat(campaign.getWarehouses()).isEmpty();
    }

    @Test
    void updateCampaign_removesWarehouseWithPendingPickup_throwsConflictWithCode() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Campaign campaign = ownedCampaign(campaignId, orgId, CampaignStatus.OPEN);
        campaign.setReceiveAtOwnAddress(true);
        campaign.setWarehouses(Set.of(warehouse(warehouseId, "Kho Quận 7")));
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(donationRepository.countByWarehouse_IdAndStatus(warehouseId, DonationStatus.PENDING_PICKUP))
                .thenReturn(2L);

        CreateCampaignRequest request = partialUpdate(null, null);
        request.setWarehouseIds(List.of());

        assertThatThrownBy(() -> service.updateCampaign(orgId, campaignId, request))
                .isInstanceOf(ConflictException.class)
                .satisfies(e -> assertThat(((ConflictException) e).getCode())
                        .isEqualTo("WAREHOUSE_HAS_PENDING_PICKUP"));

        verify(campaignRepository, never()).save(any());
        // Kho vẫn còn trong chiến dịch vì không bị xoá
        assertThat(campaign.getWarehouses()).hasSize(1);
    }

    @Test
    void updateCampaign_clearingBothReceiveWays_throwsBadRequest() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Campaign campaign = ownedCampaign(campaignId, orgId, CampaignStatus.OPEN);
        campaign.setReceiveAtOwnAddress(true);
        campaign.setWarehouses(new HashSet<>());
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));

        CreateCampaignRequest request = partialUpdate(null, null);
        request.setReceiveAtOwnAddress(false);

        assertThatThrownBy(() -> service.updateCampaign(orgId, campaignId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Phải chọn nhận tại địa chỉ riêng hoặc chọn ít nhất 1 kho.");

        verify(campaignRepository, never()).save(any());
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
    // listMyCampaigns  (F-103, OC-03)
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
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Thu áo ấm miền núi phía Bắc");
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getContent().get(1).getStatus()).isEqualTo("CLOSED");
        verify(campaignRepository, never())
                .findByOrganization_AccountIdAndStatus(any(), any(), any());
    }

    @Test
    void listMyCampaigns_includesWarehousePendingCount() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Campaign campaign = ownedCampaign(campaignId, orgId, CampaignStatus.OPEN);
        campaign.setReceiveAtOwnAddress(false);
        campaign.setWarehouses(Set.of(warehouse(warehouseId, "Kho Quận 7")));
        when(campaignRepository.findByOrganization_AccountId(eq(orgId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(campaign)));
        when(donationRepository.countByWarehouse_IdAndStatus(warehouseId, DonationStatus.PENDING_PICKUP))
                .thenReturn(5L);

        PageResponse<CampaignSummaryResponse> result = service.listMyCampaigns(orgId, null, 0, 20);

        assertThat(result.getContent().get(0).getWarehouses()).hasSize(1);
        assertThat(result.getContent().get(0).getWarehouses().get(0).getPendingCount()).isEqualTo(5L);
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
