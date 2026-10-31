package com.rehome.rehome.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayItemWithdrawalFeeRequest {
    // WALLET / MOMO / VNPAY - thanh toán riêng lẻ bằng đúng 1 phương thức, không chia nhỏ
    private String paymentMethod;
}
