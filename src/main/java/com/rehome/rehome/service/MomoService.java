package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.MomoIpnRequest;
import com.rehome.rehome.dto.response.CreateMomoResponse;
import com.rehome.rehome.dto.response.PaymentStatusResponse;

import java.util.UUID;

public interface MomoService {
    CreateMomoResponse createPaymentForOrder(String email, UUID orderId, String customRedirectUrl);

    void handleIpn(MomoIpnRequest ipnRequest);

    PaymentStatusResponse checkPaymentStatus(String email, UUID orderId);

    PaymentStatusResponse getReturnInfo(MomoIpnRequest returnParams);
}
