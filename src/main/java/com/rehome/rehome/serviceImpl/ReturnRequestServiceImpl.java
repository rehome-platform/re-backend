package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnApi;
import com.rehome.rehome.dto.request.CreateReturnRequestRequest;
import com.rehome.rehome.dto.request.GhnCreateOrderRequest;
import com.rehome.rehome.dto.request.GhnOrderItem;
import com.rehome.rehome.dto.request.InvalidReturnChoiceRequest;
import com.rehome.rehome.dto.request.MarkLostRequest;
import com.rehome.rehome.dto.request.RejectReturnRequestRequest;
import com.rehome.rehome.dto.request.ResolveReturnRequestRequest;
import com.rehome.rehome.dto.request.StartInspectionRequest;
import com.rehome.rehome.dto.response.GhnCreateOrderResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.ReturnRequestResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.enums.ReturnedItemAction;
import com.rehome.rehome.entity.marketplace.DegradedItemCase;
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
import com.rehome.rehome.repository.DegradedItemCaseRepository;
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

import java.math.BigDecimal;
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
    private final DegradedItemCaseRepository degradedItemCaseRepository;
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

        // Buyer được trả hàng trong 3 ngày kể từ lúc GHN báo giao, kể cả khi đã bấm "Đã nhận
        // hàng" (đơn COMPLETED) - vì payout người ký gửi giờ giữ lại tới hết hạn đó, không trả
        // ngay lúc confirm-receipt nữa.
        boolean returnable = order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.COMPLETED;
        if (!returnable || order.getDeliveredAt() == null) {
            throw new ConflictException(
                    "Chỉ gửi yêu cầu trả hàng cho đơn đã giao.",
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

        payoutIfReturnWindowPassed(returnRequest.getOrder(),
                "Yêu cầu trả hàng bị từ chối sau hạn trả hàng - đơn " + returnRequest.getOrder().getOrderNo());

        return toResponseWithPhotos(returnRequest);
    }

    // Yêu cầu trả hàng bị từ chối/hủy mà lúc đó đã quá hạn 3 ngày (autoCompleteDeadline) thì trả
    // tiền ngay, khỏi chờ job quét định kỳ. Còn trong hạn thì để OrderAutoCompleteServiceImpl xử lý.
    private void payoutIfReturnWindowPassed(Order order, String note) {
        if (order.getAutoCompleteDeadline() != null && Instant.now().isAfter(order.getAutoCompleteDeadline())) {
            orderService.payoutOrder(order.getId(), note);
        }
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

        // Quá handoverDeadline mà job F-069 chưa kịp quét tới -> hủy ngay tại đây, tránh
        // cho bàn giao trễ vẫn được chấp nhận trong lúc chờ tới lượt quét định kỳ.
        if (returnRequest.getHandoverDeadline() != null
                && Instant.now().isAfter(returnRequest.getHandoverDeadline())) {
            returnRequest.setStatus(ReturnRequestStatus.CANCELLED);
            returnRequest.setResolutionNote("Quá hạn bàn giao hàng trả cho GHN, yêu cầu bị hủy tự động.");
            returnRequest.setResolvedAt(Instant.now());
            returnRequestRepository.save(returnRequest);

            payoutIfReturnWindowPassed(returnRequest.getOrder(),
                    "Yêu cầu trả hàng quá hạn bàn giao sau hạn trả hàng - đơn "
                            + returnRequest.getOrder().getOrderNo());

            throw new ConflictException("Yêu cầu trả hàng đã quá hạn bàn giao.", "RETURN_HANDOVER_EXPIRED");
        }

        returnRequest.setStatus(ReturnRequestStatus.SHIPPED_BACK);
        returnRequestRepository.save(returnRequest);

        return toResponseWithPhotos(returnRequest);
    }

    // API 85: Moderator nhận hàng về kho, ghi nhận đúng sản phẩm gốc/xuống cấp/nghi hàng giả
    // không, kèm conditionNote bắt buộc; đồng thời chuyển sang INSPECTING nếu chưa.
    @Override
    @Transactional
    public ReturnRequestResponse startInspection(UUID id, StartInspectionRequest request) {

        if (request == null || !StringUtils.hasText(request.getConditionNote())) {
            throw new BadRequestException("conditionNote là bắt buộc.");
        }

        ReturnRequest returnRequest = findOrThrow(id);

        if (returnRequest.getStatus() != ReturnRequestStatus.INSPECTING
                && returnRequest.getStatus() != ReturnRequestStatus.SHIPPED_BACK) {
            throw new ConflictException("Hàng trả chưa được bàn giao cho GHN.", "RETURN_NOT_SHIPPED_BACK");
        }

        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
        returnRequest.setMatchesOriginal(request.getMatchesOriginal());
        returnRequest.setDegraded(request.getDegraded());
        returnRequest.setCounterfeitSuspected(Boolean.TRUE.equals(request.getCounterfeitSuspected()));
        returnRequest.setConditionNote(request.getConditionNote());
        returnRequestRepository.save(returnRequest);

        return toResponseWithPhotos(returnRequest);
    }

    // API 88 (F-133): VALID -> hoàn 100% cho người mua, kèm action quyết định hướng xử lý món
    // hàng (RELIST/RETURN_TO_SELLER/DISCARD); INVALID -> không hoàn tiền, đơn vẫn tính bán, Member
    // có pickupDeadline ngày để chọn cách nhận lại hàng (F-070) - action không áp dụng cho INVALID.
    // Hàng bị đánh dấu degraded lúc kiểm (API 85) thì không dùng action - mở DegradedItemCase
    // (F-036) để Member bán tự quyết định, "không đăng bán lại" (docs/OVERVIEW.md).
    @Override
    @Transactional
    public ReturnRequestResponse resolve(Account moderator, UUID id, ResolveReturnRequestRequest request) {

        if (request == null || !StringUtils.hasText(request.getResolution())) {
            throw new BadRequestException("resolution và action là bắt buộc.");
        }

        ReturnRequestStatus resolution = parseResolution(request.getResolution());

        ReturnRequest returnRequest = findOrThrow(id);

        if (returnRequest.getStatus() != ReturnRequestStatus.INSPECTING) {
            throw new ConflictException("Chưa kiểm tra hàng trả về.", "RETURN_NOT_INSPECTING");
        }

        boolean degraded = Boolean.TRUE.equals(returnRequest.getDegraded());

        ReturnedItemAction action = null;
        if (resolution == ReturnRequestStatus.VALID && !degraded) {
            if (!StringUtils.hasText(request.getAction())) {
                throw new BadRequestException("resolution và action là bắt buộc.");
            }
            action = parseItemAction(request.getAction());
        }

        Instant now = Instant.now();
        returnRequest.setResolutionNote(request.getResolutionNote());

        if (resolution == ReturnRequestStatus.VALID) {
            returnRequest.setStatus(ReturnRequestStatus.VALID);
            returnRequest.setItemAction(action);
            returnRequest.setResolvedAt(now);
            returnRequestRepository.save(returnRequest);

            orderService.refundOrderForReturn(
                    returnRequest.getOrder().getId(),
                    "Hoàn tiền trả hàng hợp lệ - đơn " + returnRequest.getOrder().getOrderNo());

            if (degraded) {
                openDegradedItemCase(moderator, returnRequest);
            } else {
                orderService.applyReturnedItemAction(returnRequest.getOrder().getId(), action);
            }
        } else {
            // Chưa đóng (resolvedAt để trống): còn chờ Member nhận lại hàng, hết hạn thì F-071 đóng hộ.
            // Trả hàng không hợp lệ -> người ký gửi vẫn được trả tiền, và trả ngay lúc kết luận
            // (không chờ hết hạn 3 ngày, không chờ Member chọn cách nhận lại hàng)
            returnRequest.setStatus(ReturnRequestStatus.INVALID);
            returnRequest.setPickupDeadline(now.plus(pickupDeadlineDays, ChronoUnit.DAYS));
            returnRequestRepository.save(returnRequest);

            orderService.payoutOrder(
                    returnRequest.getOrder().getId(),
                    "Thanh toán đơn hàng - hàng trả bị kết luận không hợp lệ, đơn "
                            + returnRequest.getOrder().getOrderNo());
        }

        return toResponseWithPhotos(returnRequest);
    }

    private ReturnRequestStatus parseResolution(String resolution) {
        ReturnRequestStatus parsed;
        try {
            parsed = ReturnRequestStatus.valueOf(resolution.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("resolution phải là VALID hoặc INVALID.");
        }
        if (parsed != ReturnRequestStatus.VALID && parsed != ReturnRequestStatus.INVALID) {
            throw new BadRequestException("resolution phải là VALID hoặc INVALID.");
        }
        return parsed;
    }

    private ReturnedItemAction parseItemAction(String action) {
        try {
            return ReturnedItemAction.valueOf(action.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("action phải là RELIST, RETURN_TO_SELLER hoặc DISCARD.");
        }
    }

    // F-036: snapshot mức đền đủ (giá bán - hoa hồng áp dụng lúc đặt đơn, KHÔNG trừ thuế - khác
    // payout thường) ngay lúc mở ca; Member tự chọn FULL_COMPENSATION hay PARTIAL_WITH_ITEM_BACK
    // sau đó ở API 95, choice/paidAmount để trống tới lúc đó.
    private void openDegradedItemCase(Account moderator, ReturnRequest returnRequest) {
        OrderItem item = orderItemRepository
                .findAllByOrder_IdWithProductOrderByIdAsc(returnRequest.getOrder().getId())
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Đơn không có món hàng."));

        BigDecimal baseCompensation = item.getPriceAtOrder()
                .subtract(item.getPriceAtOrder().multiply(item.getCommissionRateApplied()));

        DegradedItemCase degradedCase = DegradedItemCase.builder()
                .product(item.getProduct())
                .returnRequest(returnRequest)
                .compensationAmount(baseCompensation)
                .moderator(moderator)
                .build();

        degradedItemCaseRepository.save(degradedCase);
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
            markLost(returnRequest, StringUtils.hasText(reason) ? reason : status);

            log.info(">>>> Hang tra that lac, da hoan tien: returnRequestId={}, ghnStatus={}",
                    returnRequest.getId(), status);
        }

        return true;
    }

    // API 89 (F-136): Moderator tự tay đánh dấu thất lạc khi webhook GHN không về kịp hoặc không về
    @Override
    @Transactional
    public ReturnRequestResponse markLost(UUID id, MarkLostRequest request) {

        if (request == null || !StringUtils.hasText(request.getNote())) {
            throw new BadRequestException("note là bắt buộc.");
        }

        ReturnRequest returnRequest = findOrThrow(id);

        if (returnRequest.getStatus() == ReturnRequestStatus.LOST) {
            return toResponseWithPhotos(returnRequest);
        }

        if (!OPEN_STATUSES.contains(returnRequest.getStatus())) {
            throw new ConflictException("Yêu cầu trả hàng đã được xử lý xong.", "RETURN_ALREADY_RESOLVED");
        }

        markLost(returnRequest, request.getNote());

        return toResponseWithPhotos(returnRequest);
    }

    // F-136: hàng trả thất lạc trên đường về kho - rủi ro thuộc nền tảng/vận chuyển, không phải
    // người mua, nên hoàn 100% dù hàng chưa về kho và chưa qua kiểm. Dùng chung cho cả webhook
    // GHN tự báo lẫn Moderator tự tay đánh dấu (API 89).
    private void markLost(ReturnRequest returnRequest, String note) {
        returnRequest.setStatus(ReturnRequestStatus.LOST);
        returnRequest.setResolutionNote(note);
        returnRequest.setResolvedAt(Instant.now());
        returnRequestRepository.save(returnRequest);

        orderService.refundOrderForReturn(
                returnRequest.getOrder().getId(),
                "Hoàn tiền do hàng trả thất lạc - đơn " + returnRequest.getOrder().getOrderNo());
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
                .matchesOriginal(returnRequest.getMatchesOriginal())
                .degraded(returnRequest.getDegraded())
                .counterfeitSuspected(returnRequest.getCounterfeitSuspected())
                .conditionNote(returnRequest.getConditionNote())
                .itemAction(returnRequest.getItemAction() != null ? returnRequest.getItemAction().name() : null)
                .refundAmount(order.getRefundAmount())
                .createdAt(returnRequest.getCreatedAt())
                .resolvedAt(returnRequest.getResolvedAt())
                .build();
    }
}
