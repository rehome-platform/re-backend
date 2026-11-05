package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AuditLogFilterOptionsResponse;
import com.rehome.rehome.dto.response.AuditLogResponse;
import com.rehome.rehome.dto.response.PageResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogAdminService {

    /**
     * API 149 - F-160: tra cứu audit log, mới nhất trước. Mọi bộ lọc đều không bắt buộc và kết hợp được với nhau.
     * actionType (một giá trị, giữ cho client cũ) và actionTypes (nhiều giá trị) được gộp lại.
     * Khoảng thời gian là [from, to); 400 INVALID_DATE_RANGE nếu from sau to.
     */
    PageResponse<AuditLogResponse> search(UUID actorAccountId, String actionType, List<String> actionTypes,
                                          String targetType, String targetId, Instant from, Instant to,
                                          int page, int size);

    /** Actor và loại thao tác thực sự có trong audit log, dùng cho các ô lọc. */
    AuditLogFilterOptionsResponse filterOptions();
}
