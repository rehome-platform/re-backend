package com.rehome.rehome.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.WarehouseAuditGroup;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.repository.AuditLogRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void logOperation(Account actor, String actionType, String targetType, Object targetId, String result) {
        log(actor, actionType, targetType, String.valueOf(targetId), objectMapper.createObjectNode().put("result", result));
    }

    public void logSystemOperation(String actionType, String targetType, Object targetId, String result, String source) {
        log(null, actionType, targetType, String.valueOf(targetId),
                objectMapper.createObjectNode().put("result", result).put("source", source));
    }

    public void logActorOrSystemOperation(String actionType, String targetType, Object targetId,
                                          String result, String source) {
        Account actor = currentActor();
        if (actor == null) {
            logSystemOperation(actionType, targetType, targetId, result, source);
        } else {
            logOperation(actor, actionType, targetType, targetId, result);
        }
    }

    public void logCurrentOperation(String actionType, String targetType, Object targetId, String result) {
        Account actor = currentActor();
        if (actor != null) {
            logOperation(actor, actionType, targetType, targetId, result);
        }
    }

    private Account currentActor() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Account actor ? actor : null;
    }

    public void log(Account actor, String actionType, String targetType, String targetId, JsonNode detail) {
        auditLogRepository.save(AuditLog.builder()
                .actorAccount(actor)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .detail(detail)
                .build());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void logWarehouse(Account actor, Warehouse warehouse, WarehouseAuditGroup group, String actionType,
                             String targetType, String targetId, String result, JsonNode detail) {
        logWarehouse(actor, warehouse == null ? null : warehouse.getId(), group, actionType, targetType, targetId,
                result, detail);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void logWarehouseOp(Account actor, Warehouse warehouse, WarehouseAuditGroup group, String actionType,
                               String targetType, String targetId, String result, String label) {
        logWarehouse(actor, warehouse, group, actionType, targetType, targetId, result,
                objectMapper.createObjectNode().put("label", label));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void logWarehouse(Account actor, UUID warehouseId, WarehouseAuditGroup group, String actionType,
                             String targetType, String targetId, String result, JsonNode detail) {
        if (warehouseId == null) {
            throw new IllegalStateException("Log kho bắt buộc có warehouseId (action " + actionType + ")");
        }
        auditLogRepository.save(AuditLog.builder()
                .actorAccount(actor)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .detail(detail)
                .warehouseId(warehouseId)
                .auditGroup(group.name())
                .activityType(group.name())
                .result(result)
                .build());
    }
}
