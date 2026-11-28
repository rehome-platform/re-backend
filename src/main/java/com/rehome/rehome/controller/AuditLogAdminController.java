package com.rehome.rehome.controller;

import com.rehome.rehome.dto.response.ApiResponse;
import com.rehome.rehome.dto.response.AuditLogFilterOptionsResponse;
import com.rehome.rehome.dto.response.AuditLogResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.service.AuditLogAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin - Audit log")
public class AuditLogAdminController {

    private final AuditLogAdminService auditLogAdminService;

    @GetMapping
    @Operation(summary = "API 149 - Tra cứu audit log (F-160), tìm theo từ khoá, lọc theo actor / actionType(s) / targetType / targetId / khoảng thời gian [from, to)")
    public ResponseEntity<ApiResponse<PageResponse<AuditLogResponse>>> search(
            @RequestParam(required = false) UUID actorAccountId,
            @RequestParam(required = false) Role actorRole,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) List<String> actionTypes,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                auditLogAdminService.search(actorAccountId, actorRole, search, actionType, actionTypes, targetType,
                        targetId, from, to, page, size), "OK"));
    }

    @GetMapping("/filter-options")
    @Operation(summary = "Danh sách actor và loại thao tác thực sự có trong audit log (cho ô lọc)")
    public ResponseEntity<ApiResponse<AuditLogFilterOptionsResponse>> filterOptions() {
        return ResponseEntity.ok(ApiResponse.success(auditLogAdminService.filterOptions(), "OK"));
    }
}
