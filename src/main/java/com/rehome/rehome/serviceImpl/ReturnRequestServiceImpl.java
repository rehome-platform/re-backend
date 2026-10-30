package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnApi;
import com.rehome.rehome.dto.request.CreateReturnRequestRequest;
import com.rehome.rehome.dto.request.GhnCreateOrderRequest;
import com.rehome.rehome.dto.request.GhnOrderItem;
import com.rehome.rehome.dto.request.InspectReturnRequest;
import com.rehome.rehome.dto.request.InvalidReturnChoiceRequest;
import com.rehome.rehome.dto.request.RejectReturnRequestRequest;
import com.rehome.rehome.dto.response.GhnCreateOrderResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ReturnRequestResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.entity.marketplace.ReturnRequestPhoto;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.exception.GhnException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.ReturnRequestPhotoRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.service.OrderService;
import com.rehome.rehome.service.ReturnRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ReturnRequestServiceImpl implements ReturnRequestService {

    private static final List<ReturnRequestStatus> OPEN_STATUSES = List.of(
            ReturnRequestStatus.PENDING,
            ReturnRequestStatus.APPROVED,
            ReturnRequestStatus.SHIPPED_BACK,
            ReturnRequestStatus.INSPECTING);

    // Vận đơn chiều trả bị GHN báo thất lạc/hư hỏng/tiêu hủy -> F-136
    private static final List<String> LOST_GHN_STATUSES = List.of("lost", "damage", "scrap");

    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestPhotoRepository returnRequestPhotoRepository;
    private final OrderService orderService;
    private final GhnApi ghnApi;

    @Value("${app.return.request-window-days}")
    private long requestWindowDays;

    @Value("${app.return.handover-deadline-days}")
    private long handoverDeadlineDays;

    @Value("${app.return.pickup-deadline-days}")
    private long pickupDeadlineDays;

    @Value("${ghn.token}")
    private String ghnToken;

    @Value("${ghn.shop-id}")
    private String ghnShopId;

    @Value("${ghn.default-package.weight-grams}")
    private int defaultPackageWeightGrams;

    @Value("${ghn.default-package.length-cm}")
    private int defaultPackageLengthCm;

    @Value("${ghn.default-package.width-cm}")
    private int defaultPackageWidthCm;

    @Value("${ghn.default-package.height-cm}")
    private int defaultPackageHeightCm;

    @Override
    @Transactional
    public ReturnRequestResponse create(String email, CreateReturnRequestRequest request) {

        if (!StringUtils.hasText(request.getOrderId())) {
            throw new BadRequestException("orderId là bắt buộc.");
        }
        if (!StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("reason là bắt buộc.");
        }
        if (request.getPhotoUrls() == null || request.getPhotoUrls().isEmpty()) {
            throw new BadRequestException("Cần ít nhất 1 ảnh làm bằng chứng.");
        }

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        UUID orderId;
        try {
            orderId = UUID.fromString(request.getOrderId());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("orderId không hợp lệ.");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn hàng."));

        if (!order.getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenException("Bạn không có quyền trả hàng cho đơn này.");
        }

        // Chỉ trả được đơn đã giao mà chưa chốt, vì chốt đơn là đã payout cho người bán
        if (order.getStatus() != OrderStatus.DELIVERED || order.getDeliveredAt() == null) {
            throw new ConflictException(
                    "Chỉ gửi yêu cầu trả hàng cho đơn đã giao và chưa xác nhận nhận hàng.",
                    "ORDER_NOT_RETURNABLE");
        }

        Instant returnWindowEnd = order.getDeliveredAt().plus(requestWindowDays, ChronoUnit.DAYS);
        if (Instant.now().isAfter(returnWindowEnd)) {
            throw new ConflictException(
                    "Đã quá hạn " + requestWindowDays + " ngày gửi yêu cầu trả hàng.",
                    "RETURN_WINDOW_EXPIRED");
        }

        if (returnRequestRepository.existsByOrder_IdAndStatusIn(orderId, OPEN_STATUSES)) {
            throw new ConflictException("Đơn đã có yêu cầu trả hàng đang xử lý.", "RETURN_REQUEST_EXISTS");
        }

        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .reason(request.getReason())
                .status(ReturnRequestStatus.PENDING)
                // Mốc thật được chốt lại lúc Moderator duyệt và tạo vận đơn trả
                .handoverDeadline(Instant.now().plus(handoverDeadlineDays, ChronoUnit.DAYS))
                .build();
        returnRequestRepository.save(returnRequest);

        List<ReturnRequestPhoto> photos = request.getPhotoUrls().stream()
                .map(url -> ReturnRequestPhoto.builder()
                        .returnRequest(returnRequest)
                        .imageUrl(url)
                        .build())
                .toList();
        returnRequestPhotoRepository.saveAll(photos);

        return toResponse(returnRequest, request.getPhotoUrls());
    }

    @Override
    public PageResponse<ReturnRequestResponse> getMyReturnRequests(String email, int page, int size) {

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<ReturnRequest> result = returnRequestRepository
                .findByOrder_Buyer_IdOrderByCreatedAtDesc(buyer.getId(), pageable);

        return PageResponse.from(result.map(this::toResponseWithPhotos));
    }

    @Override
    public ReturnRequestResponse getMyReturnRequest(String email, UUID id) {

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        ReturnRequest returnRequest = findOrThrow(id);

        if (!returnRequest.getOrder().getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenException("Bạn không có quyền xem yêu cầu trả hàng này.");
        }

        return toResponseWithPhotos(returnRequest);
    }

    @Override
    public PageResponse<ReturnRequestResponse> getQueue(String status, int page, int size) {

        List<ReturnRequestStatus> statuses = StringUtils.hasText(status)
                ? List.of(parseStatus(status))
                : OPEN_STATUSES;

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<ReturnRequest> result = returnRequestRepository
                .findByStatusInOrderByCreatedAtAsc(statuses, pageable);

        return PageResponse.from(result.map(this::toResponseWithPhotos));
    }

    @Override
    public ReturnRequestResponse getDetailForModerator(UUID id) {
        return toResponseWithPhotos(findOrThrow(id));
    }

    // F-131: duyệt yêu cầu và tạo vận đơn GHN chiều ngược (từ địa chỉ người mua về kho)
    @Override
    @Transactional
    public ReturnRequestResponse approve(UUID id) {

        ReturnRequest returnRequest = findOrThrow(id);

        // Đã tạo vận đơn trả trước đó thì trả lại kết quả cũ, không gọi GHN lần nữa
        if (StringUtils.hasText(returnRequest.getGhnReturnTrackingCode())) {
            return toResponseWithPhotos(returnRequest);
        }

        if (returnRequest.getStatus() != ReturnRequestStatus.PENDING) {
            throw new ConflictException("Yêu cầu trả hàng không ở trạng thái chờ duyệt.", "RETURN_NOT_PENDING");
        }

        Order order = returnRequest.getOrder();
        List<OrderItem> items = orderItemRepository.findAllByOrder_IdWithProductOrderByIdAsc(order.getId());

        GhnCreateOrderResponse ghnResponse;
        try {
            ghnResponse = ghnApi.createOrder(ghnToken, ghnShopId, buildReturnShipmentRequest(returnRequest, items));
        } catch (Exception ex) {
            log.error(">>>> Loi goi GHN tao van don tra cho returnRequestId={}: {}", id, ex.getMessage());
            throw new GhnException("GHN_ERROR: không thể tạo vận đơn trả, vui lòng thử lại.");
        }

        if (ghnResponse == null || ghnResponse.getData() == null
                || !StringUtils.hasText(ghnResponse.getData().getOrderCode())) {
            String ghnMessage = ghnResponse != null ? ghnResponse.getMessage() : null;
            log.error(">>>> GHN khong tra ve order_code cho van don tra returnRequestId={}: {}", id, ghnMessage);
            throw new GhnException("GHN_ERROR: không thể tạo vận đơn trả, vui lòng thử lại.");
        }

        returnRequest.setGhnReturnTrackingCode(ghnResponse.getData().getOrderCode());
        returnRequest.setStatus(ReturnRequestStatus.APPROVED);
        returnRequest.setHandoverDeadline(Instant.now().plus(handoverDeadlineDays, ChronoUnit.DAYS));
        returnRequestRepository.save(returnRequest);

        return toResponseWithPhotos(returnRequest);
    }

    @Override
    @Transactional
    public ReturnRequestResponse reject(UUID id, RejectReturnRequestRequest request) {

        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("reason là bắt buộc.");
        }

        ReturnRequest returnRequest = findOrThrow(id);

        if (returnRequest.getStatus() != ReturnRequestStatus.PENDING) {
            throw new ConflictException("Yêu cầu trả hàng không ở trạng thái chờ duyệt.", "RETURN_NOT_PENDING");
        }

        returnRequest.setStatus(ReturnRequestStatus.CANCELLED);
        returnRequest.setResolutionNote(request.getReason());
        returnRequest.setResolvedAt(Instant.now());
        returnRequestRepository.save(returnRequest);

        return toResponseWithPhotos(returnRequest);
    }

    // F-068: Member bàn giao hàng cho GHN (webhook GHN cũng tự chuyển trạng thái này khi tài xế lấy hàng)
    @Override
    @Transactional
    public ReturnRequestResponse confirmHandover(String email, UUID id) {

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        ReturnRequest returnRequest = findOrThrow(id);

        if (!returnRequest.getOrder().getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenException("Bạn không có quyền cập nhật yêu cầu trả hàng này.");
        }

        if (returnRequest.getStatus() == ReturnRequestStatus.SHIPPED_BACK) {
            return toResponseWithPhotos(returnRequest);
        }

        if (returnRequest.getStatus() != ReturnRequestStatus.APPROVED) {
            throw new ConflictException("Yêu cầu trả hàng chưa được duyệt.", "RETURN_NOT_APPROVED");
        }

        returnRequest.setStatus(ReturnRequestStatus.SHIPPED_BACK);
        returnRequestRepository.save(returnRequest);

        return toResponseWithPhotos(returnRequest);
    }

    @Override
    @Transactional
    public ReturnRequestResponse startInspection(UUID id) {

        ReturnRequest returnRequest = findOrThrow(id);

        if (returnRequest.getStatus() == ReturnRequestStatus.INSPECTING) {
            return toResponseWithPhotos(returnRequest);
        }

        if (returnRequest.getStatus() != ReturnRequestStatus.SHIPPED_BACK) {
            throw new ConflictException("Hàng trả chưa được bàn giao cho GHN.", "RETURN_NOT_SHIPPED_BACK");
        }

        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
        returnRequestRepository.save(returnRequest);

        return toResponseWithPhotos(returnRequest);
    }

    // F-132: kết luận hợp lệ thì hoàn 100% cho người mua; không hợp lệ thì không hoàn tiền,
    // Member có pickupDeadline ngày để chọn cách nhận lại hàng (F-070)
    @Override
    @Transactional
    public ReturnRequestResponse inspect(UUID id, InspectReturnRequest request) {

        if (request == null || request.getValid() == null) {
            throw new BadRequestException("valid là bắt buộc.");
        }

        ReturnRequest returnRequest = findOrThrow(id);

        if (returnRequest.getStatus() != ReturnRequestStatus.INSPECTING) {
            throw new ConflictException("Yêu cầu trả hàng chưa ở trạng thái đang kiểm.", "RETURN_NOT_INSPECTING");
        }

        Instant now = Instant.now();
        returnRequest.setResolutionNote(request.getResolutionNote());

        if (Boolean.TRUE.equals(request.getValid())) {
            returnRequest.setStatus(ReturnRequestStatus.VALID);
            returnRequest.setResolvedAt(now);
            returnRequestRepository.save(returnRequest);

            orderService.refundOrderForReturn(
                    returnRequest.getOrder().getId(),
                    "Hoàn tiền trả hàng hợp lệ - đơn " + returnRequest.getOrder().getOrderNo());
        } else {
            // Chưa đóng (resolvedAt để trống): còn chờ Member nhận lại hàng, hết hạn thì F-071 đóng hộ
            returnRequest.setStatus(ReturnRequestStatus.INVALID);
            returnRequest.setPickupDeadline(now.plus(pickupDeadlineDays, ChronoUnit.DAYS));
            returnRequestRepository.save(returnRequest);
        }

        return toResponseWithPhotos(returnRequest);
    }

    // F-070: Member chọn gửi lại (tự trả phí) hoặc đến kho nhận hàng trả không hợp lệ
    @Override
    @Transactional
    public ReturnRequestResponse chooseInvalidReturn(String email, UUID id, InvalidReturnChoiceRequest request) {

        if (request == null || request.getChoice() == null) {
            throw new BadRequestException("choice phải là SHIP_BACK hoặc WAREHOUSE_PICKUP.");
        }

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        ReturnRequest returnRequest = findOrThrow(id);

        if (!returnRequest.getOrder().getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenException("Bạn không có quyền cập nhật yêu cầu trả hàng này.");
        }

        if (returnRequest.getStatus() != ReturnRequestStatus.INVALID) {
            throw new ConflictException(
                    "Chỉ chọn cách nhận lại hàng khi yêu cầu bị kết luận không hợp lệ.",
                    "RETURN_NOT_INVALID");
        }

        if (returnRequest.getPickupDeadline() != null
                && Instant.now().isAfter(returnRequest.getPickupDeadline())) {
            throw new ConflictException("Đã quá hạn nhận lại hàng trả.", "RETURN_PICKUP_EXPIRED");
        }

        returnRequest.setInvalidReturnChoice(request.getChoice());
        returnRequestRepository.save(returnRequest);

        return toResponseWithPhotos(returnRequest);
    }

    @Override
    @Transactional
    public boolean applyGhnReturnStatus(String ghnReturnTrackingCode, String ghnStatus, String reason) {

        ReturnRequest returnRequest = returnRequestRepository
                .findByGhnReturnTrackingCode(ghnReturnTrackingCode)
                .orElse(null);

        if (returnRequest == null) {
            return false;
        }

        String status = ghnStatus == null ? "" : ghnStatus.toLowerCase(Locale.ROOT);

        if ("picked".equals(status) && returnRequest.getStatus() == ReturnRequestStatus.APPROVED) {
            returnRequest.setStatus(ReturnRequestStatus.SHIPPED_BACK);
            returnRequestRepository.save(returnRequest);

        } else if ("delivered".equals(status) && returnRequest.getStatus() == ReturnRequestStatus.SHIPPED_BACK) {
            // Vận đơn trả đã về tới kho - chuyển sang chờ Moderator kiểm hàng
            returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
            returnRequestRepository.save(returnRequest);

        } else if (LOST_GHN_STATUSES.contains(status)
                && OPEN_STATUSES.contains(returnRequest.getStatus())) {
            // F-136: hàng trả thất lạc - người mua không phải chịu, hoàn 100%
            returnRequest.setStatus(ReturnRequestStatus.LOST);
            returnRequest.setResolutionNote(StringUtils.hasText(reason) ? reason : status);
            returnRequest.setResolvedAt(Instant.now());
            returnRequestRepository.save(returnRequest);

            orderService.refundOrderForReturn(
                    returnRequest.getOrder().getId(),
                    "Hoàn tiền do hàng trả thất lạc - đơn " + returnRequest.getOrder().getOrderNo());

            log.info(">>>> Hang tra that lac, da hoan tien: returnRequestId={}, ghnStatus={}",
                    returnRequest.getId(), status);
        }

        return true;
    }

    // Vận đơn chiều ngược: lấy hàng tại địa chỉ người mua, giao về kho đã bán món hàng
    private GhnCreateOrderRequest buildReturnShipmentRequest(ReturnRequest returnRequest, List<OrderItem> items) {
        Order order = returnRequest.getOrder();
        Warehouse warehouse = order.getWarehouse();

        List<GhnOrderItem> ghnItems = items.stream()
                .map(oi -> GhnOrderItem.builder()
                        .name(oi.getProduct().getName())
                        .code(oi.getProduct().getId())
                        .quantity(1)
                        .price(oi.getPriceAtOrder().intValue())
                        .build())
                .toList();

        return GhnCreateOrderRequest.builder()
                // UUID của return request, không phải orderNo: cùng lý do với OrderServiceImpl
                // (orderNo đếm theo DB local, có thể trùng lại số cũ sau reset/seed còn lịch sử
                // client_order_code bên GHN sandbox thì không bao giờ mất).
                .clientOrderCode("RT-" + returnRequest.getId())
                .fromName(order.getShippingRecipientName())
                .fromPhone(order.getShippingPhone())
                .fromAddress(order.getShippingAddressDetail())
                .fromWardName(order.getShippingWardName())
                .fromDistrictName(Boolean.TRUE.equals(order.getShippingIsNewAddress())
                        ? "" : order.getShippingDistrictName())
                .fromProvinceName(order.getShippingProvinceName())
                .isNewFromAddress(Boolean.TRUE.equals(order.getShippingIsNewAddress()))
                .toName(warehouse.getName())
                .toPhone(warehouse.getPhone())
                .toAddress(warehouse.getAddress())
                .toWardName(warehouse.getWardName())
                .toDistrictName(Boolean.TRUE.equals(warehouse.getIsNewAddress())
                        ? "" : warehouse.getDistrictName())
                .toProvinceName(warehouse.getProvinceName())
                .isNewToAddress(Boolean.TRUE.equals(warehouse.getIsNewAddress()))
                .weight(defaultPackageWeightGrams)
                .length(defaultPackageLengthCm)
                .width(defaultPackageWidthCm)
                .height(defaultPackageHeightCm)
                .serviceTypeId(2)
                .paymentTypeId(1)
                .requiredNote("CHOXEMHANGKHONGTHU")
                .codAmount(0)
                .orderValue(order.getSubtotal().intValue())
                .items(ghnItems)
                .build();
    }

    private ReturnRequest findOrThrow(UUID id) {
        return returnRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu trả hàng."));
    }

    private ReturnRequestStatus parseStatus(String status) {
        try {
            return ReturnRequestStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("status không hợp lệ.");
        }
    }

    private ReturnRequestResponse toResponseWithPhotos(ReturnRequest returnRequest) {
        List<String> photoUrls = new ArrayList<>();
        returnRequestPhotoRepository.findAllByReturnRequest_IdOrderByIdAsc(returnRequest.getId())
                .forEach(p -> photoUrls.add(p.getImageUrl()));

        return toResponse(returnRequest, photoUrls);
    }

    private ReturnRequestResponse toResponse(ReturnRequest returnRequest, List<String> photoUrls) {
        Order order = returnRequest.getOrder();

        return ReturnRequestResponse.builder()
                .id(returnRequest.getId().toString())
                .orderId(order.getId().toString())
                .orderNo(order.getOrderNo())
                .status(returnRequest.getStatus().name())
                .reason(returnRequest.getReason())
                .photoUrls(photoUrls)
                .ghnReturnTrackingCode(returnRequest.getGhnReturnTrackingCode())
                .handoverDeadline(returnRequest.getHandoverDeadline())
                .invalidReturnChoice(returnRequest.getInvalidReturnChoice() != null
                        ? returnRequest.getInvalidReturnChoice().name() : null)
                .pickupDeadline(returnRequest.getPickupDeadline())
                .resolutionNote(returnRequest.getResolutionNote())
                .refundAmount(order.getRefundAmount())
                .createdAt(returnRequest.getCreatedAt())
                .resolvedAt(returnRequest.getResolvedAt())
                .build();
    }
}
