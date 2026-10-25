package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.MomoIpnRequest;
import com.rehome.rehome.dto.response.CreateMomoResponse;
import com.rehome.rehome.dto.response.PaymentStatusResponse;

import java.util.List;
import java.util.UUID;

public interface MomoService {
    // Một giao dịch MoMo cho nhiều đơn: giỏ có món ở 2 kho bị tách thành 2 đơn nhưng người mua
    // chỉ quét QR trả một lần
    CreateMomoResponse createPaymentForOrders(String email, List<UUID> orderIds, String customRedirectUrl);

    void handleIpn(MomoIpnRequest ipnRequest);

    PaymentStatusResponse checkPaymentStatus(String email, UUID orderId);

    PaymentStatusResponse getReturnInfo(MomoIpnRequest returnParams);
}
