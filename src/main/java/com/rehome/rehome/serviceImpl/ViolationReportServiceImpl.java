package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.dto.request.CreateViolationReportRequest;
import com.rehome.rehome.dto.request.DismissViolationReportRequest;
import com.rehome.rehome.dto.request.ResolveViolationReportRequest;
import com.rehome.rehome.dto.response.OrganizationViolationHistoryItem;
import com.rehome.rehome.dto.response.OrganizationViolationHistoryResponse;
import com.rehome.rehome.dto.response.ViolationReportDetailResponse;
import com.rehome.rehome.dto.response.ViolationReportGroupResponse;
import com.rehome.rehome.dto.response.ViolationReportResponse;
import com.rehome.rehome.dto.response.ViolationReportSiblingResponse;
import com.rehome.rehome.dto.response.ViolationTargetSnapshotResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CampaignStatus;
import com.rehome.rehome.entity.enums.ContentStatus;
import com.rehome.rehome.entity.enums.NotificationTargetType;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.enums.ViolationReportStatus;
import com.rehome.rehome.entity.enums.ViolationResolutionAction;
import com.rehome.rehome.entity.enums.ViolationTargetType;
import com.rehome.rehome.entity.marketplace.Campaign;
import com.rehome.rehome.entity.marketplace.CommunityPost;
import com.rehome.rehome.entity.marketplace.CommunityPostPhoto;
import com.rehome.rehome.entity.marketplace.ViolationReport;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CampaignRepository;
import com.rehome.rehome.repository.CommunityPostPhotoRepository;
import com.rehome.rehome.repository.CommunityPostRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.repository.ViolationReportRepository;
import com.rehome.rehome.service.NotificationService;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.service.ViolationReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ViolationReportServiceImpl implements ViolationReportService {

    private static final String MISSING_FIELDS_MESSAGE = "targetType, targetId và reason là bắt buộc.";

    private final ViolationReportRepository violationReportRepository;
    private final CommunityPostRepository communityPostRepository;
    private final CommunityPostPhotoRepository communityPostPhotoRepository;
    private final CampaignRepository campaignRepository;
    private final AccountRepository accountRepository;
    private final OrganizationProfileRepository organizationProfileRepository;
    private final QueueService queueService;
    private final NotificationService notificationService;
    private final AuditLogger auditLogger;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ViolationReportResponse create(UUID reporterId, CreateViolationReportRequest request) {
        if (!StringUtils.hasText(request.getTargetType())
                || !StringUtils.hasText(request.getTargetId())
                || !StringUtils.hasText(request.getReason())) {
            throw new BadRequestException(MISSING_FIELDS_MESSAGE);
        }

        ViolationTargetType targetType;
        UUID targetId;
        try {
            targetType = ViolationTargetType.valueOf(request.getTargetType().trim());
            targetId = UUID.fromString(request.getTargetId().trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(MISSING_FIELDS_MESSAGE);
        }

        if (!targetExists(targetType, targetId)) {
            throw new IllegalArgumentException("Không tìm thấy đối tượng để báo cáo.");
        }

        Account reporter = accountRepository.findById(reporterId)
                .orElseThrow(() -> new AppException(ErrorCode.UNAUTHORIZED));

        // Member đã có báo cáo PENDING cho đúng target này thì không cho báo cáo thêm lần nữa
        if (violationReportRepository.existsByReporterMember_IdAndTargetTypeAndTargetIdAndStatus(
                reporterId, targetType, targetId, ViolationReportStatus.PENDING)) {
            throw new AppException(ErrorCode.DUPLICATE_PENDING_REPORT);
        }

        ViolationReport report = ViolationReport.builder()
                .reporterMember(reporter)
                .targetType(targetType)
                .targetId(targetId)
                .reason(request.getReason())
                .build();
        violationReportRepository.save(report);
        queueService.enqueue(QueueType.VIOLATION_REPORT, report.getId().toString(), null);

        return ViolationReportResponse.builder()
                .id(report.getId().toString())
                .status(report.getStatus().name())
                .build();
    }

    private boolean targetExists(ViolationTargetType targetType, UUID targetId) {
        return switch (targetType) {
            case POST -> communityPostRepository.existsById(targetId);
            case CAMPAIGN -> campaignRepository.existsById(targetId);
            case ORGANIZATION_ACCOUNT -> accountRepository.existsById(targetId);
        };
    }

    // ---------------------------------------------------------------- GET /violation-reports

    @Override
    @Transactional(readOnly = true)
    public List<ViolationReportGroupResponse> list(String status, String type) {
        ViolationReportStatus statusFilter = parseStatusOrNull(status);
        ViolationTargetType typeFilter = parseTypeOrNull(type);

        List<ViolationReport> reports = fetchFiltered(statusFilter, typeFilter);

        // reports đã asc theo createdAt (xem các @Query trong repository) nên mỗi nhóm cũng asc
        // theo encounter order của groupingBy - khỏi sort lại trong nhóm
        Map<String, List<ViolationReport>> grouped = reports.stream()
                .collect(Collectors.groupingBy(r -> r.getTargetType() + "|" + r.getTargetId(),
                        LinkedHashMap::new, Collectors.toList()));

        return grouped.values().stream()
                .map(this::toGroupResponse)
                .sorted(Comparator.comparingLong(ViolationReportGroupResponse::getReportCount).reversed()
                        .thenComparing(ViolationReportGroupResponse::getOldestReportedAt))
                .toList();
    }

    private List<ViolationReport> fetchFiltered(ViolationReportStatus status, ViolationTargetType type) {
        if (status != null && type != null) {
            return violationReportRepository.findByStatusAndTargetTypeOrderByCreatedAtAsc(status, type);
        }
        if (status != null) {
            return violationReportRepository.findByStatusOrderByCreatedAtAsc(status);
        }
        if (type != null) {
            return violationReportRepository.findByTargetTypeOrderByCreatedAtAsc(type);
        }
        return violationReportRepository.findAllByOrderByCreatedAtAsc();
    }

    private ViolationReportGroupResponse toGroupResponse(List<ViolationReport> group) {
        ViolationReport oldest = group.get(0);
        ViolationReport latest = group.get(group.size() - 1);
        return ViolationReportGroupResponse.builder()
                .id(oldest.getId().toString())
                .targetType(oldest.getTargetType().name())
                .targetId(oldest.getTargetId().toString())
                .reportCount((long) group.size())
                .sampleReason(oldest.getReason())
                .oldestReportedAt(oldest.getCreatedAt())
                .latestReportedAt(latest.getCreatedAt())
                .build();
    }

    private ViolationReportStatus parseStatusOrNull(String status) {
        if (!StringUtils.hasText(status)) {
            return null;
        }
        try {
            return ViolationReportStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("status phải là PENDING, RESOLVED hoặc DISMISSED.");
        }
    }

    private ViolationTargetType parseTypeOrNull(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        try {
            return ViolationTargetType.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("type phải là POST, CAMPAIGN hoặc ORGANIZATION_ACCOUNT.");
        }
    }

    // ---------------------------------------------------------------- GET /violation-reports/{id}

    @Override
    @Transactional(readOnly = true)
    public ViolationReportDetailResponse getDetail(UUID id) {
        ViolationReport report = violationReportRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.VIOLATION_REPORT_NOT_FOUND));
        return toDetailResponse(report);
    }

    private ViolationReportDetailResponse toDetailResponse(ViolationReport report) {
        List<ViolationReport> siblings = violationReportRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtAsc(report.getTargetType(), report.getTargetId());

        ViolationTargetSnapshotResponse snapshot = buildSnapshot(report.getTargetType(), report.getTargetId());
        UUID organizationId = StringUtils.hasText(snapshot.getOrganizationId())
                ? UUID.fromString(snapshot.getOrganizationId()) : null;

        return ViolationReportDetailResponse.builder()
                .id(report.getId().toString())
                .targetType(report.getTargetType().name())
                .targetId(report.getTargetId().toString())
                .status(report.getStatus().name())
                .reason(report.getReason())
                .createdAt(report.getCreatedAt())
                .resolutionAction(report.getResolutionAction() == null ? null : report.getResolutionAction().name())
                .resolutionReason(report.getResolutionReason())
                .resolvedAt(report.getResolvedAt())
                .targetSnapshot(snapshot)
                .otherReports(siblings.stream()
                        .filter(r -> !r.getId().equals(report.getId()))
                        .map(ViolationReportServiceImpl::toSiblingResponse)
                        .toList())
                .organizationViolationHistory(organizationId == null ? null : buildHistory(organizationId))
                .build();
    }

    private static ViolationReportSiblingResponse toSiblingResponse(ViolationReport r) {
        return ViolationReportSiblingResponse.builder()
                .id(r.getId().toString())
                .reporterId(r.getReporterMember().getId().toString())
                .reason(r.getReason())
                .status(r.getStatus().name())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private ViolationTargetSnapshotResponse buildSnapshot(ViolationTargetType type, UUID targetId) {
        return switch (type) {
            case POST -> communityPostRepository.findById(targetId)
                    .map(p -> ViolationTargetSnapshotResponse.builder()
                            .targetType(type.name())
                            .targetId(targetId.toString())
                            .organizationId(p.getOrganization().getAccountId().toString())
                            .organizationDisplayName(p.getOrganization().getDisplayName())
                            .postContent(p.getContent())
                            .postPhotoUrls(communityPostPhotoRepository.findByPost_Id(targetId).stream()
                                    .map(CommunityPostPhoto::getImageUrl).toList())
                            .postStatus(p.getStatus().name())
                            .build())
                    .orElseGet(() -> emptySnapshot(type, targetId));
            case CAMPAIGN -> campaignRepository.findById(targetId)
                    .map(c -> ViolationTargetSnapshotResponse.builder()
                            .targetType(type.name())
                            .targetId(targetId.toString())
                            .organizationId(c.getOrganization().getAccountId().toString())
                            .organizationDisplayName(c.getOrganization().getDisplayName())
                            .campaignTitle(c.getTitle())
                            .campaignDescription(c.getDescription())
                            .campaignCoverUrl(c.getCoverUrl())
                            .campaignStatus(c.getStatus().name())
                            .build())
                    .orElseGet(() -> emptySnapshot(type, targetId));
            case ORGANIZATION_ACCOUNT -> organizationProfileRepository.findById(targetId)
                    .map(o -> ViolationTargetSnapshotResponse.builder()
                            .targetType(type.name())
                            .targetId(targetId.toString())
                            .organizationId(o.getAccountId().toString())
                            .organizationDisplayName(o.getDisplayName())
                            .organizationLegalName(o.getLegalName())
                            .organizationLogoUrl(o.getLogoUrl())
                            .organizationDescription(o.getDescription())
                            .organizationVerificationStatus(o.getVerificationStatus().name())
                            .build())
                    .orElseGet(() -> emptySnapshot(type, targetId));
        };
    }

    // Target đã bị xoá vật lý khỏi DB (hiếm, nhưng báo cáo cũ vẫn phải mở được) - trả khung rỗng
    // thay vì 404 cả trang chi tiết
    private static ViolationTargetSnapshotResponse emptySnapshot(ViolationTargetType type, UUID targetId) {
        return ViolationTargetSnapshotResponse.builder()
                .targetType(type.name())
                .targetId(targetId.toString())
                .build();
    }

    // Lịch sử vi phạm của 1 tổ chức: mỗi lần resolve đóng nhiều báo cáo PENDING của cùng target
    // trong 1 lượt (cùng resolvedAt) -> gộp lại thành 1 "incident" bằng (targetType, targetId,
    // resolvedAt) để không đếm trùng theo số người báo cáo.
    private OrganizationViolationHistoryResponse buildHistory(UUID organizationId) {
        List<ViolationReport> resolved = violationReportRepository.findResolvedByOrganizationId(organizationId);

        Map<String, ViolationReport> incidents = new LinkedHashMap<>();
        for (ViolationReport r : resolved) {
            String key = r.getTargetType() + "|" + r.getTargetId() + "|" + r.getResolvedAt();
            incidents.putIfAbsent(key, r);
        }

        List<OrganizationViolationHistoryItem> items = incidents.values().stream()
                .sorted(Comparator.comparing(ViolationReport::getResolvedAt).reversed())
                .map(r -> OrganizationViolationHistoryItem.builder()
                        .targetType(r.getTargetType().name())
                        .targetId(r.getTargetId().toString())
                        .action(r.getResolutionAction() == null ? null : r.getResolutionAction().name())
                        .reason(r.getResolutionReason())
                        .date(r.getResolvedAt())
                        .build())
                .toList();

        return OrganizationViolationHistoryResponse.builder()
                .organizationId(organizationId.toString())
                .totalViolationCount((long) items.size())
                .actions(items)
                .build();
    }

    // ---------------------------------------------------------------- resolve / dismiss

    @Override
    @Transactional
    public ViolationReportDetailResponse resolve(Account moderator, UUID id, ResolveViolationReportRequest request) {
        if (request == null || !StringUtils.hasText(request.getAction()) || !StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("action và reason là bắt buộc.");
        }
        ViolationResolutionAction action = parseAction(request.getAction());

        ViolationReport report = violationReportRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.VIOLATION_REPORT_NOT_FOUND));
        if (report.getStatus() != ViolationReportStatus.PENDING) {
            throw new AppException(ErrorCode.VIOLATION_REPORT_ALREADY_HANDLED);
        }

        UUID organizationId = executeAction(action, report);

        Instant now = Instant.now();
        List<ViolationReport> closed = violationReportRepository.findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtAsc(
                report.getTargetType(), report.getTargetId(), ViolationReportStatus.PENDING);
        for (ViolationReport r : closed) {
            r.setStatus(ViolationReportStatus.RESOLVED);
            r.setResolvedBy(moderator);
            r.setResolutionAction(action);
            r.setResolutionReason(request.getReason());
            r.setResolvedAt(now);
        }
        violationReportRepository.saveAll(closed);

        for (ViolationReport r : closed) {
            queueService.dequeue(QueueType.VIOLATION_REPORT, r.getId().toString());
        }

        writeAudit(moderator, "VIOLATION_RESOLVE_" + action.name(), report, request.getReason(), closed.size());

        notifyOrganizationWarning(report, action, request.getReason(), organizationId);
        notifyReporters(closed);

        return toDetailResponse(report);
    }

    @Override
    @Transactional
    public ViolationReportDetailResponse dismiss(Account moderator, UUID id, DismissViolationReportRequest request) {
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("reason là bắt buộc.");
        }

        ViolationReport report = violationReportRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.VIOLATION_REPORT_NOT_FOUND));
        if (report.getStatus() != ViolationReportStatus.PENDING) {
            throw new AppException(ErrorCode.VIOLATION_REPORT_ALREADY_HANDLED);
        }

        Instant now = Instant.now();
        List<ViolationReport> closed = violationReportRepository.findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtAsc(
                report.getTargetType(), report.getTargetId(), ViolationReportStatus.PENDING);
        for (ViolationReport r : closed) {
            r.setStatus(ViolationReportStatus.DISMISSED);
            r.setResolvedBy(moderator);
            r.setResolutionReason(request.getReason());
            r.setResolvedAt(now);
        }
        violationReportRepository.saveAll(closed);

        for (ViolationReport r : closed) {
            queueService.dequeue(QueueType.VIOLATION_REPORT, r.getId().toString());
        }

        writeAudit(moderator, "VIOLATION_DISMISS", report, request.getReason(), closed.size());

        notifyReporters(closed);

        return toDetailResponse(report);
    }

    private ViolationResolutionAction parseAction(String raw) {
        try {
            return ViolationResolutionAction.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("action phải là REMOVE_POST, HIDE_CAMPAIGN, WARN_ORG hoặc PROPOSE_BAN.");
        }
    }

    // Thực hiện hành động trên nội dung (nếu có) rồi trả về id tài khoản tổ chức sở hữu target,
    // để gửi cảnh báo và tính lịch sử vi phạm
    private UUID executeAction(ViolationResolutionAction action, ViolationReport report) {
        return switch (action) {
            case REMOVE_POST -> {
                requireTargetType(report, ViolationTargetType.POST, "REMOVE_POST");
                CommunityPost post = communityPostRepository.findById(report.getTargetId())
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết."));
                post.setStatus(ContentStatus.REMOVED);
                communityPostRepository.save(post);
                yield post.getOrganization().getAccountId();
            }
            case HIDE_CAMPAIGN -> {
                requireTargetType(report, ViolationTargetType.CAMPAIGN, "HIDE_CAMPAIGN");
                Campaign campaign = campaignRepository.findById(report.getTargetId())
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chiến dịch."));
                campaign.setStatus(CampaignStatus.HIDDEN);
                campaignRepository.save(campaign);
                yield campaign.getOrganization().getAccountId();
            }
            // WARN_ORG/PROPOSE_BAN không đổi trạng thái nội dung - chỉ cảnh báo/ghi nhận đề xuất
            case WARN_ORG, PROPOSE_BAN -> resolveOrganizationId(report);
        };
    }

    private void requireTargetType(ViolationReport report, ViolationTargetType expected, String actionName) {
        if (report.getTargetType() != expected) {
            throw new BadRequestException("action " + actionName + " chỉ áp dụng cho target " + expected.name() + ".");
        }
    }

    private UUID resolveOrganizationId(ViolationReport report) {
        return switch (report.getTargetType()) {
            case POST -> communityPostRepository.findById(report.getTargetId())
                    .map(p -> p.getOrganization().getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bài viết."));
            case CAMPAIGN -> campaignRepository.findById(report.getTargetId())
                    .map(c -> c.getOrganization().getAccountId())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy chiến dịch."));
            case ORGANIZATION_ACCOUNT -> report.getTargetId();
        };
    }

    private void writeAudit(Account moderator, String actionType, ViolationReport report, String reason,
                             int closedReportCount) {
        ObjectNode detail = objectMapper.createObjectNode();
        detail.put("reason", reason);
        detail.put("closedReportCount", closedReportCount);
        detail.put("primaryReportId", report.getId().toString());
        auditLogger.log(moderator, actionType, report.getTargetType().name(), report.getTargetId().toString(), detail);
    }

    // F-xxx (Task 5): cảnh báo tổ chức bị xử lý, kèm target bị phạt/lý do/tổng số lần vi phạm
    private void notifyOrganizationWarning(ViolationReport report, ViolationResolutionAction action,
                                            String reason, UUID organizationId) {
        Account organizationAccount = accountRepository.findById(organizationId).orElse(null);
        if (organizationAccount == null) {
            return;
        }
        long violationCount = buildHistory(organizationId).getTotalViolationCount();
        String targetLabel = describeTarget(report.getTargetType());

        notificationService.notify(organizationAccount, NotificationType.VIOLATION_WARNING,
                "Cảnh báo vi phạm",
                targetLabel + " của bạn đã bị xử lý (" + action.name() + ") vì: " + reason
                        + ". Tổng số lần vi phạm: " + violationCount + ".",
                toNotificationTargetType(report.getTargetType()), report.getTargetId(), null);
    }

    // Task 5: báo cho MỖI người đã báo cáo (trong các báo cáo vừa đóng) đúng 1 lần
    private void notifyReporters(List<ViolationReport> closedReports) {
        Set<UUID> notified = new HashSet<>();
        for (ViolationReport r : closedReports) {
            Account reporter = r.getReporterMember();
            if (notified.add(reporter.getId())) {
                notificationService.notify(reporter, NotificationType.VIOLATION_REPORT_HANDLED,
                        "Báo cáo của bạn đã được xử lý",
                        "Báo cáo của bạn đã được xử lý. Cảm ơn bạn.",
                        null, null, null);
            }
        }
    }

    private static String describeTarget(ViolationTargetType type) {
        return switch (type) {
            case POST -> "Bài viết";
            case CAMPAIGN -> "Chiến dịch";
            case ORGANIZATION_ACCOUNT -> "Hồ sơ tổ chức";
        };
    }

    private static NotificationTargetType toNotificationTargetType(ViolationTargetType type) {
        return switch (type) {
            case POST -> NotificationTargetType.POST;
            case CAMPAIGN -> NotificationTargetType.CAMPAIGN;
            case ORGANIZATION_ACCOUNT -> NotificationTargetType.ORGANIZATION;
        };
    }
}
