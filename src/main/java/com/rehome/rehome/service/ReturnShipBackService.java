package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.PayShipBackFeeRequest;
import com.rehome.rehome.dto.response.PayShipBackFeeResponse;
import com.rehome.rehome.dto.response.ReturnRequestResponse;
import com.rehome.rehome.dto.response.ShipBackPaymentStatusResponse;
import com.rehome.rehome.dto.response.VnPayIpnResult;
import com.rehome.rehome.entity.auth.Account;

import java.util.UUID;

public interface ReturnShipBackService {

    String VNP_REF_PREFIX = "RSB-";

    static boolean isShipBackRef(String vnpTxnRef) {
        return vnpTxnRef != null && vnpTxnRef.startsWith(VNP_REF_PREFIX);
    }

    PayShipBackFeeResponse payFee(String email, UUID returnRequestId, PayShipBackFeeRequest request, String clientIp);

    VnPayIpnResult handleVnPayIpn(String vnpTxnRef, String vnpAmountRaw, String responseCode, String transactionNo);

    ShipBackPaymentStatusResponse syncPaymentStatus(String email, UUID returnRequestId);

    ReturnRequestResponse ship(Account moderator, UUID returnRequestId);
}
