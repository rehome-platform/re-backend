package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AccountStatusResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface AccountAdminService {

    /** Khoá tài khoản kèm lý do, thu hồi toàn bộ refresh token. */
    AccountStatusResponse ban(Account admin, UUID targetId, String reason);

    /** Mở khoá tài khoản, xoá lý do khoá. */
    AccountStatusResponse unban(Account admin, UUID targetId);
}
