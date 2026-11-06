package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AccountStatusResponse;
import com.rehome.rehome.dto.response.AdminAccountResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.Role;

import java.util.UUID;

public interface AccountAdminService {

    /**
     * API 135: tìm và xem tài khoản theo email / họ tên, lọc theo role và status.
     * sortBy hỗ trợ createdAt (mặc định) và status; sortDir là asc hoặc desc (mặc định). Giá trị khác
     * được đưa về mặc định thay vì báo lỗi.
     */
    PageResponse<AdminAccountResponse> search(String search, Role role, AccountStatus status,
                                              String sortBy, String sortDir, int page, int size);

    /** Khoá tài khoản kèm lý do, thu hồi toàn bộ refresh token. */
    AccountStatusResponse ban(Account admin, UUID targetId, String reason);

    /** Mở khoá tài khoản, xoá lý do khoá. */
    AccountStatusResponse unban(Account admin, UUID targetId);
}
