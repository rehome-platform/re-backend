package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentLabelResponse {
    private String id;
    private String ghnTrackingCode;
    // A5 | 80x80 | 52x70
    private String size;
    // Link in phiếu GHN, token hết hạn sau ~30 phút
    private String printUrl;
}

