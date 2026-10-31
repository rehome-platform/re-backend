package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateDonationRequest;
import com.rehome.rehome.dto.request.HandoverDonationsRequest;
import com.rehome.rehome.dto.response.DonationHandoverResponse;
import com.rehome.rehome.dto.response.DonationResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.DonationStatus;

import java.util.List;
import java.util.UUID;

public interface DonationService {

    // F-163: Moderator ghi nhận 1 lượt quyên góp tại điểm ký gửi của mình.
    DonationResponse recordDonation(Account moderator, UUID warehouseId, CreateDonationRequest request);

    // F-164: các lượt quyên góp tại điểm ký gửi; status == null thì lấy tất cả.
    List<DonationResponse> listDonations(Account moderator, UUID warehouseId, DonationStatus status);

    // F-165: bàn giao các lượt đang chờ lấy cho tổ chức đến nhận. Hoặc bàn giao hết, hoặc không lượt nào.
    DonationHandoverResponse handoverDonations(Account moderator, UUID warehouseId,
                                               HandoverDonationsRequest request);
}
