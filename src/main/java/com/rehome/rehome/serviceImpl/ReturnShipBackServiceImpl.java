package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.GhnApi;
import com.rehome.rehome.client.VnPayApi;
import com.rehome.rehome.component.AuditLogger;
import com.rehome.rehome.component.BusinessNotifications;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.request.GhnCreateOrderRequest;
import com.rehome.rehome.dto.request.GhnOrderItem;
import com.rehome.rehome.dto.request.PayShipBackFeeRequest;
import com.rehome.rehome.dto.request.QueryVnPayRequest;
import com.rehome.rehome.dto.response.GhnCreateOrderResponse;
import com.rehome.rehome.dto.response.PayShipBackFeeResponse;
import com.rehome.rehome.dto.response.QueryVnPayResponse;
import com.rehome.rehome.dto.response.ReturnRequestResponse;
import com.rehome.rehome.dto.response.ShipBackPaymentStatusResponse;
import com.rehome.rehome.dto.response.VnPayIpnResult;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.enums.ReturnRequestStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.ShipBackPaymentMethod;
import com.rehome.rehome.entity.enums.ShipBackStatus;
import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.enums.WarehouseAuditGroup;
import com.rehome.rehome.entity.marketplace.Order;
import com.rehome.rehome.entity.marketplace.OrderItem;
import com.rehome.rehome.entity.marketplace.PlatformFund;
import com.rehome.rehome.entity.marketplace.ReturnRequest;
import com.rehome.rehome.entity.marketplace.ShippingAddress;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ForbiddenException;
import com.rehome.rehome.exception.GhnException;
import com.rehome.rehome.repository.AccountRepository;
import com.rehome.rehome.repository.OrderItemRepository;
import com.rehome.rehome.repository.PlatformFundRepository;
import com.rehome.rehome.repository.ReturnRequestRepository;
import com.rehome.rehome.repository.ShippingAddressRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.service.ReturnRequestService;
import com.rehome.rehome.service.ReturnShipBackService;
import com.rehome.rehome.service.SystemConfigReader;
import com.rehome.rehome.util.GatewaySignature;
import com.rehome.rehome.util.NotificationFormatting;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReturnShipBackServiceImpl implements ReturnShipBackService {

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VNPAY_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final AccountRepository accountRepository;
    private final ReturnRequestRepository returnRequestRepository;
    private final OrderItemRepository orderItemRepository;
    private final ShippingAddressRepository shippingAddressRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PlatformFundRepository platformFundRepository;
    private final VnPayApi vnPayApi;
    private final GhnApi ghnApi;
    private final QueueService queueService;
    private final ReturnRequestService returnRequestService;
    private final SystemConfigReader systemConfigReader;
    private final BusinessNotifications businessNotifications;
    private final AuditLogger auditLogger;

    @Value("${app.return.ship-back-ship-within-days:2}")
    private int defaultShipWithinDays = SystemConfigKeys.DEFAULT_RETURN_SHIP_BACK_SHIP_WITHIN_DAYS;

    @Value("${vnpay.tmn-code}")
    private String vnpTmnCode;

    @Value("${vnpay.hash-secret}")
    private String vnpHashSecret;

    @Value("${vnpay.pay-url}")
    private String vnpPayUrl;

    @Value("${vnpay.return-url}")
    private String vnpReturnUrl;

    @Value("${vnpay.version}")
    private String vnpVersion;

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

    private int shipWithinDays() {
        return systemConfigReader.getInt(SystemConfigKeys.RETURN_SHIP_BACK_SHIP_WITHIN_DAYS, defaultShipWithinDays);
    }

    // ------------------------------------------------------------------ 1.4. Trả phí gửi lại (Member)

    @Override
    @Transactional
    public PayShipBackFeeResponse payFee(String email, UUID returnRequestId,
                                         PayShipBackFeeRequest request, String clientIp) {
        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        ReturnRequest returnRequest = returnRequestRepository.findByIdForUpdate(returnRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu trả hàng."));

        if (!returnRequest.getOrder().getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenException("Bạn không có quyền thanh toán cho yêu cầu trả hàng này.");
        }

        if (returnRequest.getStatus() != ReturnRequestStatus.INVALID) {
            throw new ConflictException(
                    "Yêu cầu trả hàng không ở trạng thái không hợp lệ.", "RETURN_NOT_INVALID");
        }

        ShipBackStatus shipBackStatus = returnRequest.getShipBackStatus();
        if (shipBackStatus == ShipBackStatus.PAID
                || shipBackStatus == ShipBackStatus.SHIPPING
                || shipBackStatus == ShipBackStatus.DELIVERED) {
            throw new ConflictException("Phí gửi lại hàng đã được thanh toán.", "RETURN_SHIP_BACK_ALREADY_PAID");
        }

        if (shipBackStatus != ShipBackStatus.AWAITING_PAYMENT) {
            throw new ConflictException(
                    "Vui lòng chọn gửi lại hàng trước khi thanh toán phí.", "RETURN_SHIP_BACK_NOT_AWAITING_PAYMENT");
        }

        if (returnRequest.getShipBackFeeDeadline() != null
                && Instant.now().isAfter(returnRequest.getShipBackFeeDeadline())) {
            throw new ConflictException("Đã quá hạn thanh toán phí gửi lại hàng.", "RETURN_SHIP_BACK_FEE_EXPIRED");
        }

        ShipBackPaymentMethod method = parsePaymentMethod(request == null ? null : request.getMethod());

        return switch (method) {
            case WALLET -> payByWallet(buyer, returnRequest);
            case VNPAY -> payByVnPay(returnRequest, request.getRedirectUrl(), clientIp);
        };
    }

    private ShipBackPaymentMethod parsePaymentMethod(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException("method là bắt buộc.");
        }
        try {
            return ShipBackPaymentMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("method phải là WALLET hoặc VNPAY.");
        }
    }

    private PayShipBackFeeResponse payByWallet(Account buyer, ReturnRequest returnRequest) {
        BigDecimal fee = returnRequest.getShipBackFee();

        Wallet wallet = walletRepository.findByMemberIdForUpdate(buyer.getId())
                .orElseThrow(() -> new ConflictException(
                        "INSUFFICIENT_BALANCE: ví chưa có số dư.", "INSUFFICIENT_BALANCE"));

        BigDecimal available = wallet.getBalance().subtract(wallet.getLockedBalance());
        if (fee.compareTo(available) > 0) {
            throw new ConflictException(
                    "INSUFFICIENT_BALANCE: ví không đủ số dư khả dụng để thanh toán.", "INSUFFICIENT_BALANCE");
        }

        wallet.setBalance(wallet.getBalance().subtract(fee));
        walletRepository.save(wallet);

        WalletTransaction feeTransaction = WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.MEMBER)
                .walletId(wallet.getId())
                .type(WalletTransactionType.RETURN_SHIP_BACK_FEE)
                .amount(fee.negate())
                .balanceAfter(wallet.getBalance())
                .relatedOrder(returnRequest.getOrder())
                .relatedReturnRequest(returnRequest)
                .status(TransactionStatus.SUCCESS)
                .note("Thanh toán phí gửi lại hàng trả cho đơn " + returnRequest.getOrder().getOrderNo())
                .build();
        walletTransactionRepository.save(feeTransaction);
        recordPlatformCounterpart(feeTransaction);
        recordShipBackFeeRevenue(returnRequest, fee,
                "Doanh thu phí gửi lại hàng trả (ví) cho đơn " + returnRequest.getOrder().getOrderNo());

        completePayment(returnRequest, ShipBackPaymentMethod.WALLET);
        auditLogger.logOperation(buyer, "PAY_RETURN_SHIP_BACK_FEE", "RETURN_REQUEST", returnRequest.getId(), "WALLET");

        return PayShipBackFeeResponse.builder().paid(true).build();
    }

    private PayShipBackFeeResponse payByVnPay(ReturnRequest returnRequest, String customReturnUrl, String clientIp) {
        String txnRef = VNP_REF_PREFIX
                + returnRequest.getId().toString().replace("-", "")
                + "-" + System.currentTimeMillis();
        long amount = toGatewayAmount(returnRequest.getShipBackFee()) * 100;

        ZonedDateTime now = ZonedDateTime.now(VN_ZONE);
        String createDate = now.format(VNPAY_TIME);
        ZonedDateTime expireAt = returnRequest.getShipBackFeeDeadline() != null
                ? returnRequest.getShipBackFeeDeadline().atZone(VN_ZONE)
                : now.plusMinutes(SystemConfigKeys.DEFAULT_RETURN_SHIP_BACK_FEE_DEADLINE_MINUTES);
        if (!expireAt.isAfter(now)) {
            expireAt = now.plusMinutes(1);
        }

        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_Version", vnpVersion);
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", vnpTmnCode);
        fields.put("vnp_Amount", String.valueOf(amount));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", txnRef);
        fields.put("vnp_OrderInfo", "Thanh toan phi gui lai hang tra don " + returnRequest.getOrder().getOrderNo());
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", StringUtils.hasText(customReturnUrl) ? customReturnUrl : vnpReturnUrl);
        fields.put("vnp_IpAddr", StringUtils.hasText(clientIp) ? clientIp : "127.0.0.1");
        fields.put("vnp_CreateDate", createDate);
        fields.put("vnp_ExpireDate", expireAt.format(VNPAY_TIME));

        String query = GatewaySignature.hashAllFields(fields);
        String secureHash = GatewaySignature.hmacSHA512(vnpHashSecret, query);
        String paymentUrl = vnpPayUrl + "?" + query + "&vnp_SecureHash=" + secureHash;

        returnRequest.setShipBackVnpTxnRef(txnRef);
        returnRequest.setShipBackVnpCreateDate(createDate);
        returnRequestRepository.save(returnRequest);

        return PayShipBackFeeResponse.builder()
                .payUrl(paymentUrl)
                .txnRef(txnRef)
                .build();
    }

    // ------------------------------------------------------------------ IPN & đồng bộ VNPay

    @Override
    @Transactional
    public VnPayIpnResult handleVnPayIpn(String vnpTxnRef, String vnpAmountRaw,
                                         String responseCode, String transactionNo) {
        ReturnRequest returnRequest = returnRequestRepository.findByShipBackVnpTxnRefForUpdate(vnpTxnRef)
                .orElse(null);
        if (returnRequest == null) {
            log.warn(">>>> IPN phi gui lai hang tra cho vnp_TxnRef={} khong khop yeu cau nao", vnpTxnRef);
            return new VnPayIpnResult("01", "Order not found");
        }

        if (returnRequest.getShipBackPaidAt() != null) {
            log.info(">>>> IPN phi gui lai hang tra cho vnp_TxnRef={} da xu ly truoc do, bo qua", vnpTxnRef);
            return new VnPayIpnResult("02", "Order already confirmed");
        }

        if (returnRequest.getShipBackFee() != null) {
            long expectedAmount = toGatewayAmount(returnRequest.getShipBackFee()) * 100;
            long receivedAmount;
            try {
                receivedAmount = Long.parseLong(vnpAmountRaw);
            } catch (NumberFormatException e) {
                receivedAmount = -1;
            }
            if (receivedAmount != expectedAmount) {
                log.error(">>>> IPN sai so tien cho vnp_TxnRef={}: expected={}, received={}",
                        vnpTxnRef, expectedAmount, receivedAmount);
                return new VnPayIpnResult("04", "Invalid amount");
            }
        }

        applyGatewayResult(returnRequest, responseCode, transactionNo);
        return new VnPayIpnResult("00", "Confirm Success");
    }

    @Override
    @Transactional
    public ShipBackPaymentStatusResponse syncPaymentStatus(String email, UUID returnRequestId) {
        Account buyer = accountRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Account không tồn tại"));

        ReturnRequest returnRequest = returnRequestRepository.findByIdForUpdate(returnRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu trả hàng."));

        if (!returnRequest.getOrder().getBuyer().getId().equals(buyer.getId())) {
            throw new ForbiddenException("Bạn không có quyền xem yêu cầu trả hàng này.");
        }

        if (returnRequest.getShipBackStatus() == ShipBackStatus.AWAITING_PAYMENT
                && StringUtils.hasText(returnRequest.getShipBackVnpTxnRef())
                && StringUtils.hasText(returnRequest.getShipBackVnpCreateDate())
                && returnRequest.getShipBackFee() != null) {
            QueryVnPayResponse result = queryVnPay(
                    returnRequest.getShipBackVnpTxnRef(), returnRequest.getShipBackVnpCreateDate());
            long expectedAmount = toGatewayAmount(returnRequest.getShipBackFee()) * 100;
            if (result != null
                    && "00".equals(result.getResponseCode())
                    && returnRequest.getShipBackVnpTxnRef().equals(result.getTxnRef())
                    && vnpTmnCode.equals(result.getTmnCode())
                    && validQuerySignature(result)
                    && result.getAmount() != null
                    && result.getAmount() == expectedAmount
                    && !"01".equals(result.getTransactionStatus())) {
                applyGatewayResult(returnRequest, result.getTransactionStatus(), result.getTransactionNo());
            }
        }

        return ShipBackPaymentStatusResponse.builder()
                .returnRequestId(returnRequest.getId())
                .paid(returnRequest.getShipBackPaidAt() != null)
                .shipBackStatus(returnRequest.getShipBackStatus() == null
                        ? null : returnRequest.getShipBackStatus().name())
                .amount(returnRequest.getShipBackFee())
                .paidAt(returnRequest.getShipBackPaidAt())
                .build();
    }

    private void applyGatewayResult(ReturnRequest returnRequest, String responseCode, String transactionNo) {
        if (!"00".equals(responseCode)) {
            log.warn(">>>> VNPay bao thanh toan phi gui lai hang tra khong thanh cong: returnRequestId={}, responseCode={}",
                    returnRequest.getId(), responseCode);
            return;
        }
        if (returnRequest.getShipBackPaidAt() != null
                || returnRequest.getShipBackStatus() != ShipBackStatus.AWAITING_PAYMENT) {
            return;
        }

        BigDecimal fee = returnRequest.getShipBackFee();
        // Tiền cổng VNPay không đi qua ví Member nên không đổi số dư quỹ nền tảng - chỉ ghi dòng doanh thu
        // giống ItemWithdrawalRequestServiceImpl.applyVnPayFeePaymentResult
        recordShipBackFeeRevenue(returnRequest, fee,
                "Thu phí gửi lại hàng trả qua VNPay (mã GD " + transactionNo + ") cho đơn "
                        + returnRequest.getOrder().getOrderNo());
        completePayment(returnRequest, ShipBackPaymentMethod.VNPAY);
        auditLogger.logSystemOperation("CONFIRM_RETURN_SHIP_BACK_PAYMENT", "RETURN_REQUEST",
                returnRequest.getId(), "PAID", "VNPAY");
    }

    private void completePayment(ReturnRequest returnRequest, ShipBackPaymentMethod method) {
        returnRequest.setShipBackStatus(ShipBackStatus.PAID);
        returnRequest.setShipBackPaymentMethod(method);
        returnRequest.setShipBackPaidAt(Instant.now());
        returnRequestRepository.save(returnRequest);

        queueService.enqueue(QueueType.RETURN_SHIP_BACK,
                returnRequest.getId().toString(), returnRequest.getOrder().getWarehouse());

        businessNotifications.returned(returnRequest, NotificationType.WALLET_EVENT,
                "Đã thanh toán phí gửi lại hàng",
                "đã thanh toán " + NotificationFormatting.vnd(returnRequest.getShipBackFee())
                        + " phí gửi lại hàng. Kho sẽ gửi hàng trong vòng " + shipWithinDays() + " ngày.");
    }

    private QueryVnPayResponse queryVnPay(String txnRef, String transactionDate) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        String createDate = ZonedDateTime.now(VN_ZONE).format(VNPAY_TIME);
        String orderInfo = "Truy van giao dich " + txnRef;
        String hashData = String.join("|",
                requestId, vnpVersion, "querydr", vnpTmnCode, txnRef,
                transactionDate, createDate, "127.0.0.1", orderInfo);
        String signature = GatewaySignature.hmacSHA512(vnpHashSecret, hashData);

        QueryVnPayRequest request = QueryVnPayRequest.builder()
                .requestId(requestId)
                .version(vnpVersion)
                .command("querydr")
                .tmnCode(vnpTmnCode)
                .txnRef(txnRef)
                .orderInfo(orderInfo)
                .transactionDate(transactionDate)
                .createDate(createDate)
                .ipAddr("127.0.0.1")
                .secureHash(signature)
                .build();
        try {
            return vnPayApi.queryTransaction(request);
        } catch (Exception e) {
            log.error(">>>> Khong the truy van trang thai giao dich VNPay cho {}: {}", txnRef, e.getMessage());
            return null;
        }
    }

    private boolean validQuerySignature(QueryVnPayResponse response) {
        if (response.getSecureHash() == null) {
            return false;
        }
        String fields = Stream.of(response.getResponseId(), response.getCommand(),
                        response.getResponseCode(), response.getMessage(), response.getTmnCode(), response.getTxnRef(),
                        response.getAmount(), response.getBankCode(), response.getPayDate(), response.getTransactionNo(),
                        response.getTransactionType(), response.getTransactionStatus(), response.getOrderInfo(),
                        response.getPromotionCode(), response.getPromotionAmount())
                .map(value -> value == null ? "" : value.toString())
                .collect(Collectors.joining("|"));
        return MessageDigest.isEqual(
                GatewaySignature.hmacSHA512(vnpHashSecret, fields).toLowerCase(Locale.ROOT)
                        .getBytes(StandardCharsets.US_ASCII),
                response.getSecureHash().toLowerCase(Locale.ROOT)
                        .getBytes(StandardCharsets.US_ASCII));
    }

    // ------------------------------------------------------------------ 1.6. Moderator tạo vận đơn gửi lại

    @Override
    @Transactional
    public ReturnRequestResponse ship(Account moderator, UUID returnRequestId) {
        ReturnRequest returnRequest = returnRequestRepository.findByIdForUpdate(returnRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy yêu cầu trả hàng."));

        requireReturnWarehouse(moderator, returnRequest);

        if (returnRequest.getStatus() != ReturnRequestStatus.INVALID
                || returnRequest.getShipBackStatus() != ShipBackStatus.PAID) {
            throw new ConflictException(
                    "Yêu cầu gửi lại hàng chưa được thanh toán phí.", "RETURN_SHIP_BACK_NOT_PAID");
        }

        Order order = returnRequest.getOrder();
        List<OrderItem> items = orderItemRepository.findAllByOrder_IdWithProductOrderByIdAsc(order.getId());
        GhnCreateOrderRequest shipment = buildShipBackOrderRequest(returnRequest, items);

        GhnCreateOrderResponse ghnResponse;
        try {
            ghnResponse = ghnApi.createOrder(ghnToken, ghnShopId, shipment);
        } catch (Exception ex) {
            log.warn(">>>> Loi goi GHN tao van don gui lai hang tra {}: {}", returnRequestId, ex.getMessage());
            throw new GhnException("GHN_ERROR: không thể tạo vận đơn gửi lại hàng trả.");
        }

        if (ghnResponse == null || ghnResponse.getData() == null
                || !StringUtils.hasText(ghnResponse.getData().getOrderCode())) {
            throw new GhnException("GHN_ERROR: GHN không trả về mã vận đơn gửi lại hàng trả.");
        }

        String trackingCode = ghnResponse.getData().getOrderCode();
        returnRequest.setShipBackTrackingCode(trackingCode);
        returnRequest.setShipBackStatus(ShipBackStatus.SHIPPING);
        returnRequest.setShipBackShippedAt(Instant.now());
        returnRequestRepository.save(returnRequest);

        queueService.dequeue(QueueType.RETURN_SHIP_BACK, returnRequest.getId().toString());
        auditLogger.logWarehouseOp(moderator, order.getWarehouse(), WarehouseAuditGroup.PACKING,
                "SHIP_BACK_RETURN", "RETURN_REQUEST", returnRequestId.toString(), trackingCode, order.getOrderNo());

        businessNotifications.returned(returnRequest, NotificationType.ORDER_UPDATE,
                "ReHome đã gửi lại hàng trả",
                "đã gửi lại hàng qua GHN · Mã vận đơn: " + trackingCode + ".");

        return returnRequestService.getDetailForModerator(returnRequestId);
    }

    private void requireReturnWarehouse(Account moderator, ReturnRequest request) {
        if (moderator == null || moderator.getRole() != Role.MODERATOR
                || moderator.getWarehouse() == null
                || !moderator.getWarehouse().getId().equals(request.getOrder().getWarehouse().getId())) {
            throw new ForbiddenException("Only the assigned warehouse moderator can handle this return.");
        }
    }

    private GhnCreateOrderRequest buildShipBackOrderRequest(ReturnRequest returnRequest, List<OrderItem> items) {
        Order order = returnRequest.getOrder();
        Warehouse warehouse = order.getWarehouse();

        String toName = returnRequest.getShipBackRecipientName();
        String toPhone = returnRequest.getShipBackPhone();
        String toAddress = returnRequest.getShipBackAddressDetail();
        String toWardName = returnRequest.getShipBackWardName();
        String toProvinceName = returnRequest.getShipBackProvinceName();

        // Phòng trường hợp dòng cũ chỉ có shipBackAddressId mà thiếu snapshot
        if ((!StringUtils.hasText(toWardName) || !StringUtils.hasText(toProvinceName))
                && returnRequest.getShipBackAddressId() != null) {
            ShippingAddress address = shippingAddressRepository.findById(returnRequest.getShipBackAddressId())
                    .orElse(null);
            if (address != null) {
                toName = address.getRecipientName();
                toPhone = address.getPhone();
                toAddress = address.getAddressDetail();
                toWardName = address.getWardName();
                toProvinceName = address.getProvinceName();
            }
        }

        List<GhnOrderItem> ghnItems = items.stream()
                .map(oi -> GhnOrderItem.builder()
                        .name(oi.getProduct().getName())
                        .code(oi.getProduct().getId())
                        .quantity(1)
                        .price(oi.getPriceAtOrder().intValue())
                        .build())
                .toList();

        return GhnCreateOrderRequest.builder()
                .clientOrderCode("RSB-" + returnRequest.getId())
                .fromName(warehouse.getName())
                .fromPhone(warehouse.getPhone())
                .fromAddress(warehouse.getAddress())
                .fromWardName(trimToNull(warehouse.getWardName()))
                .fromDistrictName(Boolean.TRUE.equals(warehouse.getIsNewAddress())
                        ? null : trimToNull(warehouse.getDistrictName()))
                .fromProvinceName(trimToNull(warehouse.getProvinceName()))
                .isNewFromAddress(Boolean.TRUE.equals(warehouse.getIsNewAddress()))
                .toName(toName)
                .toPhone(toPhone)
                .toAddress(toAddress)
                .toWardName(trimToNull(toWardName))
                .toDistrictName(null)
                .toProvinceName(trimToNull(toProvinceName))
                .isNewToAddress(true)
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

    // ------------------------------------------------------------------ Sổ quỹ & doanh thu

    private String generateTransactionNo() {
        String prefix = "WT-" + ZonedDateTime.now(ZoneId.of("UTC")).getYear() + "-";
        long seq = walletTransactionRepository.countByTransactionNoStartingWith(prefix) + 1;
        return prefix + String.format("%06d", seq);
    }

    private PlatformFund getOrCreatePlatformFund() {
        return platformFundRepository.findTopByOrderByIdAsc()
                .orElseGet(() -> PlatformFund.builder().build());
    }

    private void recordPlatformCounterpart(WalletTransaction memberTransaction) {
        PlatformFund fund = getOrCreatePlatformFund();
        BigDecimal counterpartAmount = memberTransaction.getAmount().negate();
        fund.setBalance(fund.getBalance().add(counterpartAmount));
        platformFundRepository.save(fund);

        WalletTransaction platformTransaction = WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.PLATFORM)
                .walletId(fund.getId())
                .type(memberTransaction.getType())
                .amount(counterpartAmount)
                .balanceAfter(fund.getBalance())
                .relatedOrder(memberTransaction.getRelatedOrder())
                .relatedReturnRequest(memberTransaction.getRelatedReturnRequest())
                .counterpartTransaction(memberTransaction)
                .status(TransactionStatus.SUCCESS)
                .note(memberTransaction.getNote())
                .build();
        walletTransactionRepository.save(platformTransaction);
    }

    private void recordShipBackFeeRevenue(ReturnRequest returnRequest, BigDecimal amount, String note) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        PlatformFund fund = getOrCreatePlatformFund();
        if (fund.getId() == null) {
            platformFundRepository.save(fund);
        }

        WalletTransaction revenueTransaction = WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.PLATFORM)
                .walletId(fund.getId())
                .type(WalletTransactionType.RETURN_SHIP_BACK_FEE_REVENUE)
                .amount(amount)
                .balanceAfter(fund.getBalance())
                .relatedOrder(returnRequest.getOrder())
                .relatedReturnRequest(returnRequest)
                .status(TransactionStatus.SUCCESS)
                .note(note)
                .build();
        walletTransactionRepository.save(revenueTransaction);
    }

    private static long toGatewayAmount(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
