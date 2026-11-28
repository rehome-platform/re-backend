package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateViolationReportRequest;
import com.rehome.rehome.dto.request.DismissViolationReportRequest;
import com.rehome.rehome.dto.request.ResolveViolationReportRequest;
import com.rehome.rehome.dto.response.ViolationReportDetailResponse;
import com.rehome.rehome.dto.response.ViolationReportGroupResponse;
import com.rehome.rehome.dto.response.ViolationReportResponse;
import com.rehome.rehome.entity.auth.Account;

import java.util.List;
import java.util.UUID;

public interface ViolationReportService {
    void resolvePendingForBannedOrganization(com.rehome.rehome.entity.auth.Account actor, UUID organizationId);


    ViolationReportResponse create(UUID reporterId, CreateViolationReportRequest request);

    List<ViolationReportGroupResponse> list(String status, String type);

    ViolationReportDetailResponse getDetail(UUID id);

    ViolationReportDetailResponse resolve(Account moderator, UUID id, ResolveViolationReportRequest request);

    ViolationReportDetailResponse dismiss(Account moderator, UUID id, DismissViolationReportRequest request);
}
