package com.rehome.rehome.serviceImpl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.dto.request.CreateViolationReportRequest;
import com.rehome.rehome.dto.request.DismissViolationReportRequest;
import com.rehome.rehome.dto.request.ResolveViolationReportRequest;
import com.rehome.rehome.dto.response.ViolationReportDetailResponse;
import com.rehome.rehome.dto.response.ViolationReportGroupResponse;
import com.rehome.rehome.dto.response.ViolationReportResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.OrganizationProfile;
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
import com.rehome.rehome.entity.marketplace.ViolationReport;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CampaignRepository;
import com.rehome.rehome.repository.CommunityPostPhotoRepository;
import com.rehome.rehome.repository.CommunityPostRepository;
import com.rehome.rehome.repository.OrganizationProfileRepository;
import com.rehome.rehome.repository.ViolationReportRepository;
import com.rehome.rehome.service.NotificationService;
import com.rehome.rehome.service.QueueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViolationReportServiceImplTest {

    private static final String MISSING_FIELDS_MESSAGE = "targetType, targetId và reason là bắt buộc.";

    @Mock private ViolationReportRepository violationReportRepository;
    @Mock private CommunityPostRepository communityPostRepository;
    @Mock private CommunityPostPhotoRepository communityPostPhotoRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private OrganizationProfileRepository organizationProfileRepository;
    @Mock private QueueService queueService;
    @Mock private NotificationService notificationService;
    @Mock private AuditLogger auditLogger;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private ViolationReportServiceImpl service;

    private void stubSaveAssignsId() {
        when(violationReportRepository.save(any())).thenAnswer(invocation -> {
            ViolationReport saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void create_targetTypePost_success() {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Account reporter = Account.builder().id(reporterId).build();
        CreateViolationReportRequest request =
                new CreateViolationReportRequest("POST", targetId.toString(), "Nội dung sai sự thật");

        when(communityPostRepository.existsById(targetId)).thenReturn(true);
        when(accountRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
        stubSaveAssignsId();

        ViolationReportResponse res = service.create(reporterId, request);

        assertThat(res.getId()).isNotNull();
        assertThat(res.getStatus()).isEqualTo("PENDING");

        ArgumentCaptor<ViolationReport> captor = ArgumentCaptor.forClass(ViolationReport.class);
        verify(violationReportRepository).save(captor.capture());
        assertThat(captor.getValue().getReporterMember()).isSameAs(reporter);
        assertThat(captor.getValue().getTargetId()).isEqualTo(targetId);
        assertThat(captor.getValue().getReason()).isEqualTo("Nội dung sai sự thật");

        verify(queueService).enqueue(eq(QueueType.VIOLATION_REPORT), anyString(), isNull());
        verifyNoInteractions(campaignRepository);
    }

    @Test
    void create_targetTypeCampaign_checksCampaignRepository() {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Account reporter = Account.builder().id(reporterId).build();
        CreateViolationReportRequest request =
                new CreateViolationReportRequest("CAMPAIGN", targetId.toString(), "Chiến dịch giả mạo");

        when(campaignRepository.existsById(targetId)).thenReturn(true);
        when(accountRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
        stubSaveAssignsId();

        service.create(reporterId, request);

        verify(campaignRepository).existsById(targetId);
        verifyNoInteractions(communityPostRepository);
    }

    @Test
    void create_targetTypeOrganizationAccount_checksAccountRepository() {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Account reporter = Account.builder().id(reporterId).build();
        CreateViolationReportRequest request =
                new CreateViolationReportRequest("ORGANIZATION_ACCOUNT", targetId.toString(), "Tổ chức lừa đảo");

        when(accountRepository.existsById(targetId)).thenReturn(true);
        when(accountRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
        stubSaveAssignsId();

        service.create(reporterId, request);

        verify(accountRepository).existsById(targetId);
        verifyNoInteractions(communityPostRepository, campaignRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"targetType", "targetId", "reason"})
    void create_missingRequiredField_throwsBadRequest(String blankField) {
        UUID reporterId = UUID.randomUUID();
        String targetType = blankField.equals("targetType") ? " " : "POST";
        String targetId = blankField.equals("targetId") ? " " : UUID.randomUUID().toString();
        String reason = blankField.equals("reason") ? " " : "Nội dung sai sự thật";

        assertThatThrownBy(() -> service.create(reporterId,
                new CreateViolationReportRequest(targetType, targetId, reason)))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(MISSING_FIELDS_MESSAGE);

        verifyNoInteractions(violationReportRepository, communityPostRepository, campaignRepository,
                accountRepository, queueService);
    }

    @Test
    void create_invalidTargetType_throwsBadRequest() {
        UUID reporterId = UUID.randomUUID();
        CreateViolationReportRequest request =
                new CreateViolationReportRequest("NOT_A_TYPE", UUID.randomUUID().toString(), "lý do");

        assertThatThrownBy(() -> service.create(reporterId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(MISSING_FIELDS_MESSAGE);

        verifyNoInteractions(violationReportRepository, communityPostRepository, campaignRepository, accountRepository);
    }

    @Test
    void create_invalidTargetIdFormat_throwsBadRequest() {
        UUID reporterId = UUID.randomUUID();
        CreateViolationReportRequest request =
                new CreateViolationReportRequest("POST", "not-a-uuid", "lý do");

        assertThatThrownBy(() -> service.create(reporterId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage(MISSING_FIELDS_MESSAGE);

        verifyNoInteractions(violationReportRepository, communityPostRepository, campaignRepository, accountRepository);
    }

    @Test
    void create_targetNotFound_throws() {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        CreateViolationReportRequest request =
                new CreateViolationReportRequest("POST", targetId.toString(), "Nội dung sai sự thật");

        when(communityPostRepository.existsById(targetId)).thenReturn(false);

        assertThatThrownBy(() -> service.create(reporterId, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Không tìm thấy đối tượng để báo cáo.");

        verifyNoInteractions(violationReportRepository, accountRepository, queueService);
    }

    @Test
    void create_reporterAccountNotFound_throwsUnauthorized() {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        CreateViolationReportRequest request =
                new CreateViolationReportRequest("POST", targetId.toString(), "Nội dung sai sự thật");

        when(communityPostRepository.existsById(targetId)).thenReturn(true);
        when(accountRepository.findById(reporterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(reporterId, request))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));

        verify(violationReportRepository, never()).save(any());
    }

    @Test
    void create_duplicatePendingReport_throwsConflict() {
        UUID reporterId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        CreateViolationReportRequest request =
                new CreateViolationReportRequest("POST", targetId.toString(), "Nội dung sai sự thật");

        when(communityPostRepository.existsById(targetId)).thenReturn(true);
        when(accountRepository.findById(reporterId)).thenReturn(Optional.of(Account.builder().id(reporterId).build()));
        when(violationReportRepository.existsByReporterMember_IdAndTargetTypeAndTargetIdAndStatus(
                reporterId, ViolationTargetType.POST, targetId, ViolationReportStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.create(reporterId, request))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.DUPLICATE_PENDING_REPORT));

        verify(violationReportRepository, never()).save(any());
        verifyNoInteractions(queueService);
    }

    // ------------------------------------------------------------------ list (GET /violation-reports)

    private ViolationReport report(UUID id, ViolationTargetType type, UUID targetId,
                                    ViolationReportStatus status, String reason, Instant createdAt) {
        return ViolationReport.builder()
                .id(id)
                .reporterMember(Account.builder().id(UUID.randomUUID()).build())
                .targetType(type)
                .targetId(targetId)
                .status(status)
                .reason(reason)
                .createdAt(createdAt)
                .build();
    }

    @Test
    void list_groupsByTargetAndSortsByReportCountDescThenOldestAsc() {
        UUID popularTarget = UUID.randomUUID();
        UUID rareOldTarget = UUID.randomUUID();
        UUID rareNewTarget = UUID.randomUUID();
        Instant t0 = Instant.parse("2026-11-01T00:00:00Z");
        Instant t1 = Instant.parse("2026-11-02T00:00:00Z");
        Instant t2 = Instant.parse("2026-11-03T00:00:00Z");
        Instant t3 = Instant.parse("2026-11-04T00:00:00Z");

        // rareOldTarget (1 báo cáo, cũ hơn) phải đứng trước rareNewTarget (1 báo cáo, mới hơn);
        // popularTarget (2 báo cáo) phải đứng đầu dù được báo muộn hơn cả hai
        List<ViolationReport> all = List.of(
                report(UUID.randomUUID(), ViolationTargetType.POST, rareOldTarget,
                        ViolationReportStatus.PENDING, "lý do cũ", t0),
                report(UUID.randomUUID(), ViolationTargetType.POST, rareNewTarget,
                        ViolationReportStatus.PENDING, "lý do mới", t1),
                report(UUID.randomUUID(), ViolationTargetType.CAMPAIGN, popularTarget,
                        ViolationReportStatus.PENDING, "lần 1", t2),
                report(UUID.randomUUID(), ViolationTargetType.CAMPAIGN, popularTarget,
                        ViolationReportStatus.PENDING, "lần 2", t3));
        when(violationReportRepository.findByStatusOrderByCreatedAtAsc(ViolationReportStatus.PENDING))
                .thenReturn(all);

        List<ViolationReportGroupResponse> result = service.list("PENDING", null);

        assertThat(result).hasSize(3);
        assertThat(result.get(0).getTargetId()).isEqualTo(popularTarget.toString());
        assertThat(result.get(0).getReportCount()).isEqualTo(2);
        assertThat(result.get(1).getTargetId()).isEqualTo(rareOldTarget.toString());
        assertThat(result.get(2).getTargetId()).isEqualTo(rareNewTarget.toString());
    }

    @Test
    void list_invalidStatus_throwsBadRequest() {
        assertThatThrownBy(() -> service.list("NOT_A_STATUS", null))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(violationReportRepository);
    }

    // ------------------------------------------------------------------ getDetail (GET /violation-reports/{id})

    @Test
    void getDetail_postTarget_includesSnapshotSiblingsAndOrganizationHistory() {
        UUID reportId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID siblingReportId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-01T00:00:00Z");

        ViolationReport thisReport = report(reportId, ViolationTargetType.POST, postId,
                ViolationReportStatus.PENDING, "Nội dung sai sự thật", createdAt);
        ViolationReport sibling = report(siblingReportId, ViolationTargetType.POST, postId,
                ViolationReportStatus.PENDING, "Spam", createdAt.plusSeconds(60));
        when(violationReportRepository.findById(reportId)).thenReturn(Optional.of(thisReport));
        when(violationReportRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                ViolationTargetType.POST, postId)).thenReturn(List.of(thisReport, sibling));

        OrganizationProfile org = OrganizationProfile.builder()
                .accountId(orgId).displayName("Hội Chữ Thập Đỏ").build();
        CommunityPost post = CommunityPost.builder()
                .id(postId).organization(org).content("Nội dung bài viết")
                .status(ContentStatus.VISIBLE).build();
        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(post));
        when(communityPostPhotoRepository.findByPost_Id(postId)).thenReturn(List.of());
        when(violationReportRepository.findResolvedByOrganizationId(orgId)).thenReturn(List.of());

        ViolationReportDetailResponse res = service.getDetail(reportId);

        assertThat(res.getId()).isEqualTo(reportId.toString());
        assertThat(res.getTargetSnapshot().getPostContent()).isEqualTo("Nội dung bài viết");
        assertThat(res.getTargetSnapshot().getOrganizationDisplayName()).isEqualTo("Hội Chữ Thập Đỏ");
        assertThat(res.getOtherReports()).hasSize(1);
        assertThat(res.getOtherReports().get(0).getId()).isEqualTo(siblingReportId.toString());
        assertThat(res.getOrganizationViolationHistory().getTotalViolationCount()).isEqualTo(0);
    }

    @Test
    void getDetail_notFound_throws() {
        UUID reportId = UUID.randomUUID();
        when(violationReportRepository.findById(reportId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(reportId))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VIOLATION_REPORT_NOT_FOUND));
    }

    // ------------------------------------------------------------------ resolve

    private void stubObjectMapper() {
        when(objectMapper.createObjectNode()).thenAnswer(inv -> new ObjectMapper().createObjectNode());
    }

    private ResolveViolationReportRequest resolveRequest(String action, String reason) {
        return new ResolveViolationReportRequest(action, reason);
    }

    @Test
    void resolve_removePost_setsRemovedClosesAllPendingAndNotifies() {
        stubObjectMapper();
        UUID moderatorId = UUID.randomUUID();
        Account moderator = Account.builder().id(moderatorId).build();
        UUID reportId = UUID.randomUUID();
        UUID otherReportId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-01T00:00:00Z");

        ViolationReport primary = report(reportId, ViolationTargetType.POST, postId,
                ViolationReportStatus.PENDING, "Nội dung sai sự thật", createdAt);
        ViolationReport other = report(otherReportId, ViolationTargetType.POST, postId,
                ViolationReportStatus.PENDING, "Spam", createdAt.plusSeconds(60));
        when(violationReportRepository.findById(reportId)).thenReturn(Optional.of(primary));

        OrganizationProfile org = OrganizationProfile.builder().accountId(orgId).displayName("Hội ABC").build();
        Account orgAccount = Account.builder().id(orgId).build();
        CommunityPost post = CommunityPost.builder()
                .id(postId).organization(org).content("Nội dung").status(ContentStatus.VISIBLE).build();
        when(communityPostRepository.findById(postId)).thenReturn(Optional.of(post));
        when(violationReportRepository.findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtAsc(
                ViolationTargetType.POST, postId, ViolationReportStatus.PENDING))
                .thenReturn(List.of(primary, other));
        when(accountRepository.findById(orgId)).thenReturn(Optional.of(orgAccount));
        when(violationReportRepository.findResolvedByOrganizationId(orgId)).thenReturn(List.of());
        // getDetail() ở cuối resolve() cần snapshot + sibling list
        when(communityPostPhotoRepository.findByPost_Id(postId)).thenReturn(List.of());
        when(violationReportRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                ViolationTargetType.POST, postId)).thenReturn(List.of(primary, other));

        ViolationReportDetailResponse res = service.resolve(moderator, reportId,
                resolveRequest("REMOVE_POST", "Nội dung vi phạm quy định"));

        assertThat(post.getStatus()).isEqualTo(ContentStatus.REMOVED);
        verify(communityPostRepository).save(post);

        assertThat(primary.getStatus()).isEqualTo(ViolationReportStatus.RESOLVED);
        assertThat(other.getStatus()).isEqualTo(ViolationReportStatus.RESOLVED);
        assertThat(primary.getResolutionAction()).isEqualTo(ViolationResolutionAction.REMOVE_POST);
        assertThat(primary.getResolutionReason()).isEqualTo("Nội dung vi phạm quy định");
        assertThat(other.getResolutionReason()).isEqualTo("Nội dung vi phạm quy định");
        assertThat(primary.getResolvedBy()).isSameAs(moderator);

        verify(queueService).dequeue(QueueType.VIOLATION_REPORT, reportId.toString());
        verify(queueService).dequeue(QueueType.VIOLATION_REPORT, otherReportId.toString());

        verify(auditLogger).log(eq(moderator), eq("VIOLATION_RESOLVE_REMOVE_POST"),
                eq("POST"), eq(postId.toString()), any());

        // Cảnh báo tổ chức + báo 2 người đã báo cáo (primary + other), mỗi người 1 lần
        verify(notificationService).notify(eq(orgAccount), eq(NotificationType.VIOLATION_WARNING),
                any(), any(), eq(NotificationTargetType.POST), eq(postId), isNull());
        verify(notificationService, times(2)).notify(any(), eq(NotificationType.VIOLATION_REPORT_HANDLED),
                any(), any(), isNull(), isNull(), isNull());

        assertThat(res.getStatus()).isEqualTo("RESOLVED");
    }

    @Test
    void resolve_hideCampaign_wrongTargetType_throwsBadRequest() {
        UUID moderatorId = UUID.randomUUID();
        Account moderator = Account.builder().id(moderatorId).build();
        UUID reportId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        ViolationReport report = report(reportId, ViolationTargetType.POST, postId,
                ViolationReportStatus.PENDING, "lý do", Instant.now());
        when(violationReportRepository.findById(reportId)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.resolve(moderator, reportId,
                resolveRequest("HIDE_CAMPAIGN", "lý do xử lý")))
                .isInstanceOf(BadRequestException.class);

        verify(violationReportRepository, never()).saveAll(any());
    }

    @Test
    void resolve_alreadyHandled_throwsConflictErrorCode() {
        UUID moderatorId = UUID.randomUUID();
        Account moderator = Account.builder().id(moderatorId).build();
        UUID reportId = UUID.randomUUID();
        ViolationReport report = report(reportId, ViolationTargetType.ORGANIZATION_ACCOUNT, UUID.randomUUID(),
                ViolationReportStatus.RESOLVED, "lý do", Instant.now());
        when(violationReportRepository.findById(reportId)).thenReturn(Optional.of(report));

        assertThatThrownBy(() -> service.resolve(moderator, reportId,
                resolveRequest("WARN_ORG", "lý do xử lý")))
                .isInstanceOf(AppException.class)
                .satisfies(e -> assertThat(((AppException) e).getErrorCode())
                        .isEqualTo(ErrorCode.VIOLATION_REPORT_ALREADY_HANDLED));
    }

    @Test
    void resolve_hideCampaign_setsHidden() {
        stubObjectMapper();
        UUID moderatorId = UUID.randomUUID();
        Account moderator = Account.builder().id(moderatorId).build();
        UUID reportId = UUID.randomUUID();
        UUID campaignId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        ViolationReport primary = report(reportId, ViolationTargetType.CAMPAIGN, campaignId,
                ViolationReportStatus.PENDING, "Lừa đảo quyên góp", createdAt);
        when(violationReportRepository.findById(reportId)).thenReturn(Optional.of(primary));

        OrganizationProfile org = OrganizationProfile.builder().accountId(orgId).displayName("Hội ABC").build();
        Campaign campaign = Campaign.builder().id(campaignId).organization(org)
                .status(CampaignStatus.OPEN).build();
        when(campaignRepository.findById(campaignId)).thenReturn(Optional.of(campaign));
        when(violationReportRepository.findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtAsc(
                ViolationTargetType.CAMPAIGN, campaignId, ViolationReportStatus.PENDING))
                .thenReturn(List.of(primary));
        when(accountRepository.findById(orgId)).thenReturn(Optional.of(Account.builder().id(orgId).build()));
        when(violationReportRepository.findResolvedByOrganizationId(orgId)).thenReturn(List.of());
        when(violationReportRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                ViolationTargetType.CAMPAIGN, campaignId)).thenReturn(List.of(primary));

        service.resolve(moderator, reportId, resolveRequest("HIDE_CAMPAIGN", "Lừa đảo quyên góp đã xác minh"));

        assertThat(campaign.getStatus()).isEqualTo(CampaignStatus.HIDDEN);
        verify(campaignRepository).save(campaign);
    }

    // ------------------------------------------------------------------ dismiss

    @Test
    void dismiss_closesAllPendingAsDismissedAndNotifiesReportersOnly() {
        stubObjectMapper();
        UUID moderatorId = UUID.randomUUID();
        Account moderator = Account.builder().id(moderatorId).build();
        UUID reportId = UUID.randomUUID();
        UUID otherReportId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Instant createdAt = Instant.now();

        ViolationReport primary = report(reportId, ViolationTargetType.ORGANIZATION_ACCOUNT, targetId,
                ViolationReportStatus.PENDING, "Nghi ngờ lừa đảo", createdAt);
        ViolationReport other = report(otherReportId, ViolationTargetType.ORGANIZATION_ACCOUNT, targetId,
                ViolationReportStatus.PENDING, "Không rõ", createdAt.plusSeconds(10));
        when(violationReportRepository.findById(reportId)).thenReturn(Optional.of(primary));
        when(violationReportRepository.findByTargetTypeAndTargetIdAndStatusOrderByCreatedAtAsc(
                ViolationTargetType.ORGANIZATION_ACCOUNT, targetId, ViolationReportStatus.PENDING))
                .thenReturn(List.of(primary, other));
        when(organizationProfileRepository.findById(targetId)).thenReturn(Optional.empty());
        when(violationReportRepository.findByTargetTypeAndTargetIdOrderByCreatedAtAsc(
                ViolationTargetType.ORGANIZATION_ACCOUNT, targetId)).thenReturn(List.of(primary, other));

        ViolationReportDetailResponse res = service.dismiss(moderator, reportId,
                new DismissViolationReportRequest("Không đủ căn cứ"));

        assertThat(primary.getStatus()).isEqualTo(ViolationReportStatus.DISMISSED);
        assertThat(other.getStatus()).isEqualTo(ViolationReportStatus.DISMISSED);
        assertThat(primary.getResolutionReason()).isEqualTo("Không đủ căn cứ");

        verify(queueService).dequeue(QueueType.VIOLATION_REPORT, reportId.toString());
        verify(queueService).dequeue(QueueType.VIOLATION_REPORT, otherReportId.toString());
        verify(auditLogger).log(eq(moderator), eq("VIOLATION_DISMISS"),
                eq("ORGANIZATION_ACCOUNT"), eq(targetId.toString()), any());

        // Dismiss không cảnh báo tổ chức - chỉ báo cho người đã báo cáo
        verify(notificationService, never()).notify(any(), eq(NotificationType.VIOLATION_WARNING),
                any(), any(), any(), any(), any());
        verify(notificationService, times(2)).notify(any(), eq(NotificationType.VIOLATION_REPORT_HANDLED),
                any(), any(), isNull(), isNull(), isNull());

        assertThat(res.getStatus()).isEqualTo("DISMISSED");
    }
}
