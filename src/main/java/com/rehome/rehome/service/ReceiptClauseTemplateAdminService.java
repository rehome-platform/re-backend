package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.ReceiptClauseTemplateRequest;
import com.rehome.rehome.dto.response.ReceiptClauseTemplateResponse;
import com.rehome.rehome.entity.auth.Account;
import java.util.List;
import java.util.UUID;

public interface ReceiptClauseTemplateAdminService {

    List<ReceiptClauseTemplateResponse> list();

    ReceiptClauseTemplateResponse create(Account admin, ReceiptClauseTemplateRequest request);

    ReceiptClauseTemplateResponse update(Account admin, UUID id, ReceiptClauseTemplateRequest request);

    void delete(Account admin, UUID id);

    ReceiptClauseTemplateResponse toggle(Account admin, UUID id);
}
