package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.DecideEndOfTermRequest;
import com.rehome.rehome.dto.response.DecideEndOfTermResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface EndOfTermDecisionService {
    DecideEndOfTermResponse decide(Account account, UUID decisionId, DecideEndOfTermRequest request);
}
