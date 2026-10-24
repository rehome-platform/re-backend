package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CompleteInspectionRequest;
import com.rehome.rehome.dto.response.CompleteInspectionResponse;
import com.rehome.rehome.entity.auth.Account;
import java.util.UUID;

public interface ConsignmentInspectionService {

    // API 34: hoàn tất kiểm định đạt, tạo ConsignmentReceipt chưa xác nhận
    CompleteInspectionResponse completeInspection(UUID formId, CompleteInspectionRequest request, Account moderator);
}