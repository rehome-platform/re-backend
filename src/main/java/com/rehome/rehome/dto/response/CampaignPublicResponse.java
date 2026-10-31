package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

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
    private String description;
    private Instant startTime;
    private Instant endTime;
    private String locationName;
    private String status;
}
