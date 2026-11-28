package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AdminAccountResponse {
    private final UUID accountId;
    private final String email;
    private final String phone;
    private final Role role;
    private final AccountStatus status;
    private final String fullName;
    private final String code;
    private final String banReason;
    private final Instant createdAt;
}
