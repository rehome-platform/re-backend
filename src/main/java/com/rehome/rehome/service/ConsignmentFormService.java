package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.ConsignmentFormRequest;
import com.rehome.rehome.dto.response.ConsignmentFormResponse;
import com.rehome.rehome.entity.auth.Account;

public interface ConsignmentFormService {
    ConsignmentFormResponse create(ConsignmentFormRequest request, Account account);
}
