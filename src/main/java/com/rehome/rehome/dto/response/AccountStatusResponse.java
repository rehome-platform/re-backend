package com.rehome.rehome.dto.response;

import com.rehome.rehome.entity.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AccountStatusResponse {
    private final String accountId;
    private final AccountStatus status;
    // null khi không bị khoá
    private final String banReason;
}
