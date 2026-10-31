package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.DecideDegradedItemCaseRequest;
import com.rehome.rehome.dto.response.DegradedItemCaseResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface DegradedItemCaseService {

    // API 95 (F-036) - Member quyết định nhận đền đủ hay nhận lại hàng kèm đền một phần
    DegradedItemCaseResponse decide(Account account, UUID id, DecideDegradedItemCaseRequest request);
}
