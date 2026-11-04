package com.rehome.rehome.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * F-161: điểm ghi audit log dùng chung cho các API phase 7.
 * Gọi trong cùng transaction với thao tác nghiệp vụ nên rollback thì log cũng mất theo.
 */
@Component
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditLogRepository auditLogRepository;

    public void log(Account actor, String actionType, String targetType, String targetId, JsonNode detail) {
        auditLogRepository.save(AuditLog.builder()
                .actorAccount(actor)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .detail(detail)
                .build());
    }
}
