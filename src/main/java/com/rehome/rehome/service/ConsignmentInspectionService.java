package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CompleteInspectionRequest;
import com.rehome.rehome.dto.response.CompleteInspectionResponse;
import com.rehome.rehome.entity.auth.Account;
import java.util.UUID;

public interface ConsignmentInspectionService {

    CompleteInspectionResponse completeInspection(UUID formId, CompleteInspectionRequest request, Account moderator);
}