package com.rehome.rehome.dto.request;

import com.rehome.rehome.exception.BadRequestException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Tạo một giao dịch cổng thanh toán cho một hoặc nhiều đơn: giỏ có món ở 2 kho bị tách thành
// 2 đơn nhưng người mua chỉ nên quét QR trả một lần.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGatewayPaymentRequest {
    private List<String> orderIds;

    // URL cổng thanh toán trả người dùng về sau khi trả xong; bỏ trống thì dùng cấu hình server.
    // Với VNPay đây chính là vnp_ReturnUrl.
    private String redirectUrl;

    // Bỏ id trùng để một đơn lặp hai lần trong body không tạo hai dòng payments
    public List<UUID> toOrderIds() {
        if (orderIds == null || orderIds.isEmpty()) {
            throw new BadRequestException("orderIds là bắt buộc.");
        }

        List<UUID> parsed = new ArrayList<>();
        for (String rawOrderId : orderIds) {
            UUID orderId;
            try {
                orderId = UUID.fromString(rawOrderId.trim());
            } catch (RuntimeException ex) {
                throw new BadRequestException("orderId không hợp lệ: " + rawOrderId);
            }
            if (!parsed.contains(orderId)) {
                parsed.add(orderId);
            }
        }
        return parsed;
    }
}
