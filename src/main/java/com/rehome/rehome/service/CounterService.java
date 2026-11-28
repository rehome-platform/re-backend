package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CounterHandoverRequest;
import com.rehome.rehome.dto.request.CounterLookupRequest;
import com.rehome.rehome.dto.response.MemberLookupItemResponse;
import com.rehome.rehome.dto.request.CounterLookupRequest;
import com.rehome.rehome.dto.response.MemberLookupItemResponse;
import com.rehome.rehome.dto.response.MemberTasksResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.List;
import java.util.UUID;

public interface CounterService {
    void confirmIdentity(Account moderator, UUID accountId);

    void handover(Account moderator, CounterHandoverRequest request);

    List<MemberLookupItemResponse> lookup(Account moderator, CounterLookupRequest request);

    MemberTasksResponse getTasks(UUID accountId);
}
