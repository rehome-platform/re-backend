package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.DecideDegradedItemCaseRequest;
import com.rehome.rehome.dto.request.OpenDegradedItemCaseRequest;
import com.rehome.rehome.dto.response.DegradedItemCaseDetailResponse;
import com.rehome.rehome.dto.response.DegradedItemCaseResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.List;
import java.util.UUID;

public interface DegradedItemCaseService {

    DegradedItemCaseDetailResponse open(Account moderator, OpenDegradedItemCaseRequest request);

    DegradedItemCaseResponse decide(Account account, UUID id, DecideDegradedItemCaseRequest request);

    DegradedItemCaseDetailResponse getMine(Account account, UUID id);

    List<DegradedItemCaseDetailResponse> listMine(Account account, String status);

    List<DegradedItemCaseDetailResponse> listByWarehouse(Account moderator, String status);
}
