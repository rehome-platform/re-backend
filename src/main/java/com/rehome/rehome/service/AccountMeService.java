package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AccountMeResponse;

import java.util.UUID;

public interface AccountMeService {
    AccountMeResponse getMe(UUID accountId);

}
