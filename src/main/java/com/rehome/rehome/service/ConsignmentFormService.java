package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.request.RejectConsignmentFormRequest;
import com.rehome.rehome.dto.request.ReturnOnsiteRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;

import java.util.UUID;

public interface ConsignmentFormService {
    ConsignmentFormResponse create(ConsignmentFormRequest request, Account account);

    PageResponse<ConsignmentFormListItemResponse> getMyForms(
            Account account, ConsignmentFormStatus status, int page, int size);

    ConsignmentFormDetailResponse getMyForm(Account account, UUID id);

    ConsignmentFormResponse approve(UUID id);

    void startInspection(UUID id);

    ConsignmentFormStatusResponse reject(UUID id, RejectConsignmentFormRequest request);

    ConsignmentFormStatusResponse returnOnsite(UUID id, ReturnOnsiteRequest request);
}
