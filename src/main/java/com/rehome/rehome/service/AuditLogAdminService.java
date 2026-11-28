package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.AuditLogFilterOptionsResponse;
import com.rehome.rehome.dto.response.AuditLogResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.enums.Role;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditLogAdminService {

    PageResponse<AuditLogResponse> search(UUID actorAccountId, Role actorRole, String search, String actionType,
                                          List<String> actionTypes, String targetType, String targetId,
                                          Instant from, Instant to, int page, int size);

    AuditLogFilterOptionsResponse filterOptions();
}
