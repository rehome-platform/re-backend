package com.rehome.rehome.service;

import com.rehome.rehome.dto.response.CreateVnPayResponse;
import com.rehome.rehome.dto.response.PaymentStatusResponse;
import com.rehome.rehome.dto.response.VnPayIpnResult;

import java.util.Map;
import java.util.UUID;

public interface VnPayService {
    CreateVnPayResponse createPaymentForOrder(String email, UUID orderId, String customReturnUrl, String clientIp);

    VnPayIpnResult handleIpn(Map<String, String> params);

    PaymentStatusResponse checkPaymentStatus(String email, UUID orderId);

    PaymentStatusResponse getReturnInfo(Map<String, String> params);
}
