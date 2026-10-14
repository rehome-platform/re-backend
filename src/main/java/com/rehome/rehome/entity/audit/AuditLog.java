package com.rehome.rehome.entity.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.rehome.rehome.entity.Account;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
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

    // Thời điểm ghi log — F-161
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
