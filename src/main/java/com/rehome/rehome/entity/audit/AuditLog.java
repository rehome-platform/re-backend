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
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_account_id")
    private Account actorAccount;

    @Column(name = "action_type", nullable = false, length = 255)
    private String actionType;

    @Column(name = "target_type", nullable = false, length = 255)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 255)
    private String targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "detail", nullable = true, columnDefinition = "jsonb")
    private JsonNode detail;

    @Column(name = "warehouse_id", nullable = true)
    private UUID warehouseId;

    @Column(name = "audit_group", nullable = true, length = 32)
    private String auditGroup;

    @Column(name = "activity_type", length = 32)
    private String activityType;

    @Column(name = "result", nullable = true, length = 255)
    private String result;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
    }
}
