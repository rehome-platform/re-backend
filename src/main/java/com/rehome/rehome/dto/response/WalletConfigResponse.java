package com.rehome.rehome.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Tham số màn rút tiền, để FE hiện "tối thiểu 50.000 đ" và "đã dùng 2/3 lượt hôm nay"
// mà không phải hardcode hay đợi tới lúc bị 409 mới biết.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletConfigResponse {
    private BigDecimal minPayout;
    private Integer maxPayoutsPerDay;
    private Long usedToday;
}
