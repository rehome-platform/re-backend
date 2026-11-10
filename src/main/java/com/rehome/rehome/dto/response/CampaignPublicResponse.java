package com.rehome.rehome.dto.response;

import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Chi tiết công khai 1 chiến dịch (F-083). Guest xem được, không cần token.
 * Không có kg mục tiêu/đã nhận vì donation không lưu dữ liệu giao dịch.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignPublicResponse {
    private String id;
    private String title;
    private String description;
    private String coverUrl;
    private Instant startTime;
    private Instant endTime;
    private String status;

    // Cách nhận đồ (OC-02)
    private boolean receiveAtOwnAddress;
    // null khi receiveAtOwnAddress = false
    private CampaignOwnAddressResponse ownAddress;
    private List<CampaignWarehouseResponse> warehouses;
    private List<String> preferredItemTypes;
    private Instant createdAt;
    private CampaignOrganizationSummaryResponse organization;

    // OC-08/F-167: chỉ trả khi người xem chính là tổ chức sở hữu chiến dịch; null với guest/member
    private Long donationNoteCount;

    // Lý do Moderator ẩn chiến dịch (status = HIDDEN); chỉ trả cho tổ chức sở hữu, null với mọi
    // viewer khác - Member/Guest nhận 404 trước khi tới được field này (xem CampaignServiceImpl)
    private String hiddenReason;
}
