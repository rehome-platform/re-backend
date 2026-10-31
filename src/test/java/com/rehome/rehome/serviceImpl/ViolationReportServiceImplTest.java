package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateViolationReportRequest;
import com.rehome.rehome.dto.response.ViolationReportResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.marketplace.ViolationReport;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CampaignRepository;
import com.rehome.rehome.repository.CommunityPostRepository;
import com.rehome.rehome.repository.ViolationReportRepository;
import com.rehome.rehome.service.QueueService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViolationReportServiceImplTest {

    private static final String MISSING_FIELDS_MESSAGE = "targetType, targetId và reason là bắt buộc.";

    @Mock private ViolationReportRepository violationReportRepository;
    @Mock private CommunityPostRepository communityPostRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private QueueService queueService;

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
}
