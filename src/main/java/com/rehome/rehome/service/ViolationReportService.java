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

    // F-088: báo cáo vi phạm, dùng chung cho bài viết / chiến dịch / tài khoản tổ chức. 409
    // DUPLICATE_PENDING_REPORT nếu Member này đã có báo cáo PENDING cho đúng target.
    ViolationReportResponse create(UUID reporterId, CreateViolationReportRequest request);

    // Moderator: hàng đợi báo cáo, gộp theo target - status/type == null thì không lọc tiêu chí đó.
    // Sắp theo reportCount desc rồi oldestReportedAt asc.
    List<ViolationReportGroupResponse> list(String status, String type);

    // Moderator: chi tiết 1 báo cáo, kèm snapshot target, các báo cáo khác cùng target, và lịch sử
    // vi phạm của tổ chức sở hữu target.
    ViolationReportDetailResponse getDetail(UUID id);

    // Thực hiện action trên target rồi đóng (RESOLVED) TẤT CẢ báo cáo PENDING cùng target - không
    // chỉ riêng report id trong path. Ghi audit log, báo cho tổ chức bị xử lý và mọi người đã báo cáo.
    ViolationReportDetailResponse resolve(Account moderator, UUID id, ResolveViolationReportRequest request);

    // Đóng (DISMISSED) TẤT CẢ báo cáo PENDING cùng target, không đổi gì trên nội dung. Ghi audit
    // log, báo cho mọi người đã báo cáo.
    ViolationReportDetailResponse dismiss(Account moderator, UUID id, DismissViolationReportRequest request);
}
