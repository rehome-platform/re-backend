package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateDonationNoteRequest {
    // Có thể bỏ trống khi tổ chức nhận đồ ngoài chiến dịch nào
    private String campaignId;

    private String donorName;
    private Boolean isAnonymous;
    private String note;
}
