package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateCampaignRequest;
import com.rehome.rehome.dto.response.CampaignPublicResponse;
import com.rehome.rehome.dto.response.CampaignStatusResponse;
import com.rehome.rehome.dto.response.CampaignSummaryResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.WarehouseCampaignResponse;

import java.util.UUID;

public interface CampaignService {

    CampaignPublicResponse getPublicDetail(UUID campaignId, UUID viewerAccountId);

    CampaignStatusResponse createCampaign(UUID organizationAccountId, CreateCampaignRequest request);

    CampaignPublicResponse updateCampaign(UUID organizationAccountId, UUID campaignId,
                                          CreateCampaignRequest request);

    CampaignStatusResponse unhideCampaign(com.rehome.rehome.entity.auth.Account moderator, UUID campaignId, String reason);

    CampaignStatusResponse closeCampaign(UUID organizationAccountId, UUID campaignId);

    PageResponse<CampaignSummaryResponse> listMyCampaigns(UUID organizationAccountId, String status,
                                                          int page, int size);

    PageResponse<CampaignPublicResponse> listPublicCampaigns(String search, String status, UUID warehouseId,
                                                             int page, int size);

    PageResponse<WarehouseCampaignResponse> listCampaignsReceivingAtWarehouse(UUID warehouseId, int page, int size);

    PageResponse<CampaignPublicResponse> listOrganizationCampaigns(UUID organizationId, String status,
                                                                    int page, int size);
}
