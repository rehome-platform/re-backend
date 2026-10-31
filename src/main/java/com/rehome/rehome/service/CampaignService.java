package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateCampaignRequest;
import com.rehome.rehome.dto.response.CampaignPublicResponse;
import com.rehome.rehome.dto.response.CampaignStatusResponse;
import com.rehome.rehome.dto.response.CampaignSummaryResponse;
import com.rehome.rehome.dto.response.PageResponse;

import java.util.UUID;

public interface CampaignService {

    // F-083: chi tiết công khai 1 chiến dịch.
    CampaignPublicResponse getPublicDetail(UUID campaignId);

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
}
