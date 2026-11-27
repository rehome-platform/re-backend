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

    // Member trả phí gửi lại hàng trả không hợp lệ (WALLET hoặc VNPAY)
    PayShipBackFeeResponse payFee(String email, UUID returnRequestId, PayShipBackFeeRequest request, String clientIp);

    // IPN từ VNPay cho giao dịch trả phí gửi lại (tiền tố RSB-)
    VnPayIpnResult handleVnPayIpn(String vnpTxnRef, String vnpAmountRaw, String responseCode, String transactionNo);

    // App hỏi lại trạng thái thanh toán sau khi quay về từ VNPay (nếu còn chờ thì querydr sang VNPay)
    ShipBackPaymentStatusResponse syncPaymentStatus(String email, UUID returnRequestId);

    // Moderator của kho tạo vận đơn GHN chiều kho -> người mua sau khi phí đã PAID
    ReturnRequestResponse ship(Account moderator, UUID returnRequestId);
}
