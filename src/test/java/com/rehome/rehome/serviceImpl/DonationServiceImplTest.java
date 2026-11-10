package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateCampaignDonationNoteRequest;
import com.rehome.rehome.dto.request.CreateDonationNoteRequest;
import com.rehome.rehome.dto.request.CreateDonationRequest;
import com.rehome.rehome.dto.request.HandoverDonationsRequest;
import com.rehome.rehome.dto.response.DonationHandoverResponse;
import com.rehome.rehome.dto.response.DonationNoteResponse;
import com.rehome.rehome.dto.response.DonationResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.CampaignStatus;
import com.rehome.rehome.entity.enums.DonationStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.entity.marketplace.Campaign;
import com.rehome.rehome.entity.marketplace.Donation;
import com.rehome.rehome.entity.marketplace.DonationNote;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.entity.enums.NotificationTargetType;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.repository.CampaignRepository;
import com.rehome.rehome.repository.DonationNoteRepository;
import com.rehome.rehome.repository.DonationRepository;
import com.rehome.rehome.repository.ModeratorProfileRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import com.rehome.rehome.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
class DonationServiceImplTest {

    @Mock private DonationRepository donationRepository;
    @Mock private DonationNoteRepository donationNoteRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private OrganizationProfileRepository organizationProfileRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private ModeratorProfileRepository moderatorProfileRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks private DonationServiceImpl service;

    private Account moderator(UUID warehouseId) {
        return Account.builder()
                .id(UUID.randomUUID())
                .role(Role.MODERATOR)
                .warehouse(warehouseId == null ? null : Warehouse.builder().id(warehouseId).build())
                .build();
    }

