package com.rehome.rehome.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// Đã thanh toán xong (ví, hoặc MoMo/VNPay đã confirm trước đó) -> id/feeCharged/status có giá trị, payUrl null.
// Còn phải trả qua cổng (MoMo/VNPay vừa tạo giao dịch) -> chỉ payUrl có giá trị, các field khác null.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PayItemWithdrawalFeeResponse {
    private String id;
    private BigDecimal feeCharged;
    private String status;
    private String payUrl;
}
