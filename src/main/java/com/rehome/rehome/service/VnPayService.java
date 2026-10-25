package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.CreateVnPayResponse;
import com.rehome.rehome.dto.response.PaymentStatusResponse;
import com.rehome.rehome.dto.response.VnPayIpnResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface VnPayService {
    // Một giao dịch VNPay cho nhiều đơn - xem MomoService.createPaymentForOrders
    CreateVnPayResponse createPaymentForOrders(String email, List<UUID> orderIds, String customReturnUrl,
                                               String clientIp);

    VnPayIpnResult handleIpn(Map<String, String> params);

    PaymentStatusResponse checkPaymentStatus(String email, UUID orderId);

    PaymentStatusResponse getReturnInfo(Map<String, String> params);
}
