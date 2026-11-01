package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCampaignRequest {
    private String title;
    private String description;
    private Instant startTime;
    private Instant endTime;

    // Ảnh bìa, URL trả về từ POST /uploads (folder=CAMPAIGN_COVERS); không bắt buộc
    private String coverUrl;

    // Cách nhận đồ (OC-02): phải chọn ít nhất 1 trong 2, được chọn cả hai.
    // true = nhận tại địa chỉ riêng (lấy từ hồ sơ tổ chức, không nhập tay)
    private Boolean receiveAtOwnAddress;

    // Các kho tổ chức muốn nhận đồ chuyển đến; nhiều kho, kho nào cũng được
    private List<String> warehouseIds;

    // Cột preferred_item_types của entity là 1 chuỗi -> service nối list lại bằng ", "
    private List<String> preferredItemTypes;
}
