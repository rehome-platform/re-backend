package com.rehome.rehome.dto.request;

import com.rehome.rehome.exception.BadRequestException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateGatewayPaymentRequest {
    private List<String> orderIds;

    private String redirectUrl;

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
