package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateCampaignRequest;
import com.rehome.rehome.dto.response.CampaignPublicResponse;
import com.rehome.rehome.dto.response.CampaignStatusResponse;
import com.rehome.rehome.dto.response.CampaignSummaryResponse;
import com.rehome.rehome.dto.response.PageResponse;

import java.util.UUID;

public interface CampaignService {

    // F-083: chi tiết công khai 1 chiến dịch. viewerAccountId == null khi guest/chưa đăng
    // nhập; donationNoteCount chỉ trả khi viewerAccountId chính là tổ chức sở hữu chiến dịch.
    CampaignPublicResponse getPublicDetail(UUID campaignId, UUID viewerAccountId);

    // F-098/F-099: tổ chức tạo chiến dịch mới, chặn nếu đã đạt hạn mức chiến dịch đang chạy.
    CampaignStatusResponse createCampaign(UUID organizationAccountId, CreateCampaignRequest request);

    // F-100: sửa chiến dịch của chính tổ chức, chỉ khi còn đang mở. Field bỏ trống thì giữ nguyên.
    CampaignPublicResponse updateCampaign(UUID organizationAccountId, UUID campaignId,
                                          CreateCampaignRequest request);

    // F-101: tổ chức đóng chiến dịch trước hạn. Gọi lại khi đã đóng thì không báo lỗi.
    CampaignStatusResponse closeCampaign(UUID organizationAccountId, UUID campaignId);

    // F-103: chiến dịch của chính tổ chức; status == null thì lấy mọi trạng thái.
    PageResponse<CampaignSummaryResponse> listMyCampaigns(UUID organizationAccountId, String status,
                                                          int page, int size);

    // Duyệt công khai xuyên tổ chức; status == null thì mặc định chỉ chiến dịch đang mở (ACTIVE).
    // search == null/rỗng thì không lọc theo từ khóa; có search thì tìm theo title hoặc tên tổ chức.
    PageResponse<CampaignPublicResponse> listPublicCampaigns(String search, String status, int page, int size);

    // Chiến dịch công khai của 1 tổ chức cụ thể (F-082/F-083); status == null thì mặc định
    // chỉ chiến dịch đang mở (ACTIVE). 404 nếu tổ chức không tồn tại hoặc chưa VERIFIED.
    PageResponse<CampaignPublicResponse> listOrganizationCampaigns(UUID organizationId, String status,
                                                                    int page, int size);
}
