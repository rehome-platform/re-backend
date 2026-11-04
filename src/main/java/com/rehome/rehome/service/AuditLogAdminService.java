package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AuditLogResponse;
import com.rehome.rehome.dto.response.PageResponse;

import java.util.UUID;

public interface AuditLogAdminService {

    // API 149 - F-160: tra cứu audit log, mới nhất trước
    PageResponse<AuditLogResponse> search(UUID actorAccountId, String actionType, String targetType,
                                          int page, int size);
}
