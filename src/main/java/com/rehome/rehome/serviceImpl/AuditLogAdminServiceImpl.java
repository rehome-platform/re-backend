package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.dto.response.AuditLogResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.AuditLogRepository;
import com.rehome.rehome.repository.spec.AuditLogSpecifications;
import com.rehome.rehome.service.AuditLogAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogAdminServiceImpl implements AuditLogAdminService {

    static final int MAX_PAGE_SIZE = 100;

    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AuditLogResponse> search(UUID actorAccountId, String actionType, String targetType,
                                                 int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new AppException(ErrorCode.INVALID_PAGINATION);
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return PageResponse.of(auditLogRepository
                .findAll(AuditLogSpecifications.filter(actorAccountId, actionType, targetType), pageable)
                .map(AuditLogResponse::from));
    }
}
