package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.SubscribeMembershipRequest;
import com.rehome.rehome.dto.response.AiTryOnQuotaResponse;
import com.rehome.rehome.dto.response.MembershipPlanResponse;
import com.rehome.rehome.dto.response.MembershipPurchaseResponse;
import com.rehome.rehome.dto.response.MyMembershipResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.SubscribeMembershipResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import java.util.List;
import java.util.UUID;

public interface MembershipService {

    // API §8.1 - public, khách chưa đăng nhập cũng xem được để quyết định đăng ký.
    // Chỉ trả plan active + purchasable (nên không có FREE), sắp theo sortOrder.
    List<MembershipPlanResponse> getPlans();

    // API §8.2 - LUÔN trả 200: chưa mua gói thì trả tier FREE với subscriptionId = null
    MyMembershipResponse getMyMembership(Account account);

    // API §8.3 - mua/gia hạn. Ví thì kích hoạt ngay trong transaction; MoMo/VNPay thì tạo
    // subscription PENDING_PAYMENT + trả payUrl, chờ IPN gọi applyGatewayPaymentSuccess.
    SubscribeMembershipResponse subscribe(Account account, SubscribeMembershipRequest request);

    // API §8.4 - lịch sử mua gói
    PageResponse<MembershipPurchaseResponse> getPurchaseHistory(Account account, int page, int size);

    // API §8.8 - hạn mức phối đồ AI (ảnh tĩnh)
    AiTryOnQuotaResponse getAiTryOnQuota(Account account);

    /**
     * Cổng thanh toán báo đã nhận tiền cho một subscription.
     *
     * <p>Quy tắc giống đơn hàng: cổng báo thành công <b>không</b> tự kích hoạt. Chỉ kích hoạt khi
     * subscription vẫn đang {@code PENDING_PAYMENT}; nếu đã bị hủy hoặc đã kích hoạt bằng đường
     * khác thì <b>hoàn tiền vào ví Member</b> - tiền là tiền thật, không được giữ lại. Dùng cờ
     * "đã hoàn" để không hoàn hai lần, giống {@code order.refundAmount != null}.
     */
    void applyGatewayPaymentSuccess(UUID subscriptionId, OrderPaymentMethod method, String note);

    /**
     * MomoServiceImpl.handleIpn dispatch sang đây khi {@code momoOrderId} mang tiền tố {@code SUB-}.
     * Cùng khuôn với {@code ItemWithdrawalRequestService.applyMomoFeePaymentResult}.
     *
     * @return true nếu đây đúng là giao dịch mua gói và đã xử lý xong (kể cả khi kết quả là thất
     *         bại); false nếu không khớp dòng nào để nơi gọi thử tiếp loại giao dịch khác
     */
    boolean applyMomoResult(String momoOrderId, int resultCode, String transactionId);

    // VnPayServiceImpl.handleIpn dispatch sang đây khi vnp_TxnRef mang tiền tố SUB-
    boolean applyVnPayResult(String vnpTxnRef, String responseCode, String transactionNo);
}
