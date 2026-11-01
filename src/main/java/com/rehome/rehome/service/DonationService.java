package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateDonationNoteRequest;
import com.rehome.rehome.dto.request.CreateDonationRequest;
import com.rehome.rehome.dto.request.HandoverDonationsRequest;
import com.rehome.rehome.dto.response.DonationHandoverResponse;
import com.rehome.rehome.dto.response.DonationNoteResponse;
import com.rehome.rehome.dto.response.DonationResponse;
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

    // F-166: tổ chức xem lượt đang chờ lấy (mọi kho) và lượt chính họ đã nhận.
    List<DonationResponse> listOrganizationDonations(UUID organizationAccountId, DonationStatus status);

    // F-167: tổ chức tự ghi note khi nhận quyên góp tại địa chỉ riêng, không qua điểm ký gửi.
    DonationNoteResponse createDonationNote(UUID organizationAccountId, CreateDonationNoteRequest request);
}
