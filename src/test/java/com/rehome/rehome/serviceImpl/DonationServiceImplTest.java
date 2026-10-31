package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateDonationRequest;
import com.rehome.rehome.dto.request.HandoverDonationsRequest;
import com.rehome.rehome.dto.response.DonationHandoverResponse;
import com.rehome.rehome.dto.response.DonationResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.DonationStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.entity.marketplace.Donation;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.DonationRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DonationServiceImplTest {

    @Mock private DonationRepository donationRepository;
    @Mock private WarehouseRepository warehouseRepository;
    @Mock private OrganizationProfileRepository organizationProfileRepository;

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

    @Test
    void recordDonation_named_success() {
        UUID warehouseId = UUID.randomUUID();
        Warehouse warehouse = Warehouse.builder().id(warehouseId).name("Kho Quận 7").build();

        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.of(warehouse));
        stubSaveAssignsId();

        DonationResponse res = service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest("Nguyễn Văn A", false, "2 áo khoác, 1 chăn"));

        assertThat(res.getId()).isNotNull();
        assertThat(res.getWarehouseId()).isEqualTo(warehouseId.toString());
        assertThat(res.getDonorName()).isEqualTo("Nguyễn Văn A");
        assertThat(res.isAnonymous()).isFalse();
        assertThat(res.getNote()).isEqualTo("2 áo khoác, 1 chăn");
        assertThat(res.getStatus()).isEqualTo("PENDING_PICKUP");
        assertThat(res.getCreatedAt()).isNotNull();

        ArgumentCaptor<Donation> captor = ArgumentCaptor.forClass(Donation.class);
        verify(donationRepository).save(captor.capture());
        assertThat(captor.getValue().getWarehouse()).isSameAs(warehouse);
        assertThat(captor.getValue().getDonorName()).isEqualTo("Nguyễn Văn A");
    }

    @Test
    void recordDonation_anonymous_doesNotStoreDonorName() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        stubSaveAssignsId();

        // Client gửi kèm tên nhưng đã chọn ẩn danh -> không được lưu lại tên
        DonationResponse res = service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest("Nguyễn Văn A", true, "1 thùng quần áo"));

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
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        stubSaveAssignsId();

        DonationResponse res = service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest(null, true, null));

        assertThat(res.isAnonymous()).isTrue();
        assertThat(res.getDonorName()).isNull();
        assertThat(res.getNote()).isNull();
    }

    @Test
    void recordDonation_blankDonorNameAndNotAnonymous_throwsBadRequest() {
        UUID warehouseId = UUID.randomUUID();

        assertThatThrownBy(() -> service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest("   ", false, "ghi chú")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng nhập tên người quyên góp hoặc chọn ẩn danh.");

        verifyNoInteractions(warehouseRepository, donationRepository);
    }

    @Test
    void recordDonation_missingIsAnonymousTreatedAsFalse_requiresDonorName() {
        UUID warehouseId = UUID.randomUUID();

        assertThatThrownBy(() -> service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest(null, null, "ghi chú")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Vui lòng nhập tên người quyên góp hoặc chọn ẩn danh.");

        verifyNoInteractions(warehouseRepository, donationRepository);
    }

    @Test
    void recordDonation_warehouseNotFound_throws() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordDonation(moderator(warehouseId), warehouseId,
                new CreateDonationRequest("Nguyễn Văn A", false, null)))
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
                new CreateDonationRequest("Nguyễn Văn A", false, null)))
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
                new CreateDonationRequest("Nguyễn Văn A", false, null)))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.MODERATOR_NO_WAREHOUSE));

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
    void listDonations_noStatusFilter_returnsAll() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        when(donationRepository.findByWarehouse_IdOrderByCreatedAtDesc(warehouseId))
                .thenReturn(List.of(donation(warehouseId, "Nguyễn Văn A", false)));

        List<DonationResponse> result = service.listDonations(moderator(warehouseId), warehouseId, null);

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
                moderator(warehouseId), warehouseId, DonationStatus.PENDING_PICKUP);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isAnonymous()).isTrue();
        assertThat(result.get(0).getDonorName()).isNull();
        verify(donationRepository, never()).findByWarehouse_IdOrderByCreatedAtDesc(any());
    }

    @Test
    void listDonations_empty_returnsEmptyList() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));
        when(donationRepository.findByWarehouse_IdOrderByCreatedAtDesc(warehouseId)).thenReturn(List.of());

        assertThat(service.listDonations(moderator(warehouseId), warehouseId, null)).isEmpty();
    }

    @Test
    void listDonations_warehouseNotFound_throws() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listDonations(moderator(warehouseId), warehouseId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy điểm ký gửi.");

        verifyNoInteractions(donationRepository);
    }

    @Test
    void listDonations_otherWarehouse_treatedAsNotFound() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));

        assertThatThrownBy(() -> service.listDonations(moderator(UUID.randomUUID()), warehouseId, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy điểm ký gửi.");

        verifyNoInteractions(donationRepository);
    }

    @Test
    void listDonations_moderatorWithoutWarehouse_throws() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId))
                .thenReturn(Optional.of(Warehouse.builder().id(warehouseId).build()));

        assertThatThrownBy(() -> service.listDonations(moderator(null), warehouseId, null))
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

    private Donation pendingDonation(UUID id, UUID warehouseId) {
        return Donation.builder()
                .id(id)
                .warehouse(Warehouse.builder().id(warehouseId).build())
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

    @Test
    void handoverDonations_success_marksAllHandedOver() {
        UUID warehouseId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        Donation first = pendingDonation(firstId, warehouseId);
        Donation second = pendingDonation(secondId, warehouseId);

        stubWarehouse(warehouseId);
        stubVerifiedOrganization(organizationId);
        when(donationRepository.findByIdInAndWarehouse_Id(List.of(firstId, secondId), warehouseId))
                .thenReturn(List.of(first, second));

        DonationHandoverResponse res = service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(firstId.toString(), secondId.toString()), organizationId.toString()));

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
    void handoverDonations_duplicateIds_areDeduped() {
        UUID warehouseId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID donationId = UUID.randomUUID();

        stubWarehouse(warehouseId);
        stubVerifiedOrganization(organizationId);
        when(donationRepository.findByIdInAndWarehouse_Id(List.of(donationId), warehouseId))
                .thenReturn(List.of(pendingDonation(donationId, warehouseId)));

        DonationHandoverResponse res = service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(donationId.toString(), donationId.toString()), organizationId.toString()));

        assertThat(res.getHandedOverCount()).isEqualTo(1);
    }

    @Test
    void handoverDonations_emptyIds_throwsBadRequest() {
        assertThatThrownBy(() -> service.handoverDonations(moderator(UUID.randomUUID()), UUID.randomUUID(),
                new HandoverDonationsRequest(List.of(), UUID.randomUUID().toString())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(INVALID_LIST);

        verifyNoInteractions(warehouseRepository, organizationProfileRepository, donationRepository);
    }

    @Test
    void handoverDonations_nullIds_throwsBadRequest() {
        assertThatThrownBy(() -> service.handoverDonations(moderator(UUID.randomUUID()), UUID.randomUUID(),
                new HandoverDonationsRequest(null, UUID.randomUUID().toString())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(INVALID_LIST);

        verifyNoInteractions(warehouseRepository, organizationProfileRepository, donationRepository);
    }

    @Test
    void handoverDonations_malformedDonationId_throwsBadRequest() {
        assertThatThrownBy(() -> service.handoverDonations(moderator(UUID.randomUUID()), UUID.randomUUID(),
                new HandoverDonationsRequest(List.of("don-001"), UUID.randomUUID().toString())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(INVALID_LIST);

        verifyNoInteractions(warehouseRepository, organizationProfileRepository, donationRepository);
    }

    @Test
    void handoverDonations_someDonationNotPendingPickup_handsOverNothing() {
        UUID warehouseId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID pendingId = UUID.randomUUID();
        UUID alreadyHandedId = UUID.randomUUID();
        Donation pending = pendingDonation(pendingId, warehouseId);
        Donation alreadyHanded = pendingDonation(alreadyHandedId, warehouseId);
        alreadyHanded.setStatus(DonationStatus.HANDED_OVER);

        stubWarehouse(warehouseId);
        stubVerifiedOrganization(organizationId);
        when(donationRepository.findByIdInAndWarehouse_Id(List.of(pendingId, alreadyHandedId), warehouseId))
                .thenReturn(List.of(pending, alreadyHanded));

        assertThatThrownBy(() -> service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(pendingId.toString(), alreadyHandedId.toString()), organizationId.toString())))
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
        UUID knownId = UUID.randomUUID();
        UUID unknownId = UUID.randomUUID();

        stubWarehouse(warehouseId);
        stubVerifiedOrganization(organizationId);
        // Chỉ tìm thấy 1 trong 2 id -> id còn lại không tồn tại hoặc thuộc kho khác
        when(donationRepository.findByIdInAndWarehouse_Id(List.of(knownId, unknownId), warehouseId))
                .thenReturn(List.of(pendingDonation(knownId, warehouseId)));

        assertThatThrownBy(() -> service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(knownId.toString(), unknownId.toString()), organizationId.toString())))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(INVALID_LIST);

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
                        List.of(UUID.randomUUID().toString()), organizationId.toString())))
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
                        List.of(UUID.randomUUID().toString()), organizationId.toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ORG_OR_WAREHOUSE_NOT_FOUND);
    }

    @Test
    void handoverDonations_blankOrganizationId_throws() {
        UUID warehouseId = UUID.randomUUID();
        stubWarehouse(warehouseId);

        assertThatThrownBy(() -> service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(List.of(UUID.randomUUID().toString()), "  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ORG_OR_WAREHOUSE_NOT_FOUND);
    }

    @Test
    void handoverDonations_warehouseNotFound_throwsCombinedMessage() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findById(warehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handoverDonations(moderator(warehouseId), warehouseId,
                new HandoverDonationsRequest(
                        List.of(UUID.randomUUID().toString()), UUID.randomUUID().toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ORG_OR_WAREHOUSE_NOT_FOUND);

        verifyNoInteractions(organizationProfileRepository, donationRepository);
    }

    @Test
    void handoverDonations_otherWarehouse_throwsCombinedMessage() {
        UUID warehouseId = UUID.randomUUID();
        stubWarehouse(warehouseId);

        assertThatThrownBy(() -> service.handoverDonations(moderator(UUID.randomUUID()), warehouseId,
                new HandoverDonationsRequest(
                        List.of(UUID.randomUUID().toString()), UUID.randomUUID().toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ORG_OR_WAREHOUSE_NOT_FOUND);

        verifyNoInteractions(organizationProfileRepository, donationRepository);
    }
}
