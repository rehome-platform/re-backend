package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/** API 135 - một dòng trong danh sách tài khoản của Admin. */
@Getter
@Builder
@AllArgsConstructor
public class AdminAccountResponse {
    private final UUID accountId;
    private final String email;
    private final String phone;
    private final Role role;
    private final AccountStatus status;
    // Member/Moderator: họ tên; ORGANIZATION: tên tổ chức; ADMIN: null
    private final String fullName;
    // memberCode hoặc moderatorCode; null với ORGANIZATION/ADMIN
    private final String code;
    // null khi không bị khoá
    private final String banReason;
    private final Instant createdAt;
}
