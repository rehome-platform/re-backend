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

/** API 149 - F-160. */
@Getter
@Builder
@AllArgsConstructor
public class AuditLogResponse {
    private final UUID id;
    private final UUID actorAccountId;
    // Họ tên (Member/Moderator) hoặc tên tổ chức; null với Admin (không có profile) hoặc khi không tìm thấy actor
    private final String actorName;
    private final String actorEmail;
    private final Role actorRole;
    private final String actionType;
    private final String targetType;
    private final String targetId;
    // Log cũ có thể không có beforeStatus/afterStatus: trả nguyên metadata đã lưu, không bù dữ liệu
    private final JsonNode detail;
    private final Instant createdAt;

    /** Không có thông tin actor (actorName/actorEmail/actorRole = null). */
    public static AuditLogResponse from(AuditLog a) {
        return from(a, null, null);
    }

    /** actor và actorName được nạp theo lô bởi Service; null nếu không tìm thấy. */
    public static AuditLogResponse from(AuditLog a, Account actor, String actorName) {
        return AuditLogResponse.builder()
                .id(a.getId())
                // getId() trên proxy lazy không chạm DB
                .actorAccountId(a.getActorAccount().getId())
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
