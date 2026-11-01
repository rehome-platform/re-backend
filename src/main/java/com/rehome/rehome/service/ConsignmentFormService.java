package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.request.RejectConsignmentFormRequest;
import com.rehome.rehome.dto.request.SubmitConsignmentFormsRequest;
import com.rehome.rehome.dto.request.ReturnOnsiteRequest;
import com.rehome.rehome.dto.response.*;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;

import java.util.UUID;

public interface ConsignmentFormService {
    ConsignmentFormResponse create(ConsignmentFormRequest request, Account account);

    ConsignmentFormPageResponse getMyForms(
            Account account, ConsignmentFormStatus status, int page, int size);

    ConsignmentFormDetailResponse getMyForm(Account account, UUID id);

    ConsignmentFormResponse approve(UUID id, Account moderator);

    void startInspection(UUID id, Account moderator);

    ConsignmentFormStatusResponse reject(UUID id, RejectConsignmentFormRequest request, Account moderator);
    ConsignmentFormDetailResponse update(UUID id, ConsignmentFormRequest request, Account account);
    ConsignmentFormStatusResponse returnOnsite(UUID id, ReturnOnsiteRequest request, Account moderator);

    // Nháp ký gửi ("Tủ đồ")
    ConsignmentFormDetailResponse createDraft(ConsignmentFormRequest request, Account account);
    ConsignmentFormDetailResponse updateDraft(UUID id, ConsignmentFormRequest request, Account account);
    void deleteDraft(UUID id, Account account);
    SubmitConsignmentFormsResponse submitDrafts(SubmitConsignmentFormsRequest request, Account account);
}
