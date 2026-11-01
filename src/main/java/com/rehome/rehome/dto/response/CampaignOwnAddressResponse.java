package com.rehome.rehome.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Địa chỉ riêng của tổ chức để nhận đồ quyên góp (OC-02), lấy từ điểm nhận đồ
 * trong hồ sơ tổ chức (OrganizationProfile.donationPoint*) - không nhập tay theo chiến dịch.
 * Chỉ có mặt trong response khi campaign.receiveAtOwnAddress = true.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignOwnAddressResponse {
    private String name;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String receivingHours;
    private String contactPhone;
}
