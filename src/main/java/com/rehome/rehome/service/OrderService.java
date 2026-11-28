package com.rehome.rehome.service;

import com.rehome.rehome.dto.request.CreateOrderRequest;
import com.rehome.rehome.dto.request.PackScanRequest;
import com.rehome.rehome.dto.request.PayOrdersRequest;
import com.rehome.rehome.dto.request.RateOrderRequest;
import com.rehome.rehome.dto.request.UpdateOrderShippingAddressRequest;
import com.rehome.rehome.dto.response.CreateShipmentResponse;
import com.rehome.rehome.dto.response.ShipmentLabelResponse;
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
import com.rehome.rehome.dto.response.WarehouseOrderListResponse;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.ReturnedItemAction;
import com.rehome.rehome.entity.marketplace.Order;

import java.util.List;
import java.util.UUID;

public interface OrderService {
    List<OrderResponse> createOrders(String email, CreateOrderRequest request);

    OrderListResponse getOrders(String email, String status, int page, int size);

    SoldOrderListResponse getSoldOrders(String email, String status, int page, int size);

    OrderDetailResponse getOrderDetail(String email, UUID orderId);

    WarehouseOrderListResponse getWarehouseSoldOrders(String email, String status, int page, int size);

    OrderCancelResponse cancelOrder(String email, UUID orderId);

    OrderResponse updateShippingAddress(String email, UUID orderId, UpdateOrderShippingAddressRequest request);

    PayOrdersResponse payOrders(String email, PayOrdersRequest request);

    List<Order> loadPayableOrders(String email, List<UUID> orderIds);

    void applyGatewayPaymentSuccess(UUID orderId, OrderPaymentMethod method, String note);

    OrderConfirmReceiptResponse confirmReceipt(String email, UUID orderId);

    OrderRatingResponse rateOrder(String email, UUID orderId, RateOrderRequest request);

    PackScanResponse packScan(String email, UUID orderId, PackScanRequest request);

    List<String> updatePackingPhotos(String email, UUID orderId,
            com.rehome.rehome.dto.request.UpdatePackingPhotosRequest request);

    CreateShipmentResponse createShipment(String email, UUID orderId);
    OrderResponse handoverToCarrier(String email, UUID orderId);

    ShipmentLabelResponse getShipmentLabel(String email, UUID orderId, String size);

    OrderTrackingResponse getTracking(String email, UUID orderId);

    void cancelOrderForCarrierFailure(UUID orderId, String reason);

    void refundOrderForReturn(UUID orderId, String note);

    void applyReturnedItemAction(UUID orderId, ReturnedItemAction action);

    void autoCompleteOrder(UUID orderId);

    void payoutOrder(UUID orderId, String note);
}
