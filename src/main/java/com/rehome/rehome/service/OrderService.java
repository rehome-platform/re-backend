package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateOrderRequest;
import com.rehome.rehome.dto.request.PackScanRequest;
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

import java.util.List;
import java.util.UUID;

public interface OrderService {
    List<OrderResponse> createOrders(String email, CreateOrderRequest request);

    OrderListResponse getOrders(String email, String status, int page, int size);

    OrderDetailResponse getOrderDetail(String email, UUID orderId);

    OrderCancelResponse cancelOrder(String email, UUID orderId);

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
}
