package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.util.NotificationFormatting;

import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.NotificationTargetType;

import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.entity.enums.WarehouseAuditGroup;
import com.rehome.rehome.client.GhnApi;
import com.rehome.rehome.client.GhnShippingFeeCalculator;
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
import com.rehome.rehome.dto.response.ShipBackQuoteResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.auth.MemberProfile;
import com.rehome.rehome.entity.enums.InvalidReturnChoice;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.entity.enums.ReturnPhotoSource;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.enums.ReturnedItemAction;
import com.rehome.rehome.entity.enums.ShipBackStatus;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.DegradedItemCase;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.entity.marketplace.ReturnRequestPhoto;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.exception.GhnException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.DegradedItemCaseRepository;
import com.rehome.rehome.repository.MemberProfileRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.OrderRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ReturnRequestPhotoRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.service.OrderService;
import com.rehome.rehome.service.ReturnRequestService;
import com.rehome.rehome.service.SystemConfigReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
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
    private final com.rehome.rehome.component.BusinessNotifications businessNotifications;

    private final AuditLogger auditLogger;

    @Value("${app.ghn.fallback-on-error:true}")
    private boolean ghnFallbackOnError = true;

    private static final List<ReturnRequestStatus> OPEN_STATUSES = List.of(
            ReturnRequestStatus.PENDING,
            ReturnRequestStatus.APPROVED,
            ReturnRequestStatus.SHIPPED_BACK,
            ReturnRequestStatus.INSPECTING);

    private static final List<String> LOST_GHN_STATUSES = List.of("lost", "damage", "scrap");

    private final com.rehome.rehome.service.ContentModerationService moderation;
    private final AccountRepository accountRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final ReturnRequestPhotoRepository returnRequestPhotoRepository;
    private final DegradedItemCaseRepository degradedItemCaseRepository;
    private final MemberProfileRepository memberProfileRepository;
    private final ProductPhotoRepository productPhotoRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final OrderService orderService;
    private final QueueService queueService;
    private final GhnApi ghnApi;
    private final GhnShippingFeeCalculator ghnShippingFeeCalculator;
    private final SystemConfigReader systemConfigReader;

    @Value("${app.return.ship-back-fee-deadline-minutes:30}")
    private int defaultShipBackFeeDeadlineMinutes = SystemConfigKeys.DEFAULT_RETURN_SHIP_BACK_FEE_DEADLINE_MINUTES;

    @Value("${app.return.ship-back-ship-within-days:2}")
    private int defaultShipBackShipWithinDays = SystemConfigKeys.DEFAULT_RETURN_SHIP_BACK_SHIP_WITHIN_DAYS;

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

    private int handoverDeadlineDays() {
        return systemConfigReader.getInt(SystemConfigKeys.RETURN_HANDOVER_CARRIER_DAYS,
                SystemConfigKeys.DEFAULT_RETURN_HANDOVER_CARRIER_DAYS);
    }

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

        boolean returnable = order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.COMPLETED;
        if (!returnable || order.getDeliveredAt() == null) {
            throw new ConflictException(
                    "Chỉ gửi yêu cầu trả hàng cho đơn đã giao.",
                    "ORDER_NOT_RETURNABLE");
        }

        Instant returnWindowEnd = order.getReturnDeadline() != null
                ? order.getReturnDeadline()
                : order.getDeliveredAt().plus(systemConfigReader.getInt(
                SystemConfigKeys.RETURN_REQUEST_WINDOW_DAYS,
                SystemConfigKeys.DEFAULT_RETURN_REQUEST_WINDOW_DAYS), ChronoUnit.DAYS);
        if (Instant.now().isAfter(returnWindowEnd)) {
            throw new ConflictException(
                    "Đã quá hạn " + ChronoUnit.DAYS.between(order.getDeliveredAt(), returnWindowEnd)
                            + " ngày gửi yêu cầu trả hàng.",
                    "RETURN_WINDOW_EXPIRED");
        }

        if (returnRequestRepository.existsByOrder_IdAndStatusIn(orderId, OPEN_STATUSES)) {
            throw new ConflictException("Đơn đã có yêu cầu trả hàng đang xử lý.", "RETURN_REQUEST_EXISTS");
        }

        moderation.check(buyer.getId(), "RETURN_REQUEST", request.getReason());
        ReturnRequest returnRequest = ReturnRequest.builder()
                .order(order)
                .reason(request.getReason())
                .status(ReturnRequestStatus.PENDING)
                .handoverDeadline(Instant.now().plus(handoverDeadlineDays(), ChronoUnit.DAYS))
                .build();
        returnRequestRepository.save(returnRequest);
        queueService.enqueue(QueueType.RETURN, returnRequest.getId().toString(), order.getWarehouse());

        List<ReturnRequestPhoto> photos = request.getPhotoUrls().stream()
                .map(url -> ReturnRequestPhoto.builder()
                        .returnRequest(returnRequest)
                        .imageUrl(url)
                        .build())
                .toList();
        returnRequestPhotoRepository.saveAll(photos);

        auditLogger.logOperation(buyer, "CREATE_RETURN", "RETURN_REQUEST", returnRequest.getId(), returnRequest.getStatus().name());
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
    public PageResponse<ReturnRequestResponse> getQueue(Account moderator, String status, int page, int size) {

        Warehouse warehouse = requireWarehouse(moderator);

        List<ReturnRequestStatus> statuses = StringUtils.hasText(status)
                ? List.of(parseStatus(status))
                : OPEN_STATUSES;

        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<ReturnRequest> result = returnRequestRepository
                .findByOrder_Warehouse_IdAndStatusInOrderByCreatedAtAsc(warehouse.getId(), statuses, pageable);

        return PageResponse.from(result.map(this::toResponseWithPhotos));
    }

    private Warehouse requireWarehouse(Account moderator) {
        if (moderator.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);
        }
        return moderator.getWarehouse();
    }

    @Override
    public ReturnRequestResponse getDetailForModerator(UUID id) {
        return toResponseWithPhotos(findOrThrow(id));
    }

    @Override
    @Transactional
    public ReturnRequestResponse approve(UUID id) {

        ReturnRequest returnRequest = returnRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Return request not found"));

        if (StringUtils.hasText(returnRequest.getGhnReturnTrackingCode())) {
            return toResponseWithPhotos(returnRequest);
        }

        if (returnRequest.getStatus() != ReturnRequestStatus.PENDING) {
            throw new ConflictException("Yêu cầu trả hàng không ở trạng thái chờ duyệt.", "RETURN_NOT_PENDING");
        }

        Order order = returnRequest.getOrder();
        List<OrderItem> items = orderItemRepository.findAllByOrder_IdWithProductOrderByIdAsc(order.getId());

        GhnCreateOrderRequest shipment = buildReturnShipmentRequest(returnRequest, items);
        GhnCreateOrderResponse ghnResponse = null;
        try {
            ghnResponse = ghnApi.createOrder(ghnToken, ghnShopId, shipment);
        } catch (Exception ex) {
            if (!ghnFallbackOnError) {
                throw new GhnException("GHN_ERROR: cannot create return shipment.");
            }
            log.warn("GHN return shipment failed for {}, using local fallback: {}", id, ex.getMessage());
        }
        boolean fallback = ghnResponse == null || ghnResponse.getData() == null
                || !StringUtils.hasText(ghnResponse.getData().getOrderCode());
        if (fallback && !ghnFallbackOnError) {
            throw new GhnException("GHN_ERROR: missing return order code.");
        }
        returnRequest.setGhnFallbackUsed(fallback);
        returnRequest.setGhnReturnTrackingCode(fallback
                ? "LOCAL-RT-" + id.toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT)
                : ghnResponse.getData().getOrderCode());
        returnRequest.setStatus(ReturnRequestStatus.APPROVED);
        returnRequest.setHandoverDeadline(Instant.now().plus(handoverDeadlineDays(), ChronoUnit.DAYS));
        returnRequestRepository.save(returnRequest);

        businessNotifications.returned(returnRequest, NotificationType.ORDER_UPDATE, "Yêu cầu trả hàng đã được duyệt",
                "mã thu hồi " + returnRequest.getGhnReturnTrackingCode() + ". Hạn bàn giao: " + NotificationFormatting.deadline(returnRequest.getHandoverDeadline()) + ".");
        auditLogger.logCurrentOperation("APPROVE_RETURN", "RETURN_REQUEST", id, returnRequest.getStatus().name());
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
        queueService.dequeue(QueueType.RETURN, returnRequest.getId().toString());
        returnRequest.setResolutionNote(request.getReason());
        returnRequest.setResolvedAt(Instant.now());
        returnRequestRepository.save(returnRequest);

        payoutIfReturnWindowPassed(returnRequest.getOrder(),
                "Yêu cầu trả hàng bị từ chối sau hạn trả hàng - đơn " + returnRequest.getOrder().getOrderNo());

        businessNotifications.returned(returnRequest, NotificationType.ORDER_UPDATE, "Yêu cầu trả hàng bị từ chối",
                "lý do: " + returnRequest.getResolutionNote());
        auditLogger.logCurrentOperation("REJECT_RETURN", "RETURN_REQUEST", id, returnRequest.getStatus().name());
        return toResponseWithPhotos(returnRequest);
    }

    private void payoutIfReturnWindowPassed(Order order, String note) {
        if (order.getAutoCompleteDeadline() != null && Instant.now().isAfter(order.getAutoCompleteDeadline())) {
            orderService.payoutOrder(order.getId(), note);
        }
    }

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

        List<String> evidenceImageUrls = request.getEvidenceImageUrls();
        if (evidenceImageUrls == null || evidenceImageUrls.size() < 2 || evidenceImageUrls.size() > 5) {
            throw new BadRequestException("Phải có từ 2 đến 5 ảnh bằng chứng kiểm hàng.");
        }

        returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
        returnRequest.setMatchesOriginal(request.getMatchesOriginal());
        returnRequest.setDegraded(request.getDegraded());
        returnRequest.setCounterfeitSuspected(Boolean.TRUE.equals(request.getCounterfeitSuspected()));
        returnRequest.setConditionNote(request.getConditionNote());
        returnRequestRepository.save(returnRequest);

        List<ReturnRequestPhoto> evidencePhotos = evidenceImageUrls.stream()
                .map(url -> ReturnRequestPhoto.builder()
                        .returnRequest(returnRequest)
                        .imageUrl(url)
                        .source(ReturnPhotoSource.MODERATOR)
                        .build())
                .toList();
        returnRequestPhotoRepository.saveAll(evidencePhotos);

        auditLogger.logCurrentOperation("START_RETURN_INSPECTION", "RETURN_REQUEST", id, returnRequest.getStatus().name());
        return toResponseWithPhotos(returnRequest);
    }

    @Override
    @Transactional
    public ReturnRequestResponse resolve(Account moderator, UUID id, ResolveReturnRequestRequest request) {

        if (request == null || !StringUtils.hasText(request.getResolution())) {
            throw new BadRequestException("resolution và action là bắt buộc.");
        }

        ReturnRequestStatus resolution = parseResolution(request.getResolution());

        ReturnRequest returnRequest = returnRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Return request not found"));

        if (returnRequest.getStatus() != ReturnRequestStatus.INSPECTING) {
            throw new ConflictException("Chưa kiểm tra hàng trả về.", "RETURN_NOT_INSPECTING");
        }

        boolean degraded = Boolean.TRUE.equals(returnRequest.getDegraded());

        ReturnedItemAction action = null;
        if (resolution == ReturnRequestStatus.VALID && !degraded) {
            if (StringUtils.hasText(request.getAction())) {
                action = parseItemAction(request.getAction());
            }
        }

        Instant now = Instant.now();
        returnRequest.setResolutionNote(request.getResolutionNote());
        queueService.dequeue(QueueType.RETURN, returnRequest.getId().toString());

        if (resolution == ReturnRequestStatus.VALID) {
            returnRequest.setStatus(ReturnRequestStatus.VALID);
            returnRequest.setItemAction(action);
            returnRequest.setResolvedAt(now);
            returnRequestRepository.save(returnRequest);

            orderService.refundOrderForReturn(
                    returnRequest.getOrder().getId(),
                    "Hoàn tiền trả hàng hợp lệ - đơn " + returnRequest.getOrder().getOrderNo());

            businessNotifications.returned(returnRequest, NotificationType.WALLET_EVENT, "Đã hoàn tiền vào ví",
                    "đã hoàn " + returnRequest.getOrder().getTotalAmount() + "đ vào ví của bạn.");
            if (degraded) {
                openDegradedItemCase(moderator, returnRequest);
            } else if (action != null) {
                orderService.applyReturnedItemAction(returnRequest.getOrder().getId(), action);
            }
        } else {
            returnRequest.setStatus(ReturnRequestStatus.INVALID);
            returnRequest.setPickupDeadline(now.plus(
                    systemConfigReader.getInt(SystemConfigKeys.COUNTER_PICKUP_DAYS,
                            SystemConfigKeys.DEFAULT_COUNTER_PICKUP_DAYS), ChronoUnit.DAYS));
            returnRequestRepository.save(returnRequest);

            orderService.payoutOrder(
                    returnRequest.getOrder().getId(),
                    "Thanh toán đơn hàng - hàng trả bị kết luận không hợp lệ, đơn "
                            + returnRequest.getOrder().getOrderNo());
        }

        if (resolution == ReturnRequestStatus.INVALID) {
            businessNotifications.returned(returnRequest, NotificationType.ORDER_UPDATE, "Kết luận trả hàng không hợp lệ",
                    "lý do: " + returnRequest.getResolutionNote() + ". Vui lòng chọn cách nhận lại trước hạn " + NotificationFormatting.deadline(returnRequest.getPickupDeadline()) + ".");
        }
        auditLogger.logOperation(moderator, "RESOLVE_RETURN", "RETURN_REQUEST", id, returnRequest.getStatus().name());
        return toResponseWithPhotos(returnRequest);
    }

    @Override
    @Transactional
    public ReturnRequestResponse approve(Account moderator, UUID id) {
        ReturnRequest request = returnRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Return request not found"));
        requireReturnWarehouse(moderator, request);
        return approve(id);
    }

    private void requireReturnWarehouse(Account moderator, ReturnRequest request) {
        if (moderator == null || moderator.getRole() != com.rehome.rehome.entity.enums.Role.MODERATOR
                || moderator.getWarehouse() == null
                || !moderator.getWarehouse().getId().equals(request.getOrder().getWarehouse().getId())) {
            throw new ForbiddenException("Only the assigned warehouse moderator can handle this return.");
        }
    }

    @Override
    @Transactional
    public ReturnRequestResponse applyItemAction(Account moderator, UUID id,
            com.rehome.rehome.dto.request.ApplyReturnItemActionRequest request) {
        if (request == null || !StringUtils.hasText(request.action())) {
            throw new BadRequestException("action is required.");
        }
        ReturnedItemAction action = parseItemAction(request.action());
        ReturnRequest item = returnRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Return request not found"));
        requireReturnWarehouse(moderator, item);
        if (item.getStatus() != ReturnRequestStatus.VALID || item.getItemAction() != null) {
            throw new ConflictException("Return must be VALID and have no item action.", "RETURN_ITEM_ACTION_NOT_ALLOWED");
        }
        if (Boolean.TRUE.equals(item.getDegraded())) {
            throw new ConflictException("Degraded items use the compensation workflow.", "RETURN_ITEM_DEGRADED");
        }
        orderService.applyReturnedItemAction(item.getOrder().getId(), action);
        item.setItemAction(action);
        returnRequestRepository.save(item);
        auditLogger.logWarehouseOp(moderator, item.getOrder().getWarehouse(), WarehouseAuditGroup.INVENTORY,
                "APPLY_RETURN_ITEM_ACTION", "RETURN_REQUEST", id.toString(), action.name(), item.getOrder().getOrderNo());
        return toResponseWithPhotos(item);
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

    private void openDegradedItemCase(Account moderator, ReturnRequest returnRequest) {
        OrderItem item = orderItemRepository
                .findAllByOrder_IdWithProductOrderByIdAsc(returnRequest.getOrder().getId())
                .stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("Đơn không có món hàng."));

        BigDecimal baseCompensation = item.getPriceAtOrder()
                .subtract(item.getPriceAtOrder().multiply(item.getCommissionRateApplied()));

        int decisionDeadlineDays = systemConfigReader.getInt(
                SystemConfigKeys.DEGRADED_ITEM_DECISION_DAYS, SystemConfigKeys.DEFAULT_DEGRADED_ITEM_DECISION_DAYS);

        DegradedItemCase degradedCase = DegradedItemCase.builder()
                .product(item.getProduct())
                .returnRequest(returnRequest)
                .compensationAmount(baseCompensation)
                .moderator(moderator)
                .decisionDeadline(Instant.now().plus(Duration.ofDays(decisionDeadlineDays)))
                .build();

        degradedItemCaseRepository.save(degradedCase);
        businessNotifications.send(item.getProduct().getMember(), NotificationType.TERM_EXPIRY, "Cần chọn phương án cho món xuống cấp",
                "Món " + item.getProduct().getName() + ": vui lòng chọn phương án trước hạn "
                        + NotificationFormatting.deadline(degradedCase.getDecisionDeadline()) + ".", NotificationTargetType.DEGRADED_ITEM_CASE, degradedCase.getId());

        auditLogger.logWarehouseOp(moderator, item.getProduct().getWarehouse(), WarehouseAuditGroup.INVENTORY,
                "OPEN_DEGRADED_CASE", "DEGRADED_ITEM_CASE", degradedCase.getId().toString(), "OPENED",
                item.getProduct().getId());
    }

    private int shipBackFeeDeadlineMinutes() {
        int configured = systemConfigReader.getInt(
                SystemConfigKeys.RETURN_SHIP_BACK_FEE_DEADLINE_MINUTES, defaultShipBackFeeDeadlineMinutes);
        return configured > 0 ? configured : defaultShipBackFeeDeadlineMinutes;
    }

    private int shipBackShipWithinDays() {
        int configured = systemConfigReader.getInt(
                SystemConfigKeys.RETURN_SHIP_BACK_SHIP_WITHIN_DAYS, defaultShipBackShipWithinDays);
        return configured > 0 ? configured : defaultShipBackShipWithinDays;
    }

    private int counterPickupDays() {
        int configured = systemConfigReader.getInt(
                SystemConfigKeys.COUNTER_PICKUP_DAYS, SystemConfigKeys.DEFAULT_COUNTER_PICKUP_DAYS);
        return configured > 0 ? configured : SystemConfigKeys.DEFAULT_COUNTER_PICKUP_DAYS;
    }

    @Override
    public ShipBackQuoteResponse getShipBackQuote(String email, UUID id, UUID addressId) {

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        ReturnRequest returnRequest = findOrThrow(id);

        if (!returnRequest.getOrder().getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenException("Bạn không có quyền xem yêu cầu trả hàng này.");
        }

        assertInvalidChoiceChangeable(returnRequest);
        if (returnRequest.getShipBackStatus() == ShipBackStatus.FAILED) {
            throw new ConflictException(
                    "Đã gửi lại không thành công một lần, vui lòng đến kho nhận hàng.",
                    "RETURN_SHIP_BACK_NOT_ALLOWED");
        }

        UUID effectiveAddressId = addressId;
        if (effectiveAddressId == null && returnRequest.getOrder().getShippingAddress() != null) {
            effectiveAddressId = returnRequest.getOrder().getShippingAddress().getId();
        }
        if (effectiveAddressId == null) {
            throw new BadRequestException("addressId là bắt buộc.");
        }

        ShippingAddress address = requireBuyerAddress(buyer, effectiveAddressId);
        Warehouse warehouse = returnRequest.getOrder().getWarehouse();
        BigDecimal fee = ghnShippingFeeCalculator.calculate(warehouse, address);

        return ShipBackQuoteResponse.builder()
                .fee(fee)
                .addressId(address.getId().toString())
                .recipientName(address.getRecipientName())
                .phone(address.getPhone())
                .addressDetail(address.getAddressDetail())
                .wardName(address.getWardName())
                .provinceName(address.getProvinceName())
                .warehouseName(warehouse != null ? warehouse.getName() : null)
                .feeDeadlineMinutes(shipBackFeeDeadlineMinutes())
                .build();
    }

    @Override
    @Transactional
    public ReturnRequestResponse chooseInvalidReturn(String email, UUID id, InvalidReturnChoiceRequest request) {

        if (request == null || request.getChoice() == null) {
            throw new BadRequestException("choice phải là SHIP_BACK hoặc WAREHOUSE_PICKUP.");
        }

        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        ReturnRequest returnRequest = returnRequestRepository.findByIdForUpdate(id)
                .orElseGet(() -> findOrThrow(id));

        if (!returnRequest.getOrder().getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenException("Bạn không có quyền cập nhật yêu cầu trả hàng này.");
        }

        assertInvalidChoiceChangeable(returnRequest);

        if (request.getChoice() == InvalidReturnChoice.SHIP_BACK) {
            if (returnRequest.getShipBackStatus() == ShipBackStatus.FAILED) {
                throw new ConflictException(
                        "Đã gửi lại không thành công một lần, vui lòng đến kho nhận hàng.",
                        "RETURN_SHIP_BACK_NOT_ALLOWED");
            }
            if (request.getAddressId() == null) {
                throw new BadRequestException("addressId là bắt buộc khi chọn SHIP_BACK.");
            }
            ShippingAddress address = requireBuyerAddress(buyer, request.getAddressId());
            BigDecimal fee = ghnShippingFeeCalculator.calculate(returnRequest.getOrder().getWarehouse(), address);

            clearUnpaidShipBackFields(returnRequest);
            returnRequest.setInvalidReturnChoice(InvalidReturnChoice.SHIP_BACK);
            returnRequest.setShipBackStatus(ShipBackStatus.AWAITING_PAYMENT);
            returnRequest.setShipBackFee(fee);
            returnRequest.setShipBackFeeDeadline(
                    Instant.now().plus(shipBackFeeDeadlineMinutes(), ChronoUnit.MINUTES));
            returnRequest.setShipBackAddressId(address.getId());
            returnRequest.setShipBackRecipientName(address.getRecipientName());
            returnRequest.setShipBackPhone(address.getPhone());
            returnRequest.setShipBackAddressDetail(address.getAddressDetail());
            returnRequest.setShipBackWardName(address.getWardName());
            returnRequest.setShipBackProvinceName(address.getProvinceName());
        } else {
            if (returnRequest.getShipBackStatus() == ShipBackStatus.AWAITING_PAYMENT) {
                clearUnpaidShipBackFields(returnRequest);
            }
            returnRequest.setInvalidReturnChoice(InvalidReturnChoice.WAREHOUSE_PICKUP);
        }

        returnRequestRepository.save(returnRequest);

        return toResponseWithPhotos(returnRequest);
    }

    private void assertInvalidChoiceChangeable(ReturnRequest returnRequest) {
        if (returnRequest.getStatus() != ReturnRequestStatus.INVALID) {
            throw new ConflictException(
                    "Chỉ chọn cách nhận lại hàng khi yêu cầu bị kết luận không hợp lệ.",
                    "RETURN_NOT_INVALID");
        }
        ShipBackStatus shipBack = returnRequest.getShipBackStatus();
        if (shipBack == ShipBackStatus.PAID || shipBack == ShipBackStatus.SHIPPING) {
            throw new ConflictException(
                    "Đã trả phí gửi lại, không thể đổi lựa chọn.", "RETURN_SHIP_BACK_IN_PROGRESS");
        }
        if (shipBack == ShipBackStatus.DELIVERED || returnRequest.getPickedUpAt() != null) {
            throw new ConflictException("Người mua đã nhận lại hàng.", "RETURN_ALREADY_RESOLVED");
        }
        if (returnRequest.getPickupDeadline() != null
                && Instant.now().isAfter(returnRequest.getPickupDeadline())) {
            throw new ConflictException("Đã quá hạn nhận lại hàng trả.", "RETURN_PICKUP_EXPIRED");
        }
        if (returnRequest.getResolvedAt() != null) {
            throw new ConflictException("Yêu cầu trả hàng đã được xử lý xong.", "RETURN_ALREADY_RESOLVED");
        }
    }

    private ShippingAddress requireBuyerAddress(Account buyer, UUID addressId) {
        return shippingAddressRepository.findById(addressId)
                .filter(a -> a.getMember().getId().equals(buyer.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy địa chỉ nhận hàng."));
    }

    static void clearUnpaidShipBackFields(ReturnRequest returnRequest) {
        returnRequest.setShipBackStatus(null);
        returnRequest.setShipBackFee(null);
        returnRequest.setShipBackFeeDeadline(null);
        returnRequest.setShipBackAddressId(null);
        returnRequest.setShipBackRecipientName(null);
        returnRequest.setShipBackPhone(null);
        returnRequest.setShipBackAddressDetail(null);
        returnRequest.setShipBackWardName(null);
        returnRequest.setShipBackProvinceName(null);
        returnRequest.setShipBackPaymentMethod(null);
        returnRequest.setShipBackVnpTxnRef(null);
        returnRequest.setShipBackVnpCreateDate(null);
    }

    @Override
    @Transactional
    public boolean applyGhnReturnStatus(String ghnReturnTrackingCode, String ghnStatus, String reason) {

        ReturnRequest returnRequest = returnRequestRepository
                .findByGhnReturnTrackingCode(ghnReturnTrackingCode)
                .orElse(null);

        if (returnRequest == null) {
            return applyShipBackGhnStatus(ghnReturnTrackingCode, ghnStatus);
        }

        String status = ghnStatus == null ? "" : ghnStatus.toLowerCase(Locale.ROOT);

        if ("picked".equals(status) && returnRequest.getStatus() == ReturnRequestStatus.APPROVED) {
            returnRequest.setStatus(ReturnRequestStatus.SHIPPED_BACK);
            returnRequestRepository.save(returnRequest);

        } else if ("delivered".equals(status) && returnRequest.getStatus() == ReturnRequestStatus.SHIPPED_BACK) {
            returnRequest.setStatus(ReturnRequestStatus.INSPECTING);
            returnRequestRepository.save(returnRequest);

        } else if (LOST_GHN_STATUSES.contains(status)
                && OPEN_STATUSES.contains(returnRequest.getStatus())) {
            markLost(returnRequest, StringUtils.hasText(reason) ? reason : status);

            log.info(">>>> Hang tra that lac, da hoan tien: returnRequestId={}, ghnStatus={}",
                    returnRequest.getId(), status);
        }

        return true;
    }

    private boolean applyShipBackGhnStatus(String trackingCode, String ghnStatus) {
        ReturnRequest returnRequest = returnRequestRepository
                .findByShipBackTrackingCode(trackingCode)
                .orElse(null);
        if (returnRequest == null) {
            return false;
        }

        if (returnRequest.getStatus() != ReturnRequestStatus.INVALID
                || returnRequest.getShipBackStatus() != ShipBackStatus.SHIPPING) {
            return true;
        }

        String status = ghnStatus == null ? "" : ghnStatus.trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();

        if ("delivered".equals(status)) {
            returnRequest.setShipBackStatus(ShipBackStatus.DELIVERED);
            returnRequest.setShipBackDeliveredAt(now);
            returnRequest.setResolvedAt(now);
            returnRequest.setResolutionNote("Đã gửi lại hàng cho người mua");
            returnRequestRepository.save(returnRequest);

            businessNotifications.returned(returnRequest, NotificationType.ORDER_UPDATE,
                    "Đã giao lại hàng trả",
                    "hàng trả đã được giao lại thành công · Mã GHN " + trackingCode + ".");

        } else if ("returned".equals(status) || "return".equals(status)) {
            returnRequest.setShipBackStatus(ShipBackStatus.FAILED);
            returnRequest.setShipBackFailedAt(now);
            returnRequest.setInvalidReturnChoice(InvalidReturnChoice.WAREHOUSE_PICKUP);
            returnRequest.setPickupDeadline(now.plus(counterPickupDays(), ChronoUnit.DAYS));
            returnRequestRepository.save(returnRequest);

            businessNotifications.returned(returnRequest, NotificationType.ORDER_UPDATE,
                    "Giao lại hàng trả không thành công",
                    "hàng đã hoàn về kho. Vui lòng đến kho nhận lại trước hạn "
                            + NotificationFormatting.deadline(returnRequest.getPickupDeadline()) + ".");
        }

        return true;
    }

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
        auditLogger.logCurrentOperation("MARK_RETURN_LOST", "RETURN_REQUEST", id, "LOST");

        return toResponseWithPhotos(returnRequest);
    }

    private void markLost(ReturnRequest returnRequest, String note) {
        returnRequest.setStatus(ReturnRequestStatus.LOST);
        returnRequest.setResolutionNote(note);
        returnRequest.setResolvedAt(Instant.now());
        returnRequestRepository.save(returnRequest);
        queueService.dequeue(QueueType.RETURN, returnRequest.getId().toString());

        orderService.refundOrderForReturn(
                returnRequest.getOrder().getId(),
                "Hoàn tiền do hàng trả thất lạc - đơn " + returnRequest.getOrder().getOrderNo());
        businessNotifications.returned(returnRequest, NotificationType.WALLET_EVENT, "Đã hoàn tiền vào ví",
                "đã hoàn " + returnRequest.getOrder().getTotalAmount() + "đ do hàng trả thất lạc.");
    }

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

        OrderItem item = orderItemRepository
                .findAllByOrder_IdWithProductOrderByIdAsc(order.getId())
                .stream().findFirst().orElse(null);

        String buyerName = memberProfileRepository.findByAccountId(order.getBuyer().getId())
                .map(MemberProfile::getFullName).orElse(null);
        String sellerName = item == null ? null
                : memberProfileRepository.findByAccountId(item.getProduct().getMember().getId())
                        .map(MemberProfile::getFullName).orElse(null);

        List<String> initialPhotoUrls = item == null ? List.of()
                : productPhotoRepository.findByProduct_IdOrderByDisplayOrderAscIdAsc(item.getProduct().getId())
                        .stream().map(ProductPhoto::getImageUrl).toList();

        List<ReturnRequestResponse.ReturnRequestHistoryItem> history =
                returnRequestRepository.findAllByOrder_IdAndIdNotOrderByCreatedAtDesc(
                                order.getId(), returnRequest.getId()).stream()
                        .map(r -> ReturnRequestResponse.ReturnRequestHistoryItem.builder()
                                .id(r.getId().toString())
                                .status(r.getStatus().name())
                                .degraded(r.getDegraded())
                                .createdAt(r.getCreatedAt())
                                .resolvedAt(r.getResolvedAt())
                                .build())
                        .toList();

        BigDecimal compensationAmount = null;
        BigDecimal halfCompensationAmount = null;
        String sku = null;
        String productName = null;
        String brandName = null;
        String size = null;
        String sellerCode = null;
        Short currentTerm = null;
        Integer daysIntoTerm = null;
        Integer termLengthDays = null;
        BigDecimal commissionRate = null;
        BigDecimal degradedCompensationRate = null;
        if (item != null) {
            Product product = item.getProduct();
            compensationAmount = item.getPriceAtOrder()
                    .subtract(item.getPriceAtOrder().multiply(item.getCommissionRateApplied()));
            ConsignmentReceipt receipt = product.getConsignmentReceipt();
            BigDecimal rate = receipt != null ? receipt.getDegradedCompensationRate() : null;
            if (rate != null) {
                halfCompensationAmount = compensationAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
            }

            sku = product.getId();
            productName = product.getName();
            brandName = product.getBrand() != null ? product.getBrand().getName() : null;
            size = product.getSize();
            sellerCode = memberProfileRepository.findByAccountId(product.getMember().getId())
                    .map(MemberProfile::getMemberCode).orElse(null);
            currentTerm = product.getCurrentTerm();
            daysIntoTerm = (int) Math.max(1, ChronoUnit.DAYS.between(product.getTermStartedAt(), Instant.now()));
            commissionRate = item.getCommissionRateApplied();
            if (receipt != null) {
                termLengthDays = receipt.getTermLengthDays();
                degradedCompensationRate = receipt.getDegradedCompensationRate();
            }
        }

        return ReturnRequestResponse.builder()
                .id(returnRequest.getId().toString())
                .orderId(order.getId().toString())
                .orderNo(order.getOrderNo())
                .status(returnRequest.getStatus().name())
                .reason(returnRequest.getReason())
                .photoUrls(photoUrls)
                .packingPhotoUrls(order.getPackingPhotoUrls() == null ? List.of() : List.copyOf(order.getPackingPhotoUrls()))
                .ghnFallbackUsed(Boolean.TRUE.equals(returnRequest.getGhnFallbackUsed()))
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
                .pickedUpAt(returnRequest.getPickedUpAt())
                .shipBackStatus(returnRequest.getShipBackStatus() != null
                        ? returnRequest.getShipBackStatus().name() : null)
                .shipBackFee(returnRequest.getShipBackFee())
                .shipBackFeeDeadline(returnRequest.getShipBackPaidAt() == null
                        ? returnRequest.getShipBackFeeDeadline() : null)
                .shipBackPaidAt(returnRequest.getShipBackPaidAt())
                .shipBackPaymentMethod(returnRequest.getShipBackPaymentMethod() != null
                        ? returnRequest.getShipBackPaymentMethod().name() : null)
                .shipBackShipDeadline(returnRequest.getShipBackPaidAt() != null
                        ? returnRequest.getShipBackPaidAt().plus(shipBackShipWithinDays(), ChronoUnit.DAYS) : null)
                .shipBackTrackingCode(returnRequest.getShipBackTrackingCode())
                .shipBackShippedAt(returnRequest.getShipBackShippedAt())
                .shipBackDeliveredAt(returnRequest.getShipBackDeliveredAt())
                .shipBackFailedAt(returnRequest.getShipBackFailedAt())
                .shipBackAddressId(returnRequest.getShipBackAddressId() != null
                        ? returnRequest.getShipBackAddressId().toString() : null)
                .shipBackRecipientName(returnRequest.getShipBackRecipientName())
                .shipBackPhone(returnRequest.getShipBackPhone())
                .shipBackAddressDetail(returnRequest.getShipBackAddressDetail())
                .shipBackWardName(returnRequest.getShipBackWardName())
                .shipBackProvinceName(returnRequest.getShipBackProvinceName())
                .buyerName(buyerName)
                .buyerPhone(order.getShippingPhone())
                .sellerName(sellerName)
                .initialConsignmentPhotoUrls(initialPhotoUrls)
                .returnHistory(history)
                .compensationAmount(compensationAmount)
                .halfCompensationAmount(halfCompensationAmount)
                .sku(sku)
                .productName(productName)
                .brandName(brandName)
                .size(size)
                .sellerCode(sellerCode)
                .currentTerm(currentTerm)
                .daysIntoTerm(daysIntoTerm)
                .termLengthDays(termLengthDays)
                .commissionRate(commissionRate)
                .degradedCompensationRate(degradedCompensationRate)
                .build();
    }
}
