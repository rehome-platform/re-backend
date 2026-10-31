package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.request.CreateViolationReportRequest;
import com.rehome.rehome.dto.response.ViolationReportResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.enums.ViolationTargetType;
import com.rehome.rehome.entity.marketplace.ViolationReport;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.CampaignRepository;
import com.rehome.rehome.repository.CommunityPostRepository;
import com.rehome.rehome.repository.ViolationReportRepository;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.service.ViolationReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ViolationReportServiceImpl implements ViolationReportService {

    private static final String MISSING_FIELDS_MESSAGE = "targetType, targetId và reason là bắt buộc.";

    private final ViolationReportRepository violationReportRepository;
    private final CommunityPostRepository communityPostRepository;
    private final CampaignRepository campaignRepository;
    private final AccountRepository accountRepository;
    private final QueueService queueService;

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
}
