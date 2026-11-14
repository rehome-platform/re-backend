package com.rehome.rehome.entity.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.rehome.rehome.entity.auth.Account;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_logs_warehouse_group_created", columnList = "warehouse_id, audit_group, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    // Khóa chính
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    // Người thực hiện thao tác
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_account_id", nullable = false)
    private Account actorAccount;

    // Loại thao tác, ví dụ VERIFY_ORGANIZATION, UPDATE_CONFIG
    @Column(name = "action_type", nullable = false, length = 255)
    private String actionType;

    // Loại đối tượng bị tác động, ví dụ ORDER, PRODUCT, SYSTEM_CONFIG
    @Column(name = "target_type", nullable = false, length = 255)
    private String targetType;

    // Id đối tượng; để varchar vì SKU không phải UUID
    @Column(name = "target_id", nullable = false, length = 255)
    private String targetId;

    // Dữ liệu trước và sau khi đổi, lưu JSON
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", nullable = true, columnDefinition = "jsonb")
    private JsonNode detail;

    // Kho nơi thao tác xảy ra; chỉ các log nghiệp vụ kho mới có, log cũ và log hệ thống để null
    @Column(name = "warehouse_id", nullable = true)
    private UUID warehouseId;

    // Nhóm lịch sử kho (WarehouseAuditGroup: PACKING / INSPECTION / INVENTORY); null với log không thuộc kho
    @Column(name = "audit_group", nullable = true, length = 32)
    private String auditGroup;

    // Kết quả ngắn của thao tác (APPROVED, REJECTED, SUCCESS...), hiển thị thẳng ở lịch sử kho
    @Column(name = "result", nullable = true, length = 255)
    private String result;

    // Thời điểm ghi log — F-161
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
