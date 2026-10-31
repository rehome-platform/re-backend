package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateOrderRequest;
import com.rehome.rehome.dto.request.PackScanRequest;
import com.rehome.rehome.dto.request.PayOrdersRequest;
import com.rehome.rehome.dto.request.RateOrderRequest;
import com.rehome.rehome.dto.response.CreateShipmentResponse;
import com.rehome.rehome.dto.response.OrderCancelResponse;
import com.rehome.rehome.dto.response.OrderConfirmReceiptResponse;
import com.rehome.rehome.dto.response.OrderDetailResponse;
import com.rehome.rehome.dto.response.OrderListResponse;
import com.rehome.rehome.dto.response.OrderRatingResponse;
import com.rehome.rehome.dto.response.OrderResponse;
import com.rehome.rehome.dto.response.OrderTrackingResponse;
import com.rehome.rehome.dto.response.PackScanResponse;
import com.rehome.rehome.dto.response.PayOrdersResponse;
import com.rehome.rehome.dto.response.SoldOrderListResponse;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.ReturnedItemAction;
import com.rehome.rehome.entity.marketplace.Order;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    List<OrderResponse> createOrders(String email, CreateOrderRequest request);

    OrderListResponse getOrders(String email, String status, int page, int size);

    // Đơn có chứa hàng do chính người gọi ký gửi ("Đơn đã bán"). Khác getOrders: cái kia
    // lọc theo buyer_id. Mỗi đơn chỉ trả các món của người bán này, không trả thông tin
    // người mua. status rỗng = mọi trạng thái từ PAID trở đi.
    SoldOrderListResponse getSoldOrders(String email, String status, int page, int size);

    OrderDetailResponse getOrderDetail(String email, UUID orderId);

    OrderCancelResponse cancelOrder(String email, UUID orderId);

    // Trả một hoặc nhiều đơn bằng Ví ReHome: trừ ví và chốt đơn PAID trong cùng một transaction
    PayOrdersResponse payOrders(String email, PayOrdersRequest request);

    // Nạp các đơn của chính người mua và chắc chắn còn thanh toán được.
    // Dùng chung cho ví, MoMo và VNPay để ba đường thanh toán không lệch nhau về quyền lẫn mã lỗi
    // (ORDER_ALREADY_PAID / ORDER_NOT_PENDING).
    List<Order> loadPayableOrders(String email, List<UUID> orderIds);

    // Cổng thanh toán báo đã nhận tiền cho một đơn: chốt đơn nếu còn chờ thanh toán, hoặc hoàn
    // vào ví nếu đơn không còn chờ thanh toán nữa (IPN về muộn sau khi đơn đã bị hủy).
    void applyGatewayPaymentSuccess(UUID orderId, OrderPaymentMethod method, String note);

    OrderConfirmReceiptResponse confirmReceipt(String email, UUID orderId);

    OrderRatingResponse rateOrder(String email, UUID orderId, RateOrderRequest request);

    // API 69 (F-127) - Moderator quét SKU đối chiếu trước khi tạo vận đơn
    PackScanResponse packScan(UUID orderId, PackScanRequest request);

    // API 70 (F-127) - Moderator tạo vận đơn GHN sau khi đã quét đủ món
    CreateShipmentResponse createShipment(UUID orderId);

    // API 64 (F-060) - Member xem lịch sử vận chuyển đồng bộ từ GHN
    OrderTrackingResponse getTracking(String email, UUID orderId);

    // F-062 - hệ thống tự hủy đơn và hoàn tiền khi GHN báo thất lạc/hư hỏng/hủy vận đơn
    void cancelOrderForCarrierFailure(UUID orderId, String reason);

    // F-132/F-136 - trả hàng hợp lệ hoặc hàng trả thất lạc: hoàn 100% vào ví người mua, đơn chuyển RETURNED
    void refundOrderForReturn(UUID orderId, String note);

    // API 88 (F-133) - Moderator quyết định hướng xử lý món hàng đang ở RETURNED: đăng bán lại,
    // trả về người ký gửi hay tiêu hủy
    void applyReturnedItemAction(UUID orderId, ReturnedItemAction action);

    // F-064 phần tự chốt đơn: hệ thống tự chuyển DELIVERED -> COMPLETED khi buyer không bấm xác nhận.
    // Không payout ở đây - tiền giữ lại tới hết hạn trả hàng, xem payoutOrder.
    void autoCompleteOrder(UUID orderId);

    // Cộng ORDER_PAYOUT cho người ký gửi của mọi món trong đơn rồi ghi payoutAt. Idempotent:
    // không làm gì nếu đơn đã được trả tiền. Gọi từ OrderAutoCompleteServiceImpl (job quá hạn
    // 3 ngày) và từ ReturnRequestServiceImpl khi yêu cầu trả hàng bị từ chối/hủy/kết luận không hợp lệ.
    void payoutOrder(UUID orderId, String note);
}
