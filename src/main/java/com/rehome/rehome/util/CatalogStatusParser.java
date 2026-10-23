package com.rehome.rehome.util;

import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.ErrorCode;

public final class CatalogStatusParser {

    private CatalogStatusParser() {}

    // Không truyền status thì mặc định ACTIVE. Chỉ Moderator mới xem được HIDDEN.
    public static CatalogStatus parse(String raw, Account account) {
        CatalogStatus status = CatalogStatus.ACTIVE;
        if (raw != null && !raw.isBlank()) {
            try {
                status = CatalogStatus.valueOf(raw.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.INVALID_STATUS);          // 400
            }
        }
        boolean isModerator = account != null && account.getRole() == Role.MODERATOR;
        return isModerator ? status : CatalogStatus.ACTIVE;
    }
}