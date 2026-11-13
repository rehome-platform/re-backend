package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateCampaignDonationNoteRequest;
import com.rehome.rehome.dto.request.CreateDonationNoteRequest;
import com.rehome.rehome.dto.request.CreateDonationRequest;
import com.rehome.rehome.dto.request.HandoverDonationsRequest;
import com.rehome.rehome.dto.response.DonationHandoverResponse;
import com.rehome.rehome.dto.response.DonationProductResponse;
import com.rehome.rehome.dto.response.DonationNoteResponse;
import com.rehome.rehome.dto.response.DonationResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.ModeratorProfile;
import com.rehome.rehome.entity.auth.OrganizationProfile;
import com.rehome.rehome.entity.enums.CampaignStatus;
import com.rehome.rehome.entity.enums.DonationSource;
import com.rehome.rehome.entity.enums.DonationStatus;
import com.rehome.rehome.entity.enums.NotificationTargetType;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.enums.VerificationStatus;
import com.rehome.rehome.entity.marketplace.Campaign;
import com.rehome.rehome.entity.marketplace.Donation;
import com.rehome.rehome.entity.marketplace.DonationNote;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.marketplace.Warehouse;
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
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.WarehouseRepository;
import com.rehome.rehome.service.DonationService;
import com.rehome.rehome.service.NotificationService;
import com.rehome.rehome.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private static final int MAX_PAGE_SIZE = 50;

    private final DonationRepository donationRepository;
    private final DonationNoteRepository donationNoteRepository;
    private final WarehouseRepository warehouseRepository;
    private final OrganizationProfileRepository organizationProfileRepository;
    private final CampaignRepository campaignRepository;
    private final ModeratorProfileRepository moderatorProfileRepository;
    private final ProductPhotoRepository productPhotoRepository;
    private final NotificationService notificationService;
    private final QueueService queueService;

    @Override
    @Transactional
    public DonationResponse recordDonation(Account moderator, UUID warehouseId, CreateDonationRequest request) {
        boolean anonymous = Boolean.TRUE.equals(request.getIsAnonymous());
        if (!anonymous && !StringUtils.hasText(request.getDonorName())) {
            throw new BadRequestException("Vui lòng nhập tên người quyên góp hoặc chọn ẩn danh.");
        }

        Warehouse warehouse = requireOwnWarehouse(moderator, warehouseId, WAREHOUSE_NOT_FOUND);
        Campaign campaign = requireCampaignServingWarehouse(warehouse, request.getCampaignId());

        Donation donation = Donation.pendingAt(warehouse, campaign, request.getDonorName(), anonymous,
                        request.getNote())
                .recordedBy(moderator)
                .createdAt(Instant.now())
                .build();
        donationRepository.save(donation);

        // Báo cho tổ chức sở hữu chiến dịch, kèm deep-link mở đúng màn chờ lấy của kho này (OC-07)
        notificationService.notify(
                campaign.getOrganization().getAccount(),
                NotificationType.DONATION_RECORDED,
                "Có lượt quyên góp mới",
                "Điểm ký gửi \"" + warehouse.getName()
                        + "\" vừa ghi nhận 1 lượt quyên góp cho chiến dịch của bạn, đang chờ bạn đến lấy.",
                NotificationTargetType.CAMPAIGN,
                campaign.getId(),
                warehouse.getId());

        // Ghi nhận trực tiếp (DIRECT) không có món ký gửi nên không cần tra ảnh
        return toResponse(donation, resolveModeratorCode(moderator.getId()), Map.of());
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
        Map<String, String> photoByProductId = loadFirstPhotoUrls(donations);
        return donations.stream().map(d -> toResponse(d, moderatorProfiles, photoByProductId)).toList();
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
        requireNotHidden(campaign);
        return campaign;
    }

    // Chiến dịch bị Moderator ẩn do vi phạm thì không nhận thêm lượt quyên góp mới nào nữa, dù
    // ghi nhận tại kho (recordDonation) hay tự ghi note (createDonationNote/createCampaignDonationNote)
    private void requireNotHidden(Campaign campaign) {
        if (campaign.getStatus() == CampaignStatus.HIDDEN) {
            throw new ConflictException(
                    "Chiến dịch đang bị ẩn do vi phạm, không thể ghi nhận quyên góp mới.",
                    "CAMPAIGN_HIDDEN");
        }
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

        Warehouse warehouse = requireOwnWarehouse(moderator, warehouseId, ORGANIZATION_OR_WAREHOUSE_NOT_FOUND);
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

        for (Donation donation : donations) {
            if (donation.getSource() == DonationSource.CONSIGNED_ITEM && donation.getProduct() != null) {
                String refId = donation.getId().toString();
                queueService.dequeue(QueueType.DONATION_PREP, refId);
                queueService.dequeue(QueueType.DONATION_PICKUP, refId);
                Product product = donation.getProduct();
                notificationService.notifyProduct(product.getMember(), NotificationType.DONATION_HANDED_OVER,
                        "Đã bàn giao món quyên góp",
                        "Món " + product.getName() + " đã được bàn giao cho " + organization.getDisplayName(),
                        product.getId(), warehouseId);
            }
        }
        notificationService.notify(organization.getAccount(), NotificationType.DONATION_HANDED_OVER,
                "Đã bàn giao lượt quyên góp",
                "Đã nhận " + donations.size() + " lượt quyên góp tại " + warehouse.getName(),
                NotificationTargetType.CAMPAIGN, campaign.getId(), warehouseId);

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
    public PageResponse<DonationResponse> listOrganizationDonations(UUID organizationAccountId, UUID campaignId,
                                                                     UUID warehouseId, DonationStatus status,
                                                                     int page, int size) {
        requireVerifiedOrganizationAccount(organizationAccountId);

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        // PENDING_PICKUP luôn thuộc "pool chung" nên lọc theo status là chọn 1 nửa của phạm vi:
        // đang chờ lấy (mọi kho) hoặc lịch sử đã nhận của chính tổ chức này
        Page<Donation> donations;
        if (status == null) {
            donations = donationRepository.findVisibleForOrganization(
                    organizationAccountId, DonationStatus.PENDING_PICKUP, campaignId, warehouseId, pageable);
        } else if (status == DonationStatus.PENDING_PICKUP) {
            donations = donationRepository.findByStatusWithFilters(
                    DonationStatus.PENDING_PICKUP, campaignId, warehouseId, pageable);
        } else {
            donations = donationRepository.findHandedOverForOrganization(
                    organizationAccountId, status, campaignId, warehouseId, pageable);
        }

        Map<UUID, ModeratorProfile> moderatorProfiles = loadModeratorProfiles(donations.getContent());
        Map<String, String> photoByProductId = loadFirstPhotoUrls(donations.getContent());
        return PageResponse.from(donations.map(d -> toResponse(d, moderatorProfiles, photoByProductId)));
    }

    @Override
    @Transactional
    public DonationNoteResponse createDonationNote(UUID organizationAccountId,
                                                   CreateDonationNoteRequest request) {
        boolean anonymous = requireDonorNameOrAnonymous(request.getIsAnonymous(), request.getDonorName());

        OrganizationProfile organization = organizationProfileRepository.findById(organizationAccountId)
                .orElseThrow(() -> new AppException(ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND));
        // campaignId để trống nghĩa là nhận đồ ngoài chiến dịch nào
        Campaign campaign = resolveOwnCampaign(organizationAccountId, request.getCampaignId());
        if (campaign != null) {
            requireNotHidden(campaign);
        }

        return saveDonationNote(organization, campaign, request.getDonorName(), anonymous, request.getNote());
    }

    @Override
    @Transactional
    public DonationNoteResponse createCampaignDonationNote(UUID organizationAccountId, UUID campaignId,
                                                            CreateCampaignDonationNoteRequest request) {
        boolean anonymous = requireDonorNameOrAnonymous(request.getIsAnonymous(), request.getDonorName());

        OrganizationProfile organization = organizationProfileRepository.findById(organizationAccountId)
                .orElseThrow(() -> new AppException(ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND));
        // OC-08: khác F-167 ở chỗ campaignId bắt buộc (lấy từ path) và phải thuộc đúng tổ chức này
        Campaign campaign = campaignRepository.findById(campaignId)
                .filter(c -> c.getOrganization().getAccountId().equals(organizationAccountId))
                .orElseThrow(() -> new IllegalArgumentException(CAMPAIGN_NOT_FOUND));
        requireNotHidden(campaign);

        return saveDonationNote(organization, campaign, request.getDonorName(), anonymous, request.getNote());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<DonationNoteResponse> listCampaignDonationNotes(UUID organizationAccountId, UUID campaignId,
                                                                          int page, int size) {
        // Phải xem được cả khi chiến dịch đã CLOSED nên không lọc theo status ở đây
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException(CAMPAIGN_NOT_FOUND));
        if (!campaign.getOrganization().getAccountId().equals(organizationAccountId)) {
            throw new ForbiddenException("Chiến dịch không thuộc tổ chức của bạn.");
        }

        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        Page<DonationNote> notes = donationNoteRepository.findByCampaign_IdOrderByCreatedAtDesc(campaignId, pageable);
        return PageResponse.from(notes.map(DonationServiceImpl::toNoteResponse));
    }

    // Chưa có tên người quyên góp mà cũng không tích ẩn danh thì không hợp lệ, dùng chung
    // cho cả F-167 và OC-08
    private boolean requireDonorNameOrAnonymous(Boolean isAnonymousFlag, String donorName) {
        boolean anonymous = Boolean.TRUE.equals(isAnonymousFlag);
        if (!anonymous && !StringUtils.hasText(donorName)) {
            throw new BadRequestException("Vui lòng nhập tên người quyên góp hoặc chọn ẩn danh.");
        }
        return anonymous;
    }

    private DonationNoteResponse saveDonationNote(OrganizationProfile organization, Campaign campaign,
                                                   String donorName, boolean anonymous, String note) {
        DonationNote donationNote = DonationNote.builder()
                .organization(organization)
                .campaign(campaign)
                // Ẩn danh thì không lưu tên, giống F-163
                .donorName(anonymous ? null : donorName)
                .isAnonymous(anonymous)
                .note(note)
                .createdAt(Instant.now())
                .build();
        donationNoteRepository.save(donationNote);

        return toNoteResponse(donationNote);
    }

    private static DonationNoteResponse toNoteResponse(DonationNote donationNote) {
        Campaign campaign = donationNote.getCampaign();
        return DonationNoteResponse.builder()
                .id(donationNote.getId().toString())
                .campaignId(campaign == null ? null : campaign.getId().toString())
                .donorName(donationNote.getDonorName())
                .isAnonymous(Boolean.TRUE.equals(donationNote.getIsAnonymous()))
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

    // F-165: giống resolveOwnCampaign nhưng campaignId là bắt buộc khi bàn giao. Không chặn HIDDEN
    // ở đây: bàn giao là trả hàng ĐÃ ghi nhận trước đó cho tổ chức, khác "ghi nhận quyên góp mới"
    // (recordDonation/createDonationNote/createCampaignDonationNote) - chặn ở đây sẽ kẹt hàng tại kho.
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

    // Ảnh đại diện (displayOrder nhỏ nhất) của các món ký gửi được quyên góp, tra 1 query cho cả lô
    // để không phát sinh N+1. Chỉ lượt CONSIGNED_ITEM mới trả product nên chỉ tra cho các lượt đó.
    private Map<String, String> loadFirstPhotoUrls(List<Donation> donations) {
        Set<String> productIds = donations.stream()
                .filter(d -> d.getSource() == DonationSource.CONSIGNED_ITEM && d.getProduct() != null)
                .map(d -> d.getProduct().getId())
                .collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return productPhotoRepository.findByProduct_IdInOrderByDisplayOrderAscIdAsc(productIds).stream()
                .collect(Collectors.toMap(
                        photo -> photo.getProduct().getId(),
                        ProductPhoto::getImageUrl,
                        (first, ignored) -> first));
    }

    private static DonationResponse toResponse(Donation donation, Map<UUID, ModeratorProfile> moderatorProfiles,
                                               Map<String, String> photoByProductId) {
        String moderatorCode = null;
        if (donation.getRecordedBy() != null) {
            ModeratorProfile profile = moderatorProfiles.get(donation.getRecordedBy().getId());
            moderatorCode = profile != null ? profile.getModeratorCode() : null;
        }
        return toResponse(donation, moderatorCode, photoByProductId);
    }

    private static DonationResponse toResponse(Donation donation, String moderatorCode,
                                               Map<String, String> photoByProductId) {
        OrganizationProfile organization = donation.getCampaign() == null
                ? null : donation.getCampaign().getOrganization();
        OrganizationProfile recipient = donation.getHandedOverToOrganization();
        Product product = donation.getProduct();
        return DonationResponse.builder()
                .id(donation.getId().toString())
                .warehouseId(donation.getWarehouse().getId().toString())
                .warehouseName(donation.getWarehouse().getName())
                .campaignId(donation.getCampaign() == null ? null : donation.getCampaign().getId().toString())
                .campaignTitle(donation.getCampaign() == null ? null : donation.getCampaign().getTitle())
                .donorName(donation.getDonorName())
                .isAnonymous(Boolean.TRUE.equals(donation.getIsAnonymous()))
                .note(donation.getNote())
                .status(donation.getStatus().name())
                .source(donation.getSource().name())
                .recordedBy(moderatorCode)
                .organizationId(organization == null ? null : organization.getAccountId().toString())
                .organizationName(organization == null ? null : organization.getDisplayName())
                .handedOverToOrganizationName(recipient == null ? null : recipient.getDisplayName())
                .productId(product == null ? null : product.getId())
                .productName(product == null ? null : product.getName())
                // product chỉ trả khi source = CONSIGNED_ITEM (lượt DIRECT luôn null)
                .product(donation.getSource() == DonationSource.CONSIGNED_ITEM && product != null
                        ? DonationProductResponse.builder()
                                .id(product.getId())
                                .name(product.getName())
                                .sku(product.getId())
                                .photoUrl(photoByProductId.get(product.getId()))
                                .build()
                        : null)
                .handedOverAt(donation.getHandedOverAt())
                .createdAt(donation.getCreatedAt())
                .build();
    }
}
