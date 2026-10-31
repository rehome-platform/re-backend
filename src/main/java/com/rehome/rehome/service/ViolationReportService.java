package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateViolationReportRequest;
import com.rehome.rehome.dto.response.ViolationReportResponse;

import java.util.UUID;

public interface ViolationReportService {

    // F-088: báo cáo vi phạm, dùng chung cho bài viết / chiến dịch / tài khoản tổ chức.
    ViolationReportResponse create(UUID reporterId, CreateViolationReportRequest request);
}
