package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CounterHandoverRequest;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface CounterService {
    void confirmIdentity(Account moderator, UUID accountId);

    void handover(Account moderator, CounterHandoverRequest request);
}
