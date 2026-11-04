package com.rehome.rehome.service;

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

    // F-163: Moderator ghi nhận 1 lượt quyên góp tại điểm ký gửi của mình.
    DonationResponse recordDonation(Account moderator, UUID warehouseId, CreateDonationRequest request);

    // F-164: các lượt quyên góp tại điểm ký gửi; status/campaignId == null thì không lọc theo tiêu chí đó.
    List<DonationResponse> listDonations(Account moderator, UUID warehouseId, UUID campaignId, DonationStatus status);

    // F-165: bàn giao các lượt đang chờ lấy cho tổ chức đến nhận. Hoặc bàn giao hết, hoặc không lượt nào.
    DonationHandoverResponse handoverDonations(Account moderator, UUID warehouseId,
                                               HandoverDonationsRequest request);

    // F-166/OC-07: tổ chức xem lượt đang chờ lấy (mọi kho) và lượt chính họ đã nhận,
    // lọc thêm theo chiến dịch/điểm ký gửi, có phân trang. campaignId/warehouseId/status
    // == null thì không lọc theo tiêu chí đó.
    PageResponse<DonationResponse> listOrganizationDonations(UUID organizationAccountId, UUID campaignId,
                                                             UUID warehouseId, DonationStatus status,
                                                             int page, int size);

    // F-167: tổ chức tự ghi note khi nhận quyên góp tại địa chỉ riêng, không qua điểm ký gửi.
    DonationNoteResponse createDonationNote(UUID organizationAccountId, CreateDonationNoteRequest request);

    // OC-08: giống F-167 nhưng campaignId bắt buộc, lấy từ path /campaigns/{id}/donation-notes
    // thay vì body, và phải thuộc đúng tổ chức đang gọi.
    DonationNoteResponse createCampaignDonationNote(UUID organizationAccountId, UUID campaignId,
                                                     CreateCampaignDonationNoteRequest request);

    // OC-08/F-167: tổ chức xem các note quyên góp của chính chiến dịch mình, mới nhất trước.
    // Phải xem được cả khi chiến dịch đã CLOSED. Campaign không tồn tại -> 404, thuộc
    // tổ chức khác -> 403.
    PageResponse<DonationNoteResponse> listCampaignDonationNotes(UUID organizationAccountId, UUID campaignId,
                                                                   int page, int size);
}
