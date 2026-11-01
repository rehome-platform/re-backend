package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.request.CreateCampaignRequest;
import com.rehome.rehome.dto.response.CampaignOwnAddressResponse;
import com.rehome.rehome.dto.response.CampaignPublicResponse;
import com.rehome.rehome.dto.response.CampaignStatusResponse;
import com.rehome.rehome.dto.response.CampaignSummaryResponse;
import com.rehome.rehome.dto.response.CampaignWarehouseResponse;
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
import com.rehome.rehome.service.CampaignService;
import com.rehome.rehome.service.SystemConfigReader;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CampaignServiceImpl implements CampaignService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String CAMPAIGN_NOT_FOUND = "Không tìm thấy chiến dịch.";
    private static final String INVALID_WAREHOUSE_IDS = "warehouseIds không hợp lệ.";
    private static final String RECEIVE_WAY_REQUIRED =
            "Phải chọn nhận tại địa chỉ riêng hoặc chọn ít nhất 1 kho.";

    private final CampaignRepository campaignRepository;
    private final OrganizationProfileRepository organizationProfileRepository;
    private final WarehouseRepository warehouseRepository;
    private final DonationRepository donationRepository;
    private final SystemConfigReader systemConfigReader;

    @Override
    @Transactional(readOnly = true)
    public CampaignPublicResponse getPublicDetail(UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException(CAMPAIGN_NOT_FOUND));
        return toPublicResponse(campaign);
    }

    @Override
    @Transactional
    public CampaignStatusResponse createCampaign(UUID organizationAccountId, CreateCampaignRequest request) {
        if (!StringUtils.hasText(request.getTitle())
                || !StringUtils.hasText(request.getDescription())
                || request.getStartTime() == null
                || request.getEndTime() == null) {
            throw new BadRequestException("title, description, startTime, endTime là bắt buộc.");
        }
        requireValidWindow(request.getStartTime(), request.getEndTime());

        boolean receiveAtOwnAddress = Boolean.TRUE.equals(request.getReceiveAtOwnAddress());
        Set<Warehouse> warehouses = resolveWarehouses(request.getWarehouseIds());
        if (!receiveAtOwnAddress && warehouses.isEmpty()) {
            throw new BadRequestException(RECEIVE_WAY_REQUIRED);
        }

        OrganizationProfile organization = organizationProfileRepository.findById(organizationAccountId)
                .orElseThrow(() -> new AppException(ErrorCode.ORGANIZATION_PROFILE_NOT_FOUND));
        // Chỉ tổ chức đã xác minh mới được tạo chiến dịch (docs/OVERVIEW.md mục 2, Actor Organization)
        if (organization.getVerificationStatus() != VerificationStatus.VERIFIED) {
            throw new ForbiddenException("Hồ sơ tổ chức chưa được xác minh.");
        }

        int maxConcurrent = systemConfigReader.getInt(
                SystemConfigKeys.MAX_CONCURRENT_CAMPAIGNS,
                SystemConfigKeys.DEFAULT_MAX_CONCURRENT_CAMPAIGNS);
        long running = campaignRepository.countByOrganization_AccountIdAndStatus(
                organizationAccountId, CampaignStatus.OPEN);
        if (running >= maxConcurrent) {
            throw new ConflictException(
                    "CAMPAIGN_LIMIT_REACHED: đã đạt số chiến dịch đang chạy tối đa.",
                    "CAMPAIGN_LIMIT_REACHED");
        }

        Campaign campaign = Campaign.builder()
                .organization(organization)
                .title(request.getTitle())
                .description(request.getDescription())
                .coverUrl(request.getCoverUrl())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .receiveAtOwnAddress(receiveAtOwnAddress)
                .warehouses(warehouses)
                .preferredItemTypes(joinPreferredItemTypes(request.getPreferredItemTypes()))
                .createdAt(Instant.now())
                .build();
        campaignRepository.save(campaign);

        return CampaignStatusResponse.builder()
                .id(campaign.getId().toString())
                .status(toApiStatus(campaign.getStatus()))
                .build();
    }

    @Override
    @Transactional
    public CampaignPublicResponse updateCampaign(UUID organizationAccountId, UUID campaignId,
                                                 CreateCampaignRequest request) {
        Campaign campaign = requireOwnCampaign(organizationAccountId, campaignId);
        if (campaign.getStatus() != CampaignStatus.OPEN) {
            throw new ConflictException("Chỉ sửa được chiến dịch đang mở.");
        }

        // Body giống API 119 nhưng field nào bỏ trống thì giữ nguyên: PUT toàn phần sẽ
        // xoá mất startTime/endTime (NOT NULL) khi client chỉ gửi vài field
        if (StringUtils.hasText(request.getTitle())) {
            campaign.setTitle(request.getTitle());
        }
        if (StringUtils.hasText(request.getDescription())) {
            campaign.setDescription(request.getDescription());
        }
        if (StringUtils.hasText(request.getCoverUrl())) {
            campaign.setCoverUrl(request.getCoverUrl());
        }
        if (request.getStartTime() != null) {
            campaign.setStartTime(request.getStartTime());
        }
        if (request.getEndTime() != null) {
            campaign.setEndTime(request.getEndTime());
        }
        if (request.getPreferredItemTypes() != null) {
            campaign.setPreferredItemTypes(joinPreferredItemTypes(request.getPreferredItemTypes()));
        }

        boolean receiveAtOwnAddress = request.getReceiveAtOwnAddress() != null
                ? request.getReceiveAtOwnAddress()
                : Boolean.TRUE.equals(campaign.getReceiveAtOwnAddress());

        if (request.getWarehouseIds() != null) {
            Set<Warehouse> newWarehouses = resolveWarehouses(request.getWarehouseIds());
            requireNoPendingPickupOnRemovedWarehouses(campaign.getWarehouses(), newWarehouses);
            campaign.setWarehouses(newWarehouses);
        }
        if (!receiveAtOwnAddress && campaign.getWarehouses().isEmpty()) {
            throw new BadRequestException(RECEIVE_WAY_REQUIRED);
        }
        campaign.setReceiveAtOwnAddress(receiveAtOwnAddress);

        // Kiểm tra trên giá trị sau khi ghép, vì client có thể chỉ gửi endTime
        requireValidWindow(campaign.getStartTime(), campaign.getEndTime());

        campaignRepository.save(campaign);
        return toPublicResponse(campaign);
    }

    @Override
    @Transactional
    public CampaignStatusResponse closeCampaign(UUID organizationAccountId, UUID campaignId) {
        Campaign campaign = requireOwnCampaign(organizationAccountId, campaignId);

        // Đóng lại chiến dịch đã đóng thì coi như không có gì xảy ra (spec không khai báo 409 ở đây)
        if (campaign.getStatus() != CampaignStatus.CLOSED) {
            campaign.setStatus(CampaignStatus.CLOSED);
            campaignRepository.save(campaign);
        }

        return CampaignStatusResponse.builder()
                .id(campaign.getId().toString())
                .status(toApiStatus(campaign.getStatus()))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CampaignSummaryResponse> listMyCampaigns(UUID organizationAccountId, String status,
                                                                  int page, int size) {
        CampaignStatus statusFilter = parseStatusFilter(status);
        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Campaign> result = statusFilter == null
                ? campaignRepository.findByOrganization_AccountId(organizationAccountId, pageable)
                : campaignRepository.findByOrganization_AccountIdAndStatus(
                        organizationAccountId, statusFilter, pageable);

        return PageResponse.from(result.map(this::toSummaryResponse));
    }

    // Response dùng ACTIVE/CLOSED nên filter cũng nhận đúng bộ chữ đó; vẫn chấp nhận
    // tên enum gốc OPEN để khớp với tài liệu API
    private static CampaignStatus parseStatusFilter(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        return switch (status.trim().toUpperCase()) {
            case "ACTIVE", "OPEN" -> CampaignStatus.OPEN;
            case "CLOSED" -> CampaignStatus.CLOSED;
            default -> throw new BadRequestException("status không hợp lệ.");
        };
    }

    private Campaign requireOwnCampaign(UUID organizationAccountId, UUID campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException(CAMPAIGN_NOT_FOUND));
        if (!campaign.getOrganization().getAccountId().equals(organizationAccountId)) {
            throw new ForbiddenException("Chiến dịch không thuộc tổ chức của bạn.");
        }
        return campaign;
    }

    private static void requireValidWindow(Instant startTime, Instant endTime) {
        if (!endTime.isAfter(startTime)) {
            throw new BadRequestException("endTime phải sau startTime.");
        }
    }

    // Kho đang có lượt chờ lấy thì không cho bỏ khỏi chiến dịch (mobile đã khoá sẵn ở UI)
    private void requireNoPendingPickupOnRemovedWarehouses(Set<Warehouse> currentWarehouses,
                                                            Set<Warehouse> newWarehouses) {
        Set<UUID> newIds = newWarehouses.stream().map(Warehouse::getId).collect(Collectors.toSet());
        for (Warehouse removed : currentWarehouses) {
            if (newIds.contains(removed.getId())) {
                continue;
            }
            long pending = donationRepository.countByWarehouse_IdAndStatus(
                    removed.getId(), DonationStatus.PENDING_PICKUP);
            if (pending > 0) {
                throw new ConflictException(
                        "WAREHOUSE_HAS_PENDING_PICKUP: kho \"" + removed.getName()
                                + "\" còn lượt quyên góp chờ lấy, không thể bỏ.",
                        "WAREHOUSE_HAS_PENDING_PICKUP");
            }
        }
    }

    private Set<Warehouse> resolveWarehouses(List<String> rawWarehouseIds) {
        if (rawWarehouseIds == null || rawWarehouseIds.isEmpty()) {
            return new HashSet<>();
        }
        Set<UUID> ids;
        try {
            ids = rawWarehouseIds.stream()
                    .map(id -> UUID.fromString(id.trim()))
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException(INVALID_WAREHOUSE_IDS);
        }
        List<Warehouse> found = warehouseRepository.findByIdInAndStatus(ids, CatalogStatus.ACTIVE);
        if (found.size() != ids.size()) {
            throw new BadRequestException(INVALID_WAREHOUSE_IDS);
        }
        return new HashSet<>(found);
    }

    private static String joinPreferredItemTypes(List<String> preferredItemTypes) {
        if (preferredItemTypes == null || preferredItemTypes.isEmpty()) {
            return null;
        }
        return preferredItemTypes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + ", " + right)
                .orElse(null);
    }

    private CampaignPublicResponse toPublicResponse(Campaign campaign) {
        boolean ownAddress = Boolean.TRUE.equals(campaign.getReceiveAtOwnAddress());
        return CampaignPublicResponse.builder()
                .id(campaign.getId().toString())
                .title(campaign.getTitle())
                .description(campaign.getDescription())
                .coverUrl(campaign.getCoverUrl())
                .startTime(campaign.getStartTime())
                .endTime(campaign.getEndTime())
                .status(toApiStatus(campaign.getStatus()))
                .receiveAtOwnAddress(ownAddress)
                .ownAddress(ownAddress ? toOwnAddressResponse(campaign.getOrganization()) : null)
                .warehouses(toWarehouseResponses(campaign.getWarehouses()))
                .build();
    }

    private CampaignSummaryResponse toSummaryResponse(Campaign campaign) {
        boolean ownAddress = Boolean.TRUE.equals(campaign.getReceiveAtOwnAddress());
        return CampaignSummaryResponse.builder()
                .id(campaign.getId().toString())
                .title(campaign.getTitle())
                .description(campaign.getDescription())
                .coverUrl(campaign.getCoverUrl())
                .startTime(campaign.getStartTime())
                .endTime(campaign.getEndTime())
                .status(toApiStatus(campaign.getStatus()))
                .receiveAtOwnAddress(ownAddress)
                .ownAddress(ownAddress ? toOwnAddressResponse(campaign.getOrganization()) : null)
                .warehouses(toWarehouseResponses(campaign.getWarehouses()))
                .build();
    }

    private static CampaignOwnAddressResponse toOwnAddressResponse(OrganizationProfile organization) {
        return CampaignOwnAddressResponse.builder()
                .name(organization.getDonationPointName())
                .address(organization.getDonationPointAddress())
                .latitude(organization.getDonationPointLatitude())
                .longitude(organization.getDonationPointLongitude())
                .receivingHours(organization.getDonationPointReceivingHours())
                .contactPhone(organization.getDonationPointContactPhone())
                .build();
    }

    private List<CampaignWarehouseResponse> toWarehouseResponses(Set<Warehouse> warehouses) {
        return warehouses.stream()
                .map(warehouse -> CampaignWarehouseResponse.builder()
                        .id(warehouse.getId().toString())
                        .name(warehouse.getName())
                        .address(warehouse.getAddress())
                        .openingHours(warehouse.getOpeningHours())
                        .pendingCount(donationRepository.countByWarehouse_IdAndStatus(
                                warehouse.getId(), DonationStatus.PENDING_PICKUP))
                        .build())
                .sorted(Comparator.comparing(CampaignWarehouseResponse::getName))
                .toList();
    }

    // FE hiển thị "ACTIVE"/"CLOSED"; DB giữ nguyên OPEN/CLOSED (xem F-098..F-102)
    private static String toApiStatus(CampaignStatus status) {
        return status == CampaignStatus.OPEN ? "ACTIVE" : "CLOSED";
    }
}
