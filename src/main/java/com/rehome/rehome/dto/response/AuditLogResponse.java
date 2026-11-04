package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.rehome.rehome.entity.audit.AuditLog;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** API 149 - F-160. */
@Getter
@Builder
@AllArgsConstructor
public class AuditLogResponse {
    private final UUID actorAccountId;
    private final String actionType;
    private final String targetType;
    private final String targetId;
    private final JsonNode detail;
    private final Instant createdAt;

    public static AuditLogResponse from(AuditLog a) {
        return AuditLogResponse.builder()
                // getId() trên proxy lazy không chạm DB
                .actorAccountId(a.getActorAccount().getId())
                .actionType(a.getActionType())
                .targetType(a.getTargetType())
                .targetId(a.getTargetId())
                .detail(a.getDetail())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
