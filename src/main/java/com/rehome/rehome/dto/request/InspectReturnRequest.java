package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectReturnRequest {
    // true = trả hàng hợp lệ (hoàn 100% cho người mua), false = không hợp lệ (không hoàn tiền)
    private Boolean valid;
    private String resolutionNote;
}
