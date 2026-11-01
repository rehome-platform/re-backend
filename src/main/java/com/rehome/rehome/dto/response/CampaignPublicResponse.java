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
}
