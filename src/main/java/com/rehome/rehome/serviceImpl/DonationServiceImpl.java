package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateDonationNoteRequest;
import com.rehome.rehome.dto.request.CreateDonationRequest;
import com.rehome.rehome.dto.request.HandoverDonationsRequest;
import com.rehome.rehome.dto.response.DonationHandoverResponse;
import com.rehome.rehome.dto.response.DonationNoteResponse;
import com.rehome.rehome.dto.response.DonationResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.DonationStatus;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.entity.marketplace.Campaign;
import com.rehome.rehome.entity.marketplace.Donation;
import com.rehome.rehome.entity.marketplace.DonationNote;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.repository.CampaignRepository;
import com.rehome.rehome.repository.DonationNoteRepository;
import com.rehome.rehome.repository.DonationRepository;
import com.rehome.rehome.repository.ModeratorProfileRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import com.rehome.rehome.service.DonationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DonationServiceImpl implements DonationService {

    private static final String WAREHOUSE_NOT_FOUND = "Không tìm thấy điểm ký gửi.";
    private static final String CAMPAIGN_NOT_FOUND = "Không tìm thấy chiến dịch.";
    private static final String ORGANIZATION_OR_WAREHOUSE_NOT_FOUND =
            "Không tìm thấy tổ chức hoặc điểm ký gửi.";
    private static final String INVALID_DONATION_LIST = "Danh sách quyên góp không hợp lệ.";

    private final DonationRepository donationRepository;
    private final DonationNoteRepository donationNoteRepository;
    private final WarehouseRepository warehouseRepository;
    private final OrganizationProfileRepository organizationProfileRepository;
    private final CampaignRepository campaignRepository;
    private final ModeratorProfileRepository moderatorProfileRepository;

    @Override
    @Transactional
    public DonationResponse recordDonation(Account moderator, UUID warehouseId, CreateDonationRequest request) {
        boolean anonymous = Boolean.TRUE.equals(request.getIsAnonymous());
        if (!anonymous && !StringUtils.hasText(request.getDonorName())) {
            throw new BadRequestException("Vui lòng nhập tên người quyên góp hoặc chọn ẩn danh.");
        }

        Warehouse warehouse = requireOwnWarehouse(moderator, warehouseId, WAREHOUSE_NOT_FOUND);
        Campaign campaign = requireCampaignServingWarehouse(warehouse, request.getCampaignId());

        Donation donation = Donation.builder()
                .warehouse(warehouse)
                .campaign(campaign)
                .recordedBy(moderator)
                // Ẩn danh thì không lưu tên, tránh giữ lại danh tính người đã chọn ẩn
                .donorName(anonymous ? null : request.getDonorName())
                .isAnonymous(anonymous)
                .note(request.getNote())
                .createdAt(Instant.now())
                .build();
        donationRepository.save(donation);

        return toResponse(donation, resolveModeratorCode(moderator.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DonationResponse> listDonations(Account moderator, UUID warehouseId, UUID campaignId,
                                                 DonationStatus status) {
        requireOwnWarehouse(moderator, warehouseId, WAREHOUSE_NOT_FOUND);

        List<Donation> donations;
        if (campaignId != null && status != null) {
            donations = donationRepository.findByWarehouse_IdAndCampaign_IdAndStatusOrderByCreatedAtDesc(
                    warehouseId, campaignId, status);
        } else if (campaignId != null) {
            donations = donationRepository.findByWarehouse_IdAndCampaign_IdOrderByCreatedAtDesc(
                    warehouseId, campaignId);
        } else if (status != null) {
            donations = donationRepository.findByWarehouse_IdAndStatusOrderByCreatedAtDesc(warehouseId, status);
        } else {
            donations = donationRepository.findByWarehouse_IdOrderByCreatedAtDesc(warehouseId);
        }

        Map<UUID, ModeratorProfile> moderatorProfiles = loadModeratorProfiles(donations);
        return donations.stream().map(d -> toResponse(d, moderatorProfiles)).toList();
    }

    // F-163: chiến dịch được chọn phải có nhận đồ tại chính kho đang ghi nhận, nếu không coi như không tồn tại
    private Campaign requireCampaignServingWarehouse(Warehouse warehouse, String rawCampaignId) {
        if (!StringUtils.hasText(rawCampaignId)) {
            throw new BadRequestException("Vui lòng chọn chiến dịch.");
        }
        UUID campaignId;
        try {
            campaignId = UUID.fromString(rawCampaignId.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(CAMPAIGN_NOT_FOUND);
        }
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException(CAMPAIGN_NOT_FOUND));
        boolean servesThisWarehouse = campaign.getWarehouses().stream()
                .anyMatch(w -> w.getId().equals(warehouse.getId()));
        if (!servesThisWarehouse) {
            throw new IllegalArgumentException(CAMPAIGN_NOT_FOUND);
        }
        return campaign;
    }

    private String resolveModeratorCode(UUID moderatorAccountId) {
        return moderatorProfileRepository.findById(moderatorAccountId)
                .map(ModeratorProfile::getModeratorCode)
                .orElse(null);
    }

    private Map<UUID, ModeratorProfile> loadModeratorProfiles(List<Donation> donations) {
        Set<UUID> ids = donations.stream()
                .map(Donation::getRecordedBy)
                .filter(Objects::nonNull)
                .map(Account::getId)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return moderatorProfileRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(ModeratorProfile::getAccountId, Function.identity(), (a, b) -> a));
    }

    @Override
    @Transactional
    public DonationHandoverResponse handoverDonations(Account moderator, UUID warehouseId,
                                                      HandoverDonationsRequest request) {
        List<UUID> donationIds = parseDonationIds(request.getDonationIds());

        requireOwnWarehouse(moderator, warehouseId, ORGANIZATION_OR_WAREHOUSE_NOT_FOUND);
        OrganizationProfile organization = requireVerifiedOrganization(request.getOrganizationId());
        // Chiến dịch bàn giao phải thuộc đúng tổ chức đến nhận, tránh bàn giao nhầm cho tổ chức khác
        Campaign campaign = requireOwnCampaign(organization.getAccountId(), request.getCampaignId());

        List<Donation> donations = donationRepository.findByIdInAndWarehouse_Id(donationIds, warehouseId);
        // Thiếu id nào (không tồn tại hoặc thuộc kho khác), có lượt không còn chờ lấy, hoặc có lượt
        // thuộc chiến dịch khác -> không bàn giao gì cả (không bàn giao từng phần)
        if (donations.size() != donationIds.size()
                || donations.stream().anyMatch(d -> d.getStatus() != DonationStatus.PENDING_PICKUP)
                || donations.stream().anyMatch(d -> d.getCampaign() == null
                        || !d.getCampaign().getId().equals(campaign.getId()))) {
            throw new BadRequestException(INVALID_DONATION_LIST);
        }

        Instant handedOverAt = Instant.now();
        donations.forEach(donation -> {
            donation.setStatus(DonationStatus.HANDED_OVER);
            donation.setHandedOverToOrganization(organization);
            donation.setHandedOverAt(handedOverAt);
        });
        donationRepository.saveAll(donations);

        return DonationHandoverResponse.builder()
                .handedOverCount(donations.size())
                .organizationId(organization.getAccountId().toString())
                .status(DonationStatus.HANDED_OVER.name())
                .build();
    }

    private List<UUID> parseDonationIds(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            throw new BadRequestException(INVALID_DONATION_LIST);
        }
        try {
            return rawIds.stream()
                    .map(id -> UUID.fromString(id.trim()))
                    .distinct()
                    .toList();
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException(INVALID_DONATION_LIST);
        }
    }

    // Chỉ tổ chức đã VERIFIED mới được nhận đồ quyên góp
    private OrganizationProfile requireVerifiedOrganization(String rawOrganizationId) {
        if (!StringUtils.hasText(rawOrganizationId)) {
            throw new IllegalArgumentException(ORGANIZATION_OR_WAREHOUSE_NOT_FOUND);
        }
        UUID organizationId;
        try {
            organizationId = UUID.fromString(rawOrganizationId.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(ORGANIZATION_OR_WAREHOUSE_NOT_FOUND);
        }
        return organizationProfileRepository.findById(organizationId)
                .filter(o -> o.getVerificationStatus() == VerificationStatus.VERIFIED)
                .orElseThrow(() -> new IllegalArgumentException(ORGANIZATION_OR_WAREHOUSE_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<DonationResponse> listOrganizationDonations(UUID organizationAccountId, DonationStatus status) {
        requireVerifiedOrganizationAccount(organizationAccountId);

        // PENDING_PICKUP luôn thuộc "pool chung" nên lọc theo status là chọn 1 nửa của phạm vi:
        // đang chờ lấy (mọi kho) hoặc lịch sử đã nhận của chính tổ chức này
        List<Donation> donations;
        if (status == null) {
            donations = donationRepository.findVisibleForOrganization(
                    organizationAccountId, DonationStatus.PENDING_PICKUP);
        } else if (status == DonationStatus.PENDING_PICKUP) {
            donations = donationRepository.findByStatusOrderByCreatedAtDesc(DonationStatus.PENDING_PICKUP);
        } else {
            donations = donationRepository
                    .findByHandedOverToOrganization_AccountIdAndStatusOrderByCreatedAtDesc(
                            organizationAccountId, status);
        }

        Map<UUID, ModeratorProfile> moderatorProfiles = loadModeratorProfiles(donations);
        return donations.stream().map(d -> toResponse(d, moderatorProfiles)).toList();
    }

    @Override
    @Transactional
    public DonationNoteResponse createDonationNote(UUID organizationAccountId,
                                                   CreateDonationNoteRequest request) {
        boolean anonymous = Boolean.TRUE.equals(request.getIsAnonymous());
        if (!anonymous && !StringUtils.hasText(request.getDonorName())) {
            throw new BadRequestException("Vui lòng nhập tên người quyên góp hoặc chọn ẩn danh.");
        }

        OrganizationProfile organization = organizationProfileRepository.findById(organizationAccountId)
                .orElseThrow(() -> new AppException(ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND));
        // campaignId để trống nghĩa là nhận đồ ngoài chiến dịch nào
        Campaign campaign = resolveOwnCampaign(organizationAccountId, request.getCampaignId());

        DonationNote donationNote = DonationNote.builder()
                .organization(organization)
                .campaign(campaign)
                // Ẩn danh thì không lưu tên, giống F-163
                .donorName(anonymous ? null : request.getDonorName())
                .isAnonymous(anonymous)
                .note(request.getNote())
                .createdAt(Instant.now())
                .build();
        donationNoteRepository.save(donationNote);

        return DonationNoteResponse.builder()
                .id(donationNote.getId().toString())
                .campaignId(campaign == null ? null : campaign.getId().toString())
                .donorName(donationNote.getDonorName())
                .isAnonymous(anonymous)
                .note(donationNote.getNote())
                .createdAt(donationNote.getCreatedAt())
                .build();
    }

    // Chiến dịch của tổ chức khác thì coi như không tìm thấy
    private Campaign resolveOwnCampaign(UUID organizationAccountId, String rawCampaignId) {
        if (!StringUtils.hasText(rawCampaignId)) {
            return null;
        }
        UUID campaignId;
        try {
            campaignId = UUID.fromString(rawCampaignId.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(CAMPAIGN_NOT_FOUND);
        }
        return campaignRepository.findById(campaignId)
                .filter(c -> c.getOrganization().getAccountId().equals(organizationAccountId))
                .orElseThrow(() -> new IllegalArgumentException(CAMPAIGN_NOT_FOUND));
    }

    // F-165: giống resolveOwnCampaign nhưng campaignId là bắt buộc khi bàn giao
    private Campaign requireOwnCampaign(UUID organizationAccountId, String rawCampaignId) {
        if (!StringUtils.hasText(rawCampaignId)) {
            throw new IllegalArgumentException(CAMPAIGN_NOT_FOUND);
        }
        return resolveOwnCampaign(organizationAccountId, rawCampaignId);
    }

    // F-166: chỉ tổ chức đã xác minh mới xem được danh sách quyên góp
    private void requireVerifiedOrganizationAccount(UUID organizationAccountId) {
        organizationProfileRepository.findById(organizationAccountId)
                .filter(o -> o.getVerificationStatus() == VerificationStatus.VERIFIED)
                .orElseThrow(() -> new ForbiddenException("Bạn không có quyền truy cập."));
    }

    // Moderator thuộc đúng 1 kho; kho khác thì coi như không tồn tại (giống QueueServiceImpl).
    // notFoundMessage khác nhau giữa các API nên truyền vào thay vì hằng số chung.
    private Warehouse requireOwnWarehouse(Account moderator, UUID warehouseId, String notFoundMessage) {
        Warehouse warehouse = warehouseRepository.findById(warehouseId)
                .orElseThrow(() -> new IllegalArgumentException(notFoundMessage));

        if (moderator.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);
        }
        if (!moderator.getWarehouse().getId().equals(warehouseId)) {
            throw new IllegalArgumentException(notFoundMessage);
        }
        return warehouse;
    }

    private static DonationResponse toResponse(Donation donation, Map<UUID, ModeratorProfile> moderatorProfiles) {
        String moderatorCode = null;
        if (donation.getRecordedBy() != null) {
            ModeratorProfile profile = moderatorProfiles.get(donation.getRecordedBy().getId());
            moderatorCode = profile != null ? profile.getModeratorCode() : null;
        }
        return toResponse(donation, moderatorCode);
    }

    private static DonationResponse toResponse(Donation donation, String moderatorCode) {
        return DonationResponse.builder()
                .id(donation.getId().toString())
                .warehouseId(donation.getWarehouse().getId().toString())
                .campaignId(donation.getCampaign() == null ? null : donation.getCampaign().getId().toString())
                .donorName(donation.getDonorName())
                .isAnonymous(Boolean.TRUE.equals(donation.getIsAnonymous()))
                .note(donation.getNote())
                .status(donation.getStatus().name())
                .recordedBy(moderatorCode)
                .handedOverAt(donation.getHandedOverAt())
                .createdAt(donation.getCreatedAt())
                .build();
    }
}
