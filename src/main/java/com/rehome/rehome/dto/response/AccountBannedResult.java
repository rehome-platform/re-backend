package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Nội dung nằm trong "result" của lỗi 403 ACCOUNT_BANNED.
 * reason = null khi tài khoản bị khoá nhưng chưa ghi lý do.
 */
@Getter
@AllArgsConstructor
public class AccountBannedResult {
    private final String reason;
}
