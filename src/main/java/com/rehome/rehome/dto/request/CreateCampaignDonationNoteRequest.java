package com.rehome.rehome.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * OC-08: tổ chức ghi note khi nhận quyên góp tại địa chỉ riêng, gắn với 1 chiến dịch cụ thể
 * (campaignId lấy từ path, không nằm trong body).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCampaignDonationNoteRequest {
    private String donorName;
    private Boolean isAnonymous;

    @Size(max = 500, message = "note tối đa 500 ký tự.")
    private String note;
}
