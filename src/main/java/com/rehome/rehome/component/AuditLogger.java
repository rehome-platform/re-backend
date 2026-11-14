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

/**
 * F-161: điểm ghi audit log dùng chung cho các API phase 7.
 * Gọi trong cùng transaction với thao tác nghiệp vụ nên rollback thì log cũng mất theo.
 */
@Component
@RequiredArgsConstructor
public class AuditLogger {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public void log(Account actor, String actionType, String targetType, String targetId, JsonNode detail) {
        auditLogRepository.save(AuditLog.builder()
                .actorAccount(actor)
                .actionType(actionType)
                .targetType(targetType)
                .targetId(targetId)
                .detail(detail)
                .build());
    }

    /**
     * Log thao tác nghiệp vụ kho, hiện ở GET /moderator/warehouse-history. Luôn lưu đủ kho, nhóm, đối tượng,
     * người làm, hành động và kết quả.
     *
     * <p>{@code Propagation.MANDATORY}: bắt buộc chạy trong transaction của nghiệp vụ, để thao tác thất bại
     * (rollback) không để lại log và không ai ghi log "chay" ngoài transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void logWarehouse(Account actor, Warehouse warehouse, WarehouseAuditGroup group, String actionType,
                             String targetType, String targetId, String result, JsonNode detail) {
        logWarehouse(actor, warehouse == null ? null : warehouse.getId(), group, actionType, targetType, targetId,
                result, detail);
    }

    /** Như logWarehouse nhưng detail chỉ gồm nhãn hiển thị (mã đơn / SKU / số form) cho màn lịch sử kho. */
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
                .result(result)
                .build());
    }
}
