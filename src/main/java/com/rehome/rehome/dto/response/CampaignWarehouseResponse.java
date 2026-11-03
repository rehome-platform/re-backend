package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 1 kho mà chiến dịch đã chọn để nhận đồ (OC-02/OC-03).
 * pendingCount là số lượt quyên góp đang chờ tổ chức tới lấy tại kho đó CHO RIÊNG chiến dịch
 * này - không tính lượt chờ lấy của chiến dịch khác dùng chung kho.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignWarehouseResponse {
    private String id;
    private String name;
    private String address;
    private String openingHours;
    private long pendingCount;
}