    private void stubSaveAssignsId() {
        when(donationRepository.save(any())).thenAnswer(invocation -> {
            Donation saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    // Chiến dịch có chọn warehouseId này để nhận đồ (xem Campaign.warehouses, OC-02).
    // Tổ chức sở hữu phải có account vì recordDonation báo DONATION_RECORDED cho account đó.
    private Campaign campaignServingWarehouse(UUID campaignId, UUID warehouseId) {
        UUID organizationId = UUID.randomUUID();
        return Campaign.builder()
                .id(campaignId)
                .warehouses(Set.of(Warehouse.builder().id(warehouseId).build()))
                .organization(OrganizationProfile.builder()
                        .accountId(organizationId)
                        .account(Account.builder().id(organizationId).build())
                        .build())
                .build();
    }

    private void stubCampaignServingWarehouse(UUID campaignId, UUID warehouseId) {
        when(campaignRepository.findById(campaignId))
                .thenReturn(Optional.of(campaignServingWarehouse(campaignId, warehouseId)));
    }

    @Test
    void recordDonation_named_success() {
        UUID warehouseId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder().id(warehouseId).name("Kho Quận 7").build();
        Account moderatorAccount = moderator(warehouseId);

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        stubCampaignServingWarehouse(campaignId, warehouseId);
        when(moderatorProfileRepository.findById(moderatorAccount.getId())).thenReturn(Optional.of(
                ModeratorProfile.builder().accountId(moderatorAccount.getId()).moderatorCode("MOD-07-003").build()));
        stubSaveAssignsId();

        DonationResponse res = service.recordDonation(moderatorAccount, warehouseId,
                new CreateDonationRequest(campaignId.toString(), "Nguyễn Văn A", false, "2 áo khoác, 1 chăn"));

        assertThat(res.getId()).isNotNull();
        assertThat(res.getWarehouseId()).isEqualTo(warehouseId.toString());
        assertThat(res.getCampaignId()).isEqualTo(campaignId.toString());
        assertThat(res.getDonorName()).isEqualTo("Nguyễn Văn A");
        assertThat(res.isAnonymous()).isFalse();
        assertThat(res.getNote()).isEqualTo("2 áo khoác, 1 chăn");
        assertThat(res.getStatus()).isEqualTo("PENDING_PICKUP");
        assertThat(res.getRecordedBy()).isEqualTo("MOD-07-003");
        assertThat(res.getCreatedAt()).isNotNull();

        ArgumentCaptor<Donation> captor = ArgumentCaptor.forClass(Donation.class);
        verify(donationRepository).save(captor.capture());
        assertThat(captor.getValue().getWarehouse()).isSameAs(warehouse);
        assertThat(captor.getValue().getDonorName()).isEqualTo("Nguyễn Văn A");
        assertThat(captor.getValue().getCampaign().getId()).isEqualTo(campaignId);
        assertThat(captor.getValue().getRecordedBy()).isSameAs(moderatorAccount);
    }

    @Test
    void recordDonation_notifiesCampaignOrganizationWithCampaignAndWarehouseTarget() {
        UUID warehouseId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder().id(warehouseId).name("Kho Quận 7").build();
        Account moderatorAccount = moderator(warehouseId);
        Campaign campaign = campaignServingWarehouse(campaignId, warehouseId);

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        stubSaveAssignsId();

        service.recordDonation(moderatorAccount, warehouseId,
                new CreateDonationRequest(campaignId.toString(), "Nguyễn Văn A", false, null));

        verify(notificationService).notify(
                eq(campaign.getOrganization().getAccount()),
                eq(NotificationType.DONATION_RECORDED),
                any(),
                any(),
                eq(NotificationTargetType.CAMPAIGN),
                eq(campaignId),
                eq(warehouseId));
    }

    @Test
    void recordDonation_anonymous_doesNotStoreDonorName() {
        UUID warehouseId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        stubCampaignServingWarehouse(campaignId, warehouseId);
        stubSaveAssignsId();

        // Client gửi kèm tên nhưng đã chọn ẩn danh -> không được lưu lại tên
        DonationResponse res = service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest(campaignId.toString(), "Nguyễn Văn A", true, "1 thùng quần áo"));

        assertThat(res.isAnonymous()).isTrue();
        assertThat(res.getDonorName()).isNull();

        ArgumentCaptor<Donation> captor = ArgumentCaptor.forClass(Donation.class);
        verify(donationRepository).save(captor.capture());
        assertThat(captor.getValue().getDonorName()).isNull();
        assertThat(captor.getValue().getIsAnonymous()).isTrue();
    }

    @Test
    void recordDonation_anonymousWithoutDonorName_isAllowed() {
        UUID warehouseId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        stubCampaignServingWarehouse(campaignId, warehouseId);
        stubSaveAssignsId();

        DonationResponse res = service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest(campaignId.toString(), null, true, null));

        assertThat(res.isAnonymous()).isTrue();
        assertThat(res.getDonorName()).isNull();
        assertThat(res.getNote()).isNull();
    }

    @Test
    void recordDonation_blankDonorNameAndNotAnonymous_throwsBadRequest() {
        UUID warehouseId = UUID.randomUUID();

        assertThatThrownBy(() -> service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest(UUID.randomUUID().toString(), "   ", false, "ghi chú")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng nhập tên người quyên góp hoặc chọn ẩn danh.");

        verifyNoInteractions(warehouseRepository, donationRepository);
    }

    @Test
    void recordDonation_missingIsAnonymousTreatedAsFalse_requiresDonorName() {
        UUID warehouseId = UUID.randomUUID();

        assertThatThrownBy(() -> service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest(UUID.randomUUID().toString(), null, null, "ghi chú")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng nhập tên người quyên góp hoặc chọn ẩn danh.");

        verifyNoInteractions(warehouseRepository, donationRepository);
    }

    @Test
    void recordDonation_warehouseNotFound_throws() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest(UUID.randomUUID().toString(), "Nguyễn Văn A", false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy điểm ký gửi.");

        verify(donationRepository, never()).save(any());
    }

    @Test
    void recordDonation_otherWarehouse_treatedAsNotFound() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));

        // Moderator của kho khác không ghi nhận được ở kho này
        assertThatThrownBy(() -> service.recordDonation(moderator(UUID.randomUUID()), warehouseId,
                new CreateDonationRequest(UUID.randomUUID().toString(), "Nguyễn Văn A", false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy điểm ký gửi.");

        verify(donationRepository, never()).save(any());
    }

    @Test
    void recordDonation_moderatorWithoutWarehouse_throws() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));

        assertThatThrownBy(() -> service.recordDonation(moderator(null), warehouseId,
                new CreateDonationRequest(UUID.randomUUID().toString(), "Nguyễn Văn A", false, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MODERATOR_NO_WAREHOUSE));

        verify(donationRepository, never()).save(any());
    }

    @Test
    void recordDonation_missingCampaignId_throwsBadRequest() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));

        assertThatThrownBy(() -> service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest(null, "Nguyễn Văn A", false, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng chọn chiến dịch.");

        verify(donationRepository, never()).save(any());
    }

    @Test
    void recordDonation_malformedCampaignId_throwsNotFound() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));

        assertThatThrownBy(() -> service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest("camp-01", "Nguyễn Văn A", false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");

        verifyNoInteractions(campaignRepository);
        verify(donationRepository, never()).save(any());
    }

    @Test
    void recordDonation_campaignNotFound_throwsNotFound() {
        UUID warehouseId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest(campaignId.toString(), "Nguyễn Văn A", false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");

        verify(donationRepository, never()).save(any());
    }

    @Test
    void recordDonation_campaignNotServingThisWarehouse_treatedAsNotFound() {
        UUID warehouseId = UUID.randomUUID();
        UUID otherWarehouseId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        // Chiến dịch có thật nhưng không chọn kho này để nhận đồ
        stubCampaignServingWarehouse(campaignId, otherWarehouseId);

        assertThatThrownBy(() -> service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest(campaignId.toString(), "Nguyễn Văn A", false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");

        verify(donationRepository, never()).save(any());
    }

    @Test
    void recordDonation_campaignHidden_throwsConflict() {
        UUID warehouseId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        Campaign hidden = campaignServingWarehouse(campaignId, warehouseId);
        hidden.setStatus(CampaignStatus.HIDDEN);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest(campaignId.toString(), "Nguyễn Văn A", false, null)))
                .isInstanceOf(ConflictException.class);

        verify(donationRepository, never()).save(any());
    }

    // =====================================================================
    // listDonations  (F-164)
    // =====================================================================

    private Donation donation(UUID warehouseId, String donorName, boolean anonymous) {
        return Donation.builder()
                .id(UUID.randomUUID())
                .warehouse(Warehouse.builder().id(warehouseId).build())
                .donorName(donorName)
                .isAnonymous(anonymous)
                .note("2 áo khoác, 1 chăn")
                .status(DonationStatus.PENDING_PICKUP)
                .createdAt(Instant.parse("2026-10-30T09:00:00Z"))
                .build();
    }

    @Test
    void listDonations_noFilter_returnsAll() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        when(donationRepository.findByWarehouse_IdOrderByCreatedAtDesc(warehouseId))
                .thenReturn(List.of(donation(warehouseId, "Nguyễn Văn A", false)));

        List<DonationResponse> result = service.listDonations(moderator(warehouseId), warehouseId, null, null);

        assertThat(result).hasSize(1);
        DonationResponse res = result.get(0);
        assertThat(res.getDonorName()).isEqualTo("Nguyễn Văn A");
        assertThat(res.isAnonymous()).isFalse();
        assertThat(res.getNote()).isEqualTo("2 áo khoác, 1 chăn");
        assertThat(res.getStatus()).isEqualTo("PENDING_PICKUP");
        assertThat(res.getCreatedAt()).isEqualTo(Instant.parse("2026-10-30T09:00:00Z"));
        verify(donationRepository, never()).findByWarehouse_IdAndStatusOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void listDonations_withStatusFilter_queriesByStatus() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        when(donationRepository.findByWarehouse_IdAndStatusOrderByCreatedAtDesc(
                warehouseId, DonationStatus.PENDING_PICKUP))
                .thenReturn(List.of(donation(warehouseId, null, true)));

        List<DonationResponse> result = service.listDonations(
                moderator(warehouseId), warehouseId, null, DonationStatus.PENDING_PICKUP);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isAnonymous()).isTrue();
        assertThat(result.get(0).getDonorName()).isNull();
        verify(donationRepository, never()).findByWarehouse_IdOrderByCreatedAtDesc(any());
    }

    @Test
    void listDonations_withCampaignFilter_queriesByCampaign() {
        UUID warehouseId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        when(donationRepository.findByWarehouse_IdAndCampaign_IdOrderByCreatedAtDesc(warehouseId, campaignId))
                .thenReturn(List.of(donation(warehouseId, "Nguyễn Văn A", false)));

        List<DonationResponse> result = service.listDonations(
                moderator(warehouseId), warehouseId, campaignId, null);

        assertThat(result).hasSize(1);
        verify(donationRepository, never()).findByWarehouse_IdOrderByCreatedAtDesc(any());
        verify(donationRepository, never()).findByWarehouse_IdAndStatusOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void listDonations_withCampaignAndStatusFilter_queriesByBoth() {
        UUID warehouseId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        when(donationRepository.findByWarehouse_IdAndCampaign_IdAndStatusOrderByCreatedAtDesc(
                warehouseId, campaignId, DonationStatus.PENDING_PICKUP))
                .thenReturn(List.of(donation(warehouseId, "Nguyễn Văn A", false)));

        List<DonationResponse> result = service.listDonations(
                moderator(warehouseId), warehouseId, campaignId, DonationStatus.PENDING_PICKUP);

        assertThat(result).hasSize(1);
        verify(donationRepository, never())
                .findByWarehouse_IdAndCampaign_IdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void listDonations_empty_returnsEmptyList() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        when(donationRepository.findByWarehouse_IdOrderByCreatedAtDesc(warehouseId)).thenReturn(List.of());

        assertThat(service.listDonations(moderator(warehouseId), warehouseId, null, null)).isEmpty();
    }

    @Test
    void listDonations_warehouseNotFound_throws() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listDonations(moderator(warehouseId), warehouseId, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy điểm ký gửi.");

        verifyNoInteractions(donationRepository);
    }

    @Test
    void listDonations_otherWarehouse_treatedAsNotFound() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));

        assertThatThrownBy(() -> service.listDonations(moderator(UUID.randomUUID()), warehouseId, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy điểm ký gửi.");

        verifyNoInteractions(donationRepository);
    }

    @Test
    void listDonations_moderatorWithoutWarehouse_throws() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));

        assertThatThrownBy(() -> service.listDonations(moderator(null), warehouseId, null, null))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MODERATOR_NO_WAREHOUSE));

        verifyNoInteractions(donationRepository);
    }

    // =====================================================================
    // handoverDonations  (F-165)
    // =====================================================================

    private static final String ORG_OR_WAREHOUSE_NOT_FOUND = "Không tìm thấy tổ chức hoặc điểm ký gửi.";
    private static final String INVALID_LIST = "Danh sách quyên góp không hợp lệ.";

    private Donation pendingDonation(UUID id, UUID warehouseId, UUID campaignId) {
        return Donation.builder()
                .id(id)
                .warehouse(Warehouse.builder().id(warehouseId).build())
                .campaign(campaignId == null ? null : Campaign.builder().id(campaignId).build())
                .donorName("Nguyễn Văn A")
                .isAnonymous(false)
                .status(DonationStatus.PENDING_PICKUP)
                .createdAt(Instant.now())
                .build();
    }

    private void stubWarehouse(UUID warehouseId) {
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
    }

    private void stubVerifiedOrganization(UUID organizationId) {
        when(organizationProfileRepository.findById(organizationId)).thenReturn(Optional.of(
                OrganizationProfile.builder().accountId(organizationId)
                        .verificationStatus(VerificationStatus.VERIFIED).build()));
    }

    private void stubOwnCampaign(UUID campaignId, UUID organizationId) {
        when(campaignRepository.findById(campaignId))
                .thenReturn(Optional.of(campaignOf(campaignId, organizationId)));
    }

    @Test
    void handoverDonations_success_marksAllHandedOver() {
        UUID warehouseId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Donation first = pendingDonation(firstId, warehouseId, campaignId);
        Donation second = pendingDonation(secondId, warehouseId, campaignId);

        stubWarehouse(warehouseId);
        stubVerifiedOrganization(organizationId);
        stubOwnCampaign(campaignId, organizationId);
        when(donationRepository.findByIdInAndWarehouse_Id(List.of(firstId, secondId), warehouseId))
                .thenReturn(List.of(first, second));

        DonationHandoverResponse res = service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(firstId.toString(), secondId.toString()), organizationId.toString(),
                        campaignId.toString()));

        assertThat(res.getHandedOverCount()).isEqualTo(2);
        assertThat(res.getOrganizationId()).isEqualTo(organizationId.toString());
        assertThat(res.getStatus()).isEqualTo("HANDED_OVER");

        assertThat(first.getStatus()).isEqualTo(DonationStatus.HANDED_OVER);
        assertThat(second.getStatus()).isEqualTo(DonationStatus.HANDED_OVER);
        assertThat(first.getHandedOverToOrganization().getAccountId()).isEqualTo(organizationId);
        assertThat(first.getHandedOverAt()).isNotNull();
        assertThat(second.getHandedOverAt()).isEqualTo(first.getHandedOverAt());
        verify(donationRepository).saveAll(List.of(first, second));
    }

    @Test
    void handoverDonations_campaignHidden_stillAllowed() {
        // Bàn giao là trả hàng ĐÃ ghi nhận trước đó - khác "ghi nhận mới" (recordDonation/
        // createDonationNote), nên không bị chặn khi chiến dịch đã bị ẩn do vi phạm, tránh kẹt
        // hàng tại kho (xem DonationServiceImpl.requireOwnCampaign)
        UUID warehouseId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID donationId = UUID.randomUUID();
        Donation donation = pendingDonation(donationId, warehouseId, campaignId);

        stubWarehouse(warehouseId);
        stubVerifiedOrganization(organizationId);
        Campaign hidden = campaignOf(campaignId, organizationId);
        hidden.setStatus(CampaignStatus.HIDDEN);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(hidden));
        when(donationRepository.findByIdInAndWarehouse_Id(List.of(donationId), warehouseId))
                .thenReturn(List.of(donation));

        DonationHandoverResponse res = service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(donationId.toString()), organizationId.toString(), campaignId.toString()));

        assertThat(res.getHandedOverCount()).isEqualTo(1);
        assertThat(donation.getStatus()).isEqualTo(DonationStatus.HANDED_OVER);
    }

    @Test
    void handoverDonations_duplicateIds_areDeduped() {
        UUID warehouseId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID donationId = UUID.randomUUID();

        stubWarehouse(warehouseId);
        stubVerifiedOrganization(organizationId);
        stubOwnCampaign(campaignId, organizationId);
        when(donationRepository.findByIdInAndWarehouse_Id(List.of(donationId), warehouseId))
                .thenReturn(List.of(pendingDonation(donationId, warehouseId, campaignId)));

        DonationHandoverResponse res = service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(donationId.toString(), donationId.toString()), organizationId.toString(),
                        campaignId.toString()));

        assertThat(res.getHandedOverCount()).isEqualTo(1);
    }

    @Test
    void handoverDonations_emptyIds_throwsBadRequest() {
        assertThatThrownBy(() -> service.handoverDonations(moderator(UUID.randomUUID()), UUID.randomUUID(),
                new HandoverDonationsRequest(List.of(), UUID.randomUUID().toString(), UUID.randomUUID().toString())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(INVALID_LIST);

        verifyNoInteractions(warehouseRepository, organizationProfileRepository, donationRepository);
    }

    @Test
    void handoverDonations_nullIds_throwsBadRequest() {
        assertThatThrownBy(() -> service.handoverDonations(moderator(UUID.randomUUID()), UUID.randomUUID(),
                new HandoverDonationsRequest(null, UUID.randomUUID().toString(), UUID.randomUUID().toString())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(INVALID_LIST);

        verifyNoInteractions(warehouseRepository, organizationProfileRepository, donationRepository);
    }

    @Test
    void handoverDonations_malformedDonationId_throwsBadRequest() {
        assertThatThrownBy(() -> service.handoverDonations(moderator(UUID.randomUUID()), UUID.randomUUID(),
                new HandoverDonationsRequest(
                        List.of("don-001"), UUID.randomUUID().toString(), UUID.randomUUID().toString())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(INVALID_LIST);

        verifyNoInteractions(warehouseRepository, organizationProfileRepository, donationRepository);
    }

    @Test
    void handoverDonations_someDonationNotPendingPickup_handsOverNothing() {
        UUID warehouseId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();
        UUID alreadyHandedId = UUID.randomUUID();
        Donation pending = pendingDonation(pendingId, warehouseId, campaignId);
        Donation alreadyHanded = pendingDonation(alreadyHandedId, warehouseId, campaignId);
        alreadyHanded.setStatus(DonationStatus.HANDED_OVER);

        stubWarehouse(warehouseId);
        stubVerifiedOrganization(organizationId);
        stubOwnCampaign(campaignId, organizationId);
        when(donationRepository.findByIdInAndWarehouse_Id(List.of(pendingId, alreadyHandedId), warehouseId))
                .thenReturn(List.of(pending, alreadyHanded));

        assertThatThrownBy(() -> service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(pendingId.toString(), alreadyHandedId.toString()), organizationId.toString(),
                        campaignId.toString())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(INVALID_LIST);

        // Không bàn giao từng phần: lượt còn chờ lấy phải giữ nguyên trạng thái
        assertThat(pending.getStatus()).isEqualTo(DonationStatus.PENDING_PICKUP);
        verify(donationRepository, never()).saveAll(any());
    }

    @Test
    void handoverDonations_unknownOrOtherWarehouseDonation_throwsBadRequest() {
        UUID warehouseId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID knownId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();

        stubWarehouse(warehouseId);
        stubVerifiedOrganization(organizationId);
        stubOwnCampaign(campaignId, organizationId);
        // Chỉ tìm thấy 1 trong 2 id -> id còn lại không tồn tại hoặc thuộc kho khác
        when(donationRepository.findByIdInAndWarehouse_Id(List.of(knownId, unknownId), warehouseId))
                .thenReturn(List.of(pendingDonation(knownId, warehouseId, campaignId)));

        assertThatThrownBy(() -> service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(knownId.toString(), unknownId.toString()), organizationId.toString(),
                        campaignId.toString())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(INVALID_LIST);

        verify(donationRepository, never()).saveAll(any());
    }

    @Test
    void handoverDonations_donationBelongsToOtherCampaign_handsOverNothing() {
        UUID warehouseId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID otherCampaignId = UUID.randomUUID();
        UUID donationId = UUID.randomUUID();
        Donation donation = pendingDonation(donationId, warehouseId, otherCampaignId);

        stubWarehouse(warehouseId);
        stubVerifiedOrganization(organizationId);
        stubOwnCampaign(campaignId, organizationId);
        when(donationRepository.findByIdInAndWarehouse_Id(List.of(donationId), warehouseId))
                .thenReturn(List.of(donation));

        assertThatThrownBy(() -> service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(donationId.toString()), organizationId.toString(), campaignId.toString())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(INVALID_LIST);

        assertThat(donation.getStatus()).isEqualTo(DonationStatus.PENDING_PICKUP);
        verify(donationRepository, never()).saveAll(any());
    }

    @Test
    void handoverDonations_missingCampaignId_throwsNotFound() {
        UUID warehouseId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        stubWarehouse(warehouseId);
        stubVerifiedOrganization(organizationId);

        assertThatThrownBy(() -> service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(UUID.randomUUID().toString()), organizationId.toString(), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");

        verify(donationRepository, never()).saveAll(any());
    }

    @Test
    void handoverDonations_campaignBelongsToOtherOrganization_throwsNotFound() {
        UUID warehouseId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        stubWarehouse(warehouseId);
        stubVerifiedOrganization(organizationId);
        when(campaignRepository.findById(campaignId))
                .thenReturn(Optional.of(campaignOf(campaignId, UUID.randomUUID())));

        assertThatThrownBy(() -> service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(UUID.randomUUID().toString()), organizationId.toString(), campaignId.toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");

        verify(donationRepository, never()).saveAll(any());
    }

    @Test
    void handoverDonations_organizationNotFound_throws() {
        UUID warehouseId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        stubWarehouse(warehouseId);
        when(organizationProfileRepository.findById(organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(UUID.randomUUID().toString()), organizationId.toString(),
                        UUID.randomUUID().toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ORG_OR_WAREHOUSE_NOT_FOUND);

        verify(donationRepository, never()).saveAll(any());
    }

    @Test
    void handoverDonations_organizationNotVerified_throws() {
        UUID warehouseId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        stubWarehouse(warehouseId);
        when(organizationProfileRepository.findById(organizationId)).thenReturn(Optional.of(
                OrganizationProfile.builder().accountId(organizationId)
                        .verificationStatus(VerificationStatus.PENDING).build()));

        assertThatThrownBy(() -> service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(UUID.randomUUID().toString()), organizationId.toString(),
                        UUID.randomUUID().toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ORG_OR_WAREHOUSE_NOT_FOUND);
    }

    @Test
    void handoverDonations_blankOrganizationId_throws() {
        UUID warehouseId = UUID.randomUUID();
        stubWarehouse(warehouseId);

        assertThatThrownBy(() -> service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(UUID.randomUUID().toString()), "  ", UUID.randomUUID().toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ORG_OR_WAREHOUSE_NOT_FOUND);
    }

    @Test
    void handoverDonations_warehouseNotFound_throwsCombinedMessage() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(UUID.randomUUID().toString()), UUID.randomUUID().toString(),
                        UUID.randomUUID().toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ORG_OR_WAREHOUSE_NOT_FOUND);

        verifyNoInteractions(organizationProfileRepository, donationRepository);
    }

    // =====================================================================
    // listOrganizationDonations  (F-166)
    // =====================================================================

    private Donation handedOverDonation(UUID warehouseId, UUID organizationId) {
        Donation donation = donation(warehouseId, "Trần Thị B", false);
        donation.setStatus(DonationStatus.HANDED_OVER);
        donation.setHandedOverToOrganization(
                OrganizationProfile.builder().accountId(organizationId).build());
        return donation;
    }

    private static final Pageable FIRST_PAGE_OF_20 = PageRequest.of(0, 20,
            org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));

    @Test
    void listOrganizationDonations_noFilter_returnsPendingPoolPlusOwnHistory() {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        when(donationRepository.findVisibleForOrganization(
                orgId, DonationStatus.PENDING_PICKUP, null, null, FIRST_PAGE_OF_20))
                .thenReturn(new PageImpl<>(List.of(
                        donation(warehouseId, "Nguyễn Văn A", false),
                        handedOverDonation(warehouseId, orgId))));

        PageResponse<DonationResponse> result =
                service.listOrganizationDonations(orgId, null, null, null, 0, 20);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getWarehouseId()).isEqualTo(warehouseId.toString());
        assertThat(result.getContent().get(0).getDonorName()).isEqualTo("Nguyễn Văn A");
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("PENDING_PICKUP");
        assertThat(result.getContent().get(1).getStatus()).isEqualTo("HANDED_OVER");
    }

    @Test
    void listOrganizationDonations_filterPendingPickup_readsSharedPool() {
        UUID orgId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        when(donationRepository.findByStatusWithFilters(
                DonationStatus.PENDING_PICKUP, null, null, FIRST_PAGE_OF_20))
                .thenReturn(new PageImpl<>(List.of(donation(UUID.randomUUID(), "Nguyễn Văn A", false))));

        PageResponse<DonationResponse> result = service.listOrganizationDonations(
                orgId, null, null, DonationStatus.PENDING_PICKUP, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        // Lượt chờ lấy là pool chung nên không giới hạn theo tổ chức
        verify(donationRepository, never()).findVisibleForOrganization(any(), any(), any(), any(), any());
        verify(donationRepository, never()).findHandedOverForOrganization(any(), any(), any(), any(), any());
    }

    @Test
    void listOrganizationDonations_filterHandedOver_onlyOwnHistory() {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        when(donationRepository.findHandedOverForOrganization(
                orgId, DonationStatus.HANDED_OVER, null, null, FIRST_PAGE_OF_20))
                .thenReturn(new PageImpl<>(List.of(handedOverDonation(warehouseId, orgId))));

        PageResponse<DonationResponse> result = service.listOrganizationDonations(
                orgId, null, null, DonationStatus.HANDED_OVER, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getStatus()).isEqualTo("HANDED_OVER");
        // Không lọt lịch sử của tổ chức khác
        verify(donationRepository).findHandedOverForOrganization(
                orgId, DonationStatus.HANDED_OVER, null, null, FIRST_PAGE_OF_20);
        verify(donationRepository, never()).findByStatusWithFilters(any(), any(), any(), any());
    }

    @Test
    void listOrganizationDonations_filtersByCampaignAndWarehouse() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        when(donationRepository.findVisibleForOrganization(
                orgId, DonationStatus.PENDING_PICKUP, campaignId, warehouseId, FIRST_PAGE_OF_20))
                .thenReturn(new PageImpl<>(List.of(donation(warehouseId, "Nguyễn Văn A", false))));

        PageResponse<DonationResponse> result =
                service.listOrganizationDonations(orgId, campaignId, warehouseId, null, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        verify(donationRepository).findVisibleForOrganization(
                orgId, DonationStatus.PENDING_PICKUP, campaignId, warehouseId, FIRST_PAGE_OF_20);
    }

    @Test
    void listOrganizationDonations_includesWarehouseName() {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        Donation withNamedWarehouse = donation(warehouseId, "Nguyễn Văn A", false);
        withNamedWarehouse.setWarehouse(Warehouse.builder().id(warehouseId).name("Kho Quận 7").build());
        when(donationRepository.findVisibleForOrganization(
                orgId, DonationStatus.PENDING_PICKUP, null, null, FIRST_PAGE_OF_20))
                .thenReturn(new PageImpl<>(List.of(withNamedWarehouse)));

        PageResponse<DonationResponse> result =
                service.listOrganizationDonations(orgId, null, null, null, 0, 20);

        assertThat(result.getContent().get(0).getWarehouseName()).isEqualTo("Kho Quận 7");
    }

    @Test
    void listOrganizationDonations_pageAndSize_clampedAndForwarded() {
        UUID orgId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        Pageable expected = PageRequest.of(2, 10,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        when(donationRepository.findVisibleForOrganization(
                orgId, DonationStatus.PENDING_PICKUP, null, null, expected))
                .thenReturn(new PageImpl<>(List.of()));

        service.listOrganizationDonations(orgId, null, null, null, 2, 10);

        verify(donationRepository).findVisibleForOrganization(
                orgId, DonationStatus.PENDING_PICKUP, null, null, expected);
    }

    @Test
    void listOrganizationDonations_anonymousDonation_hidesDonorName() {
        UUID orgId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        when(donationRepository.findVisibleForOrganization(
                orgId, DonationStatus.PENDING_PICKUP, null, null, FIRST_PAGE_OF_20))
                .thenReturn(new PageImpl<>(List.of(donation(UUID.randomUUID(), null, true))));

        PageResponse<DonationResponse> result =
                service.listOrganizationDonations(orgId, null, null, null, 0, 20);

        assertThat(result.getContent().get(0).isAnonymous()).isTrue();
        assertThat(result.getContent().get(0).getDonorName()).isNull();
    }

    @Test
    void listOrganizationDonations_empty_returnsEmptyPage() {
        UUID orgId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        when(donationRepository.findVisibleForOrganization(
                orgId, DonationStatus.PENDING_PICKUP, null, null, FIRST_PAGE_OF_20))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.listOrganizationDonations(orgId, null, null, null, 0, 20).getContent()).isEmpty();
    }

    @Test
    void listOrganizationDonations_organizationNotFound_throwsForbidden() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listOrganizationDonations(orgId, null, null, null, 0, 20))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không có quyền truy cập.");

        verifyNoInteractions(donationRepository);
    }

    @Test
    void listOrganizationDonations_organizationNotVerified_throwsForbidden() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.of(
                OrganizationProfile.builder().accountId(orgId)
                        .verificationStatus(VerificationStatus.PENDING).build()));

        assertThatThrownBy(() -> service.listOrganizationDonations(orgId, null, null, null, 0, 20))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Bạn không có quyền truy cập.");

        verifyNoInteractions(donationRepository);
    }

    // =====================================================================
    // createDonationNote  (F-167)
    // =====================================================================

    private void stubNoteSaveAssignsId() {
        when(donationNoteRepository.save(any())).thenAnswer(invocation -> {
            DonationNote saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    private Campaign campaignOf(UUID campaignId, UUID orgId) {
        return Campaign.builder()
                .id(campaignId)
                .organization(OrganizationProfile.builder().accountId(orgId).build())
                .build();
    }

    @Test
    void createDonationNote_withCampaign_success() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaignOf(campaignId, orgId)));
        stubNoteSaveAssignsId();

        DonationNoteResponse res = service.createDonationNote(orgId, new CreateDonationNoteRequest(
                campaignId.toString(), "Trần Thị B", false, "1 túi quần áo trẻ em"));

        assertThat(res.getId()).isNotNull();
        assertThat(res.getCampaignId()).isEqualTo(campaignId.toString());
        assertThat(res.getDonorName()).isEqualTo("Trần Thị B");
        assertThat(res.isAnonymous()).isFalse();
        assertThat(res.getNote()).isEqualTo("1 túi quần áo trẻ em");
        assertThat(res.getCreatedAt()).isNotNull();

        ArgumentCaptor<DonationNote> captor = ArgumentCaptor.forClass(DonationNote.class);
        verify(donationNoteRepository).save(captor.capture());
        assertThat(captor.getValue().getOrganization().getAccountId()).isEqualTo(orgId);
        assertThat(captor.getValue().getCampaign().getId()).isEqualTo(campaignId);
    }

    @Test
    void createDonationNote_withoutCampaign_success() {
        UUID orgId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        stubNoteSaveAssignsId();

        // Nhận đồ ngoài chiến dịch nào thì campaignId để trống
        DonationNoteResponse res = service.createDonationNote(orgId,
                new CreateDonationNoteRequest(null, "Trần Thị B", false, "1 túi quần áo"));

        assertThat(res.getCampaignId()).isNull();
        verifyNoInteractions(campaignRepository);
    }

    @Test
    void createDonationNote_anonymous_doesNotStoreDonorName() {
        UUID orgId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        stubNoteSaveAssignsId();

        DonationNoteResponse res = service.createDonationNote(orgId,
                new CreateDonationNoteRequest(null, "Trần Thị B", true, "1 túi quần áo"));

        assertThat(res.isAnonymous()).isTrue();
        assertThat(res.getDonorName()).isNull();

        ArgumentCaptor<DonationNote> captor = ArgumentCaptor.forClass(DonationNote.class);
        verify(donationNoteRepository).save(captor.capture());
        assertThat(captor.getValue().getDonorName()).isNull();
    }

    @Test
    void createDonationNote_blankDonorNameAndNotAnonymous_throwsBadRequest() {
        assertThatThrownBy(() -> service.createDonationNote(UUID.randomUUID(),
                new CreateDonationNoteRequest(null, "  ", false, "ghi chú")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng nhập tên người quyên góp hoặc chọn ẩn danh.");

        verifyNoInteractions(organizationProfileRepository, campaignRepository, donationNoteRepository);
    }

    @Test
    void createDonationNote_campaignNotFound_throws() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDonationNote(orgId,
                new CreateDonationNoteRequest(campaignId.toString(), "Trần Thị B", false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");

        verify(donationNoteRepository, never()).save(any());
    }

    @Test
    void createDonationNote_otherOrganizationCampaign_treatedAsNotFound() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        // Chiến dịch của tổ chức khác
        when(campaignRepository.findById(campaignId))
                .thenReturn(Optional.of(campaignOf(campaignId, UUID.randomUUID())));

        assertThatThrownBy(() -> service.createDonationNote(orgId,
                new CreateDonationNoteRequest(campaignId.toString(), "Trần Thị B", false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");

        verify(donationNoteRepository, never()).save(any());
    }

    @Test
    void createDonationNote_campaignHidden_throwsConflict() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        Campaign hidden = campaignOf(campaignId, orgId);
        hidden.setStatus(CampaignStatus.HIDDEN);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> service.createDonationNote(orgId,
                new CreateDonationNoteRequest(campaignId.toString(), "Trần Thị B", false, null)))
                .isInstanceOf(ConflictException.class);

        verify(donationNoteRepository, never()).save(any());
    }

    @Test
    void createDonationNote_malformedCampaignId_throwsNotFound() {
        UUID orgId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);

        assertThatThrownBy(() -> service.createDonationNote(orgId,
                new CreateDonationNoteRequest("camp-01", "Trần Thị B", false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");

        verifyNoInteractions(campaignRepository, donationNoteRepository);
    }

    @Test
    void createDonationNote_organizationProfileNotFound_throws() {
        UUID orgId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDonationNote(orgId,
                new CreateDonationNoteRequest(null, "Trần Thị B", false, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND));

        verify(donationNoteRepository, never()).save(any());
    }

    // =====================================================================
    // createCampaignDonationNote  (OC-08)
    // =====================================================================

    @Test
    void createCampaignDonationNote_named_success() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaignOf(campaignId, orgId)));
        stubNoteSaveAssignsId();

        DonationNoteResponse res = service.createCampaignDonationNote(orgId, campaignId,
                new CreateCampaignDonationNoteRequest("Trần Thị B", false, "1 túi quần áo trẻ em"));

        assertThat(res.getId()).isNotNull();
        assertThat(res.getCampaignId()).isEqualTo(campaignId.toString());
        assertThat(res.getDonorName()).isEqualTo("Trần Thị B");
        assertThat(res.isAnonymous()).isFalse();
        assertThat(res.getNote()).isEqualTo("1 túi quần áo trẻ em");
        assertThat(res.getCreatedAt()).isNotNull();

        ArgumentCaptor<DonationNote> captor = ArgumentCaptor.forClass(DonationNote.class);
        verify(donationNoteRepository).save(captor.capture());
        assertThat(captor.getValue().getOrganization().getAccountId()).isEqualTo(orgId);
        assertThat(captor.getValue().getCampaign().getId()).isEqualTo(campaignId);
    }

    @Test
    void createCampaignDonationNote_anonymous_doesNotStoreDonorName() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaignOf(campaignId, orgId)));
        stubNoteSaveAssignsId();

        DonationNoteResponse res = service.createCampaignDonationNote(orgId, campaignId,
                new CreateCampaignDonationNoteRequest("Trần Thị B", true, "1 túi quần áo"));

        assertThat(res.isAnonymous()).isTrue();
        assertThat(res.getDonorName()).isNull();

        ArgumentCaptor<DonationNote> captor = ArgumentCaptor.forClass(DonationNote.class);
        verify(donationNoteRepository).save(captor.capture());
        assertThat(captor.getValue().getDonorName()).isNull();
    }

    @Test
    void createCampaignDonationNote_blankDonorNameAndNotAnonymous_throwsBadRequest() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createCampaignDonationNote(orgId, campaignId,
                new CreateCampaignDonationNoteRequest("  ", false, "ghi chú")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng nhập tên người quyên góp hoặc chọn ẩn danh.");

        verifyNoInteractions(organizationProfileRepository, campaignRepository, donationNoteRepository);
    }

    @Test
    void createCampaignDonationNote_missingIsAnonymousTreatedAsFalse_requiresDonorName() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();

        assertThatThrownBy(() -> service.createCampaignDonationNote(orgId, campaignId,
                new CreateCampaignDonationNoteRequest(null, null, "ghi chú")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng nhập tên người quyên góp hoặc chọn ẩn danh.");

        verifyNoInteractions(organizationProfileRepository, campaignRepository, donationNoteRepository);
    }

    @Test
    void createCampaignDonationNote_campaignNotFound_throwsNotFound() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCampaignDonationNote(orgId, campaignId,
                new CreateCampaignDonationNoteRequest("Trần Thị B", false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");

        verify(donationNoteRepository, never()).save(any());
    }

    @Test
    void createCampaignDonationNote_campaignHidden_throwsConflict() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        Campaign hidden = campaignOf(campaignId, orgId);
        hidden.setStatus(CampaignStatus.HIDDEN);
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(hidden));

        assertThatThrownBy(() -> service.createCampaignDonationNote(orgId, campaignId,
                new CreateCampaignDonationNoteRequest("Trần Thị B", false, null)))
                .isInstanceOf(ConflictException.class);

        verify(donationNoteRepository, never()).save(any());
    }

    @Test
    void createCampaignDonationNote_otherOrganizationCampaign_treatedAsNotFound() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubVerifiedOrganization(orgId);
        // Chiến dịch của tổ chức khác
        when(campaignRepository.findById(campaignId))
                .thenReturn(Optional.of(campaignOf(campaignId, UUID.randomUUID())));

        assertThatThrownBy(() -> service.createCampaignDonationNote(orgId, campaignId,
                new CreateCampaignDonationNoteRequest("Trần Thị B", false, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");

        verify(donationNoteRepository, never()).save(any());
    }

    @Test
    void createCampaignDonationNote_organizationProfileNotFound_throws() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(organizationProfileRepository.findById(orgId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createCampaignDonationNote(orgId, campaignId,
                new CreateCampaignDonationNoteRequest("Trần Thị B", false, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND));

        verify(donationNoteRepository, never()).save(any());
    }

    // =====================================================================
    // listCampaignDonationNotes  (OC-08)
    // =====================================================================

    private DonationNote donationNote(UUID campaignId, String donorName, boolean anonymous) {
        return DonationNote.builder()
                .id(UUID.randomUUID())
                .campaign(Campaign.builder().id(campaignId).build())
                .donorName(donorName)
                .isAnonymous(anonymous)
                .note("1 túi quần áo trẻ em")
                .createdAt(Instant.parse("2026-10-30T09:00:00Z"))
                .build();
    }

    private static final Pageable NOTES_FIRST_PAGE_OF_20 = PageRequest.of(0, 20);

    @Test
    void listCampaignDonationNotes_ownCampaign_returnsPage() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubOwnCampaign(campaignId, orgId);
        when(donationNoteRepository.findByCampaign_IdOrderByCreatedAtDesc(campaignId, NOTES_FIRST_PAGE_OF_20))
                .thenReturn(new PageImpl<>(List.of(donationNote(campaignId, "Trần Thị B", false))));

        PageResponse<DonationNoteResponse> result =
                service.listCampaignDonationNotes(orgId, campaignId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        DonationNoteResponse res = result.getContent().get(0);
        assertThat(res.getCampaignId()).isEqualTo(campaignId.toString());
        assertThat(res.getDonorName()).isEqualTo("Trần Thị B");
        assertThat(res.isAnonymous()).isFalse();
        assertThat(res.getNote()).isEqualTo("1 túi quần áo trẻ em");
        assertThat(res.getCreatedAt()).isEqualTo(Instant.parse("2026-10-30T09:00:00Z"));
    }

    @Test
    void listCampaignDonationNotes_closedCampaign_stillReturnsNotes() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(
                Campaign.builder().id(campaignId)
                        .organization(OrganizationProfile.builder().accountId(orgId).build())
                        .status(com.rehome.rehome.entity.enums.CampaignStatus.CLOSED)
                        .build()));
        when(donationNoteRepository.findByCampaign_IdOrderByCreatedAtDesc(campaignId, NOTES_FIRST_PAGE_OF_20))
                .thenReturn(new PageImpl<>(List.of(donationNote(campaignId, "Trần Thị B", false))));

        PageResponse<DonationNoteResponse> result =
                service.listCampaignDonationNotes(orgId, campaignId, 0, 20);

        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void listCampaignDonationNotes_campaignNotFound_throwsNotFound() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listCampaignDonationNotes(orgId, campaignId, 0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy chiến dịch.");

        verifyNoInteractions(donationNoteRepository);
    }

    @Test
    void listCampaignDonationNotes_otherOrganizationCampaign_throwsForbidden() {
        UUID orgId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        stubOwnCampaign(campaignId, UUID.randomUUID());

        assertThatThrownBy(() -> service.listCampaignDonationNotes(orgId, campaignId, 0, 20))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Chiến dịch không thuộc tổ chức của bạn.");

        verifyNoInteractions(donationNoteRepository);
    }

    @Test
    void handoverDonations_otherWarehouse_throwsCombinedMessage() {
        UUID warehouseId = UUID.randomUUID();
        stubWarehouse(warehouseId);

        assertThatThrownBy(() -> service.handoverDonations(moderator(UUID.randomUUID()), warehouseId,
                new HandoverDonationsRequest(
                        List.of(UUID.randomUUID().toString()), UUID.randomUUID().toString(),
                        UUID.randomUUID().toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ORG_OR_WAREHOUSE_NOT_FOUND);

        verifyNoInteractions(organizationProfileRepository, donationRepository);
    }
}
