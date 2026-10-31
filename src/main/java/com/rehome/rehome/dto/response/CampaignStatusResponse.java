package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Id + trạng thái của chiến dịch — dùng cho lúc tạo (F-098) và lúc đóng (F-101).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampaignStatusResponse {
    private String id;
    private String status;
}
