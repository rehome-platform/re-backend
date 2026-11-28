package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AccountStatusResponse;
import com.rehome.rehome.dto.response.AdminAccountResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.Role;

import java.util.UUID;

public interface AccountAdminService {

    PageResponse<AdminAccountResponse> search(String search, Role role, AccountStatus status,
                                              String from, String to,
                                              String sortBy, String sortDir, int page, int size);

    AccountStatusResponse ban(Account admin, UUID targetId, String reason);

    AccountStatusResponse unban(Account admin, UUID targetId);
}
