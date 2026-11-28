package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.TransferDonationsRequest;

import com.rehome.rehome.dto.request.CreateCampaignDonationNoteRequest;
import com.rehome.rehome.dto.request.CreateDonationNoteRequest;
import com.rehome.rehome.dto.request.CreateDonationRequest;
import com.rehome.rehome.dto.request.HandoverDonationsRequest;
import com.rehome.rehome.dto.response.DonationHandoverResponse;
import com.rehome.rehome.dto.response.DonationNoteResponse;
import com.rehome.rehome.dto.response.DonationResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.DonationStatus;

import java.util.List;
import java.util.UUID;

public interface DonationService {

    DonationResponse recordDonation(Account moderator, UUID warehouseId, CreateDonationRequest request);

    List<DonationResponse> listDonations(Account moderator, UUID warehouseId, UUID campaignId, DonationStatus status);

    void holdPendingDonations(UUID organizationId);

    void holdCampaignDonations(UUID campaignId);

    void restoreCampaignDonations(UUID campaignId);

    List<DonationResponse> reassignDonations(Account moderator, UUID warehouseId, List<String> donationIds,
                                             UUID campaignId);

    List<DonationResponse> transferHeldDonations(Account moderator, UUID warehouseId, TransferDonationsRequest request);

    DonationHandoverResponse handoverDonations(Account moderator, UUID warehouseId,
                                               HandoverDonationsRequest request);

    PageResponse<DonationResponse> listOrganizationDonations(UUID organizationAccountId, UUID campaignId,
                                                             UUID warehouseId, DonationStatus status,
                                                             int page, int size);

    DonationNoteResponse createDonationNote(UUID organizationAccountId, CreateDonationNoteRequest request);

    DonationNoteResponse createCampaignDonationNote(UUID organizationAccountId, UUID campaignId,
                                                     CreateCampaignDonationNoteRequest request);

    PageResponse<DonationNoteResponse> listCampaignDonationNotes(UUID organizationAccountId, UUID campaignId,
                                                                   int page, int size);
}
