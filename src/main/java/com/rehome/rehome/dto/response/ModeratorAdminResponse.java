package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** API 138, 139, 140 - thông tin Moderator dưới góc nhìn Admin. */
@Getter
@Builder
@AllArgsConstructor
public class ModeratorAdminResponse {
    private final UUID accountId;
    private final String email;
    private final String phone;
    private final String fullName;
    private final String moderatorCode;
    private final UUID warehouseId;
    private final String warehouseName;
    private final AccountStatus status;
    private final Instant createdAt;
}
