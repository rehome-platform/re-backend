package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.rehome.rehome.entity.audit.AuditLog;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AuditLogResponse {
    private final UUID id;
    private final UUID actorAccountId;
    private final String actorName;
    private final String actorEmail;
    private final Role actorRole;
    private final String actionType;
    private final String targetType;
    private final String targetId;
    private final JsonNode detail;
    private final Instant createdAt;

    public static AuditLogResponse from(AuditLog a) {
        return from(a, null, null);
    }

    public static AuditLogResponse from(AuditLog a, Account actor, String actorName) {
        return AuditLogResponse.builder()
                .id(a.getId())
                .actorAccountId(a.getActorAccount() == null ? null : a.getActorAccount().getId())
                .actorName(actorName)
                .actorEmail(actor != null ? actor.getEmail() : null)
                .actorRole(actor != null ? actor.getRole() : null)
                .actionType(a.getActionType())
                .targetType(a.getTargetType())
                .targetId(a.getTargetId())
                .detail(a.getDetail())
                .createdAt(a.getCreatedAt())
                .build();
    }
}
