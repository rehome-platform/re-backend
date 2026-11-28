package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.dto.request.CreateItemWithdrawalRequest;
import com.rehome.rehome.dto.request.PayItemWithdrawalFeeRequest;
import com.rehome.rehome.dto.request.RejectItemWithdrawalRequest;
import com.rehome.rehome.dto.response.ItemWithdrawalDetailResponse;
import com.rehome.rehome.dto.response.ItemWithdrawalResponse;
import com.rehome.rehome.dto.response.PayItemWithdrawalFeeResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.QueueType;
import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.enums.WithdrawalFeePaymentMethod;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.PlatformFund;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.ProductPhoto;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.entity.marketplace.Warehouse;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ItemWithdrawalRequestRepository;
import com.rehome.rehome.repository.PlatformFundRepository;
import com.rehome.rehome.repository.ProductPhotoRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import com.rehome.rehome.service.ItemWithdrawalRequestService;
import com.rehome.rehome.service.QueueService;
import com.rehome.rehome.service.SystemConfigReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ItemWithdrawalRequestServiceImpl implements ItemWithdrawalRequestService {

    private static final List<ItemWithdrawalStatus> ACTIVE_WITHDRAWAL_STATUSES =
            List.of(ItemWithdrawalStatus.PENDING_APPROVAL, ItemWithdrawalStatus.PENDING_PICKUP);

    private final com.rehome.rehome.component.AuditLogger auditLogger;
    private final ProductRepository productRepository;
    private final ProductPhotoRepository productPhotoRepository;
    private final ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PlatformFundRepository platformFundRepository;
    private final QueueService queueService;
    private final com.rehome.rehome.service.NotificationService notificationService;
    private final SystemConfigReader systemConfigReader;

    @Value("${app.item-withdrawal.fee-payment-deadline-minutes}")
    private long feePaymentDeadlineMinutes;

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

    private int getCounterPickupDays() {
        return systemConfigReader.getInt(
                SystemConfigKeys.COUNTER_PICKUP_DAYS, SystemConfigKeys.DEFAULT_COUNTER_PICKUP_DAYS);
    }

    @Override
    @Transactional
    public ItemWithdrawalResponse create(Account account, CreateItemWithdrawalRequest request) {

        if (request == null || !StringUtils.hasText(request.getProductId())) {
            throw new BadRequestException("productId là bắt buộc.");
        }

        Product product = productRepository.findByIdForUpdate(request.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        if (!product.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.PRODUCT_FORBIDDEN);
        }

        if (product.getStatus() != ProductStatus.ON_SALE) {
            throw new AppException(ErrorCode.PRODUCT_ALREADY_ORDERED);
        }

        if (itemWithdrawalRequestRepository.existsByProduct_IdAndStatusIn(
                product.getId(), ACTIVE_WITHDRAWAL_STATUSES)) {
            throw new AppException(ErrorCode.ITEM_WITHDRAWAL_ALREADY_PENDING);
        }

        BigDecimal fee = computeFee(product);

        ItemWithdrawalRequest withdrawal = ItemWithdrawalRequest.builder()
                .product(product)
                .member(account)
                .feeCharged(fee)
                .status(ItemWithdrawalStatus.PENDING_APPROVAL)
                .build();

        itemWithdrawalRequestRepository.save(withdrawal);

        queueService.enqueue(QueueType.WITHDRAWAL, withdrawal.getId().toString(), product.getWarehouse());

        product.setStatus(ProductStatus.WAREHOUSE);
        productRepository.save(product);

        auditLogger.logOperation(account, "CREATE_WITHDRAWAL", "WITHDRAWAL", withdrawal.getId(), withdrawal.getStatus().name());
        return toResponse(withdrawal);
    }

    @Override
    @Transactional
    public ItemWithdrawalResponse approve(Account moderator, UUID id) {
        ItemWithdrawalRequest withdrawal = itemWithdrawalRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.ITEM_WITHDRAWAL_NOT_FOUND));
        assertModeratorOwnsWarehouse(moderator, withdrawal);

        if (withdrawal.getStatus() != ItemWithdrawalStatus.PENDING_APPROVAL) {
            throw new AppException(ErrorCode.ITEM_WITHDRAWAL_NOT_PENDING_APPROVAL);
        }

        withdrawal.setStatus(ItemWithdrawalStatus.PENDING_PICKUP);
        withdrawal.setModerator(moderator);
        withdrawal.setReviewedAt(Instant.now());
        withdrawal.setPickupDeadline(Instant.now().plus(getCounterPickupDays(), ChronoUnit.DAYS));
        withdrawal.setFeePaymentDeadline(withdrawal.getFeeCharged().compareTo(BigDecimal.ZERO) > 0
                ? Instant.now().plus(feePaymentDeadlineMinutes, ChronoUnit.MINUTES)
                : null);

        itemWithdrawalRequestRepository.save(withdrawal);
        notificationService.notify(withdrawal.getMember(),
                com.rehome.rehome.entity.enums.NotificationType.WITHDRAWAL_APPROVED,
                "Yêu cầu rút hàng đã được duyệt", "Bạn có thể xem thời hạn nhận hàng và thông tin thanh toán trong yêu cầu.",
                com.rehome.rehome.entity.enums.NotificationTargetType.WITHDRAWAL, id,
                withdrawal.getProduct().getWarehouse().getId());
        auditLogger.logOperation(moderator, "APPROVE_WITHDRAWAL", "WITHDRAWAL", id, withdrawal.getStatus().name());
        return toResponse(withdrawal);
    }

    @Override
    @Transactional
    public ItemWithdrawalResponse reject(Account moderator, UUID id, RejectItemWithdrawalRequest request) {
        if (request == null || !StringUtils.hasText(request.getReason())) {
            throw new BadRequestException("reason là bắt buộc.");
        }

        ItemWithdrawalRequest withdrawal = itemWithdrawalRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.ITEM_WITHDRAWAL_NOT_FOUND));
        assertModeratorOwnsWarehouse(moderator, withdrawal);

        if (withdrawal.getStatus() != ItemWithdrawalStatus.PENDING_APPROVAL) {
            throw new AppException(ErrorCode.ITEM_WITHDRAWAL_NOT_PENDING_APPROVAL);
        }

        withdrawal.setStatus(ItemWithdrawalStatus.REJECTED);
        withdrawal.setModerator(moderator);
        withdrawal.setRejectReason(request.getReason().trim());
        withdrawal.setReviewedAt(Instant.now());
        itemWithdrawalRequestRepository.save(withdrawal);

        queueService.dequeue(QueueType.WITHDRAWAL, withdrawal.getId().toString());

        Product product = productRepository.findByIdForUpdate(withdrawal.getProduct().getId())
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        if (product.getStatus() == ProductStatus.WAREHOUSE || product.getStatus() == ProductStatus.WITHDRAWN) {
            product.setStatus(ProductStatus.ON_SALE);
            productRepository.save(product);
        }

        notificationService.notify(withdrawal.getMember(),
                com.rehome.rehome.entity.enums.NotificationType.WITHDRAWAL_REJECTED,
                "Yêu cầu rút hàng bị từ chối", withdrawal.getRejectReason(),
                com.rehome.rehome.entity.enums.NotificationTargetType.WITHDRAWAL, id,
                withdrawal.getProduct().getWarehouse().getId());
        auditLogger.logOperation(moderator, "REJECT_WITHDRAWAL", "WITHDRAWAL", id, withdrawal.getStatus().name());
        return toResponse(withdrawal);
    }

    private void assertModeratorOwnsWarehouse(Account moderator, ItemWithdrawalRequest withdrawal) {
        if (moderator.getWarehouse() == null) {
            throw new AppException(ErrorCode.MODERATOR_NO_WAREHOUSE);
        }
        Warehouse productWarehouse = withdrawal.getProduct().getWarehouse();
        if (productWarehouse == null || !productWarehouse.getId().equals(moderator.getWarehouse().getId())) {
            throw new AppException(ErrorCode.ITEM_WITHDRAWAL_FORBIDDEN);
        }
    }

    private BigDecimal computeFee(Product product) {
        ConsignmentReceipt receipt = product.getConsignmentReceipt();
        Instant termStartedAt = product.getTermStartedAt();
        if (receipt == null || termStartedAt == null || receipt.getFreeWithdrawalWindowDays() == null) {
            throw new AppException(ErrorCode.PRODUCT_RECEIPT_INCOMPLETE);
        }

        Instant freeWindowEnd = termStartedAt.plus(receipt.getFreeWithdrawalWindowDays(), ChronoUnit.DAYS);
        if (!Instant.now().isAfter(freeWindowEnd)) {
            return BigDecimal.ZERO;
        }

        if (receipt.getWithdrawalFee() == null) {
            throw new AppException(ErrorCode.PRODUCT_RECEIPT_INCOMPLETE);
        }
        return receipt.getWithdrawalFee();
    }

    @Override
    @Transactional
    public PayItemWithdrawalFeeResponse payFee(
            Account account, UUID id, PayItemWithdrawalFeeRequest request, String clientIp) {

        ItemWithdrawalRequest withdrawal = itemWithdrawalRequestRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(ErrorCode.ITEM_WITHDRAWAL_NOT_FOUND));

        if (!withdrawal.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.ITEM_WITHDRAWAL_FORBIDDEN);
        }

        if (withdrawal.getStatus() != ItemWithdrawalStatus.PENDING_PICKUP) {
            throw new AppException(ErrorCode.ITEM_WITHDRAWAL_NOT_PENDING);
        }

        if (withdrawal.getFeeCharged().compareTo(BigDecimal.ZERO) == 0 || withdrawal.getFeePaidAt() != null) {
            return toPaidResponse(withdrawal);
        }

        WithdrawalFeePaymentMethod method = parsePaymentMethod(
                request == null ? null : request.getPaymentMethod());

        return switch (method) {
            case WALLET -> payByWallet(account, withdrawal);
            case VNPAY -> payByVnPay(withdrawal, clientIp);
        };
    }

    private WithdrawalFeePaymentMethod parsePaymentMethod(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BadRequestException("paymentMethod là bắt buộc.");
        }
        try {
            return WithdrawalFeePaymentMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("paymentMethod phải là WALLET hoặc VNPAY.");
        }
    }

    private PayItemWithdrawalFeeResponse payByWallet(Account account, ItemWithdrawalRequest withdrawal) {

        Wallet wallet = walletRepository.findByMemberIdForUpdate(account.getId())
                .orElseThrow(() -> new ConflictException(
                        "INSUFFICIENT_BALANCE: ví chưa có số dư.", "INSUFFICIENT_BALANCE"));

        BigDecimal available = wallet.getBalance().subtract(wallet.getLockedBalance());
        if (withdrawal.getFeeCharged().compareTo(available) > 0) {
            throw new ConflictException(
                    "INSUFFICIENT_BALANCE: ví không đủ số dư khả dụng để thanh toán.", "INSUFFICIENT_BALANCE");
        }

        wallet.setBalance(wallet.getBalance().subtract(withdrawal.getFeeCharged()));
        walletRepository.save(wallet);

        WalletTransaction feeTransaction = WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.MEMBER)
                .walletId(wallet.getId())
                .type(WalletTransactionType.WITHDRAWAL_FEE)
                .amount(withdrawal.getFeeCharged().negate())
                .balanceAfter(wallet.getBalance())
                .relatedItemWithdrawalRequest(withdrawal)
                .status(TransactionStatus.SUCCESS)
                .note("Thanh toán phí rút hàng cho sản phẩm " + withdrawal.getProduct().getId())
                .build();
        walletTransactionRepository.save(feeTransaction);
        recordPlatformCounterpart(feeTransaction);
        recordWithdrawalFeeRevenue(withdrawal, "Doanh thu phí rút hàng (ví) cho sản phẩm "
                + withdrawal.getProduct().getId());

        withdrawal.setFeePaymentMethod(WithdrawalFeePaymentMethod.WALLET);
        withdrawal.setFeePaidAt(Instant.now());
        itemWithdrawalRequestRepository.save(withdrawal);

        return toPaidResponse(withdrawal);
    }

    private PayItemWithdrawalFeeResponse payByVnPay(ItemWithdrawalRequest withdrawal, String clientIp) {

        String txnRef = "IWR-" + withdrawal.getId() + "-" + System.currentTimeMillis();
        long amount = withdrawal.getFeeCharged().setScale(0, RoundingMode.HALF_UP).longValueExact() * 100;

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        String createDate = now.format(formatter);
        String expireDate = now.plusMinutes(15).format(formatter);

        Map<String, String> fields = new HashMap<>();
        fields.put("vnp_Version", vnpVersion);
        fields.put("vnp_Command", "pay");
        fields.put("vnp_TmnCode", vnpTmnCode);
        fields.put("vnp_Amount", String.valueOf(amount));
        fields.put("vnp_CurrCode", "VND");
        fields.put("vnp_TxnRef", txnRef);
        fields.put("vnp_OrderInfo", "Thanh toan phi rut hang: " + withdrawal.getProduct().getId());
        fields.put("vnp_OrderType", "other");
        fields.put("vnp_Locale", "vn");
        fields.put("vnp_ReturnUrl", vnpReturnUrl);
        fields.put("vnp_IpAddr", StringUtils.hasText(clientIp) ? clientIp : "127.0.0.1");
        fields.put("vnp_CreateDate", createDate);
        fields.put("vnp_ExpireDate", expireDate);

        String query = hashAllFields(fields);

        String secureHash;
        try {
            secureHash = hmacSHA512(vnpHashSecret, query);
        } catch (Exception e) {
            log.error(">>>> Co loi khi hash chu ky VNPay cho phi rut hang: " + e);
            throw new BadRequestException("Không thể tạo chữ ký thanh toán");
        }

        String paymentUrl = vnpPayUrl + "?" + query + "&vnp_SecureHash=" + secureHash;

        withdrawal.setVnpTxnRef(txnRef);
        withdrawal.setVnpCreateDate(createDate);
        itemWithdrawalRequestRepository.save(withdrawal);

        return PayItemWithdrawalFeeResponse.builder().payUrl(paymentUrl).build();
    }

    private PayItemWithdrawalFeeResponse toPaidResponse(ItemWithdrawalRequest withdrawal) {
        return PayItemWithdrawalFeeResponse.builder()
                .id(withdrawal.getId().toString())
                .feeCharged(withdrawal.getFeeCharged())
                .status(withdrawal.getStatus().name())
                .build();
    }

    @Override
    @Transactional
    public boolean applyVnPayFeePaymentResult(String vnpTxnRef, String responseCode, String transactionNo) {

        ItemWithdrawalRequest withdrawal = itemWithdrawalRequestRepository
                .findByVnpTxnRef(vnpTxnRef).orElse(null);

        if (withdrawal == null) {
            return false;
        }

        if (withdrawal.getFeePaidAt() != null) {
            log.info(">>>> IPN VNPay cho phi rut hang vnp_TxnRef={} da duoc xu ly truoc do, bo qua", vnpTxnRef);
            return true;
        }

        if (!"00".equals(responseCode)) {
            log.warn(">>>> VNPay bao thanh toan phi rut hang khong thanh cong: vnp_TxnRef={}, responseCode={}",
                    vnpTxnRef, responseCode);
            return true;
        }

        withdrawal.setFeePaymentMethod(WithdrawalFeePaymentMethod.VNPAY);
        withdrawal.setFeePaidAt(Instant.now());
        itemWithdrawalRequestRepository.saveAndFlush(withdrawal);
        refundDonationFee(withdrawal);
        recordPlatformIncomeFromGateway(withdrawal, "VNPay", transactionNo);

        log.info(">>>> Da thanh toan phi rut hang qua VNPay: itemWithdrawalRequestId={}, transactionNo={}",
                withdrawal.getId(), transactionNo);
        return true;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void refundDonationFee(ItemWithdrawalRequest withdrawal) {
        if (withdrawal.getStatus() != ItemWithdrawalStatus.CONVERTED_TO_DONATION
                || withdrawal.getFeePaidAt() == null || withdrawal.getFeeCharged().signum() <= 0) {
            return;
        }

        String transactionNo = "WT-REFUND-IWR-" + withdrawal.getId();
        Wallet wallet = walletRepository.findByMemberIdForUpdate(withdrawal.getMember().getId())
                .orElseGet(() -> Wallet.builder().member(withdrawal.getMember()).build());
        if (walletTransactionRepository.existsByTransactionNo(transactionNo)) {
            return;
        }

        BigDecimal amount = withdrawal.getFeeCharged();
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);
        WalletTransaction refund = walletTransactionRepository.save(WalletTransaction.builder()
                .transactionNo(transactionNo)
                .walletType(WalletType.MEMBER)
                .walletId(wallet.getId())
                .type(WalletTransactionType.REFUND)
                .amount(amount)
                .balanceAfter(wallet.getBalance())
                .relatedItemWithdrawalRequest(withdrawal)
                .status(TransactionStatus.SUCCESS)
                .note("Hoàn phí rút hàng do chuyển món ký gửi sang quyên góp")
                .build());
        recordPlatformCounterpart(refund);
        recordWithdrawalFeeRevenue(withdrawal, amount.negate(), refund.getNote());
    }

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
                .relatedItemWithdrawalRequest(memberTransaction.getRelatedItemWithdrawalRequest())
                .counterpartTransaction(memberTransaction)
                .status(TransactionStatus.SUCCESS)
                .note(memberTransaction.getNote())
                .build();
        walletTransactionRepository.save(platformTransaction);
    }

    private void recordPlatformIncomeFromGateway(
            ItemWithdrawalRequest withdrawal, String gateway, String gatewayTransId) {
        recordWithdrawalFeeRevenue(withdrawal, "Thu phí rút hàng qua " + gateway + " (mã GD " + gatewayTransId
                + ") cho sản phẩm " + withdrawal.getProduct().getId());
    }

    private void recordWithdrawalFeeRevenue(ItemWithdrawalRequest withdrawal, String note) {
        BigDecimal amount = withdrawal.getFeeCharged();
        if (amount != null && amount.signum() > 0) {
            recordWithdrawalFeeRevenue(withdrawal, amount, note);
        }
    }

    private void recordWithdrawalFeeRevenue(ItemWithdrawalRequest withdrawal, BigDecimal amount, String note) {
        PlatformFund fund = getOrCreatePlatformFund();
        if (fund.getId() == null) {
            platformFundRepository.save(fund);
        }

        WalletTransaction revenueTransaction = WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.PLATFORM)
                .walletId(fund.getId())
                .type(WalletTransactionType.WITHDRAWAL_FEE_REVENUE)
                .amount(amount)
                .balanceAfter(fund.getBalance())
                .relatedItemWithdrawalRequest(withdrawal)
                .status(TransactionStatus.SUCCESS)
                .note(note)
                .build();
        walletTransactionRepository.save(revenueTransaction);
    }

    private String hashAllFields(Map<String, String> fields) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);

        List<String> pairs = new ArrayList<>();
        for (String fieldName : fieldNames) {
            String fieldValue = fields.get(fieldName);
            if (StringUtils.hasText(fieldValue)) {
                pairs.add(fieldName + "=" + urlEncode(fieldValue));
            }
        }
        return String.join("&", pairs);
    }

    private String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.US_ASCII.toString());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String hmacSHA512(String key, String data) throws Exception {
        Mac hmac512 = Mac.getInstance("HmacSHA512");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        hmac512.init(secretKeySpec);

        byte[] hash = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return toHex(hash);
    }

    private String toHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }

    @Override
    public ItemWithdrawalDetailResponse getMine(Account account, UUID id) {
        ItemWithdrawalRequest withdrawal = itemWithdrawalRequestRepository.findDetailById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ITEM_WITHDRAWAL_NOT_FOUND));

        if (!withdrawal.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.ITEM_WITHDRAWAL_FORBIDDEN);
        }
        String photoUrl = productPhotoRepository
                .findByProduct_IdOrderByDisplayOrderAscIdAsc(withdrawal.getProduct().getId()).stream()
                .map(ProductPhoto::getImageUrl)
                .findFirst().orElse(null);
        return toDetailResponse(withdrawal, photoUrl);
    }

    @Override
    public List<ItemWithdrawalDetailResponse> listMine(Account account, String status) {
        ItemWithdrawalStatus parsed = StringUtils.hasText(status) ? parseStatus(status) : null;

        List<ItemWithdrawalRequest> withdrawals = itemWithdrawalRequestRepository
                .findDetailsByMember(account.getId(), parsed);
        if (withdrawals.isEmpty()) {
            return List.of();
        }

        List<String> productIds = withdrawals.stream().map(w -> w.getProduct().getId()).toList();
        Map<String, String> photosByProduct = productPhotoRepository
                .findByProduct_IdInOrderByDisplayOrderAscIdAsc(productIds).stream()
                .collect(Collectors.toMap(
                        ph -> ph.getProduct().getId(), ProductPhoto::getImageUrl, (first, second) -> first));

        return withdrawals.stream()
                .map(w -> toDetailResponse(w, photosByProduct.get(w.getProduct().getId())))
                .toList();
    }

    private ItemWithdrawalStatus parseStatus(String status) {
        try {
            return ItemWithdrawalStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(
                    "status phải là PENDING_APPROVAL, PENDING_PICKUP, REJECTED, COMPLETED, EXPIRED, CANCELLED hoặc CONVERTED_TO_DONATION.");
        }
    }

    private static ItemWithdrawalDetailResponse toDetailResponse(ItemWithdrawalRequest w, String photoUrl) {
        Product product = w.getProduct();
        Warehouse warehouse = product.getWarehouse();
        return ItemWithdrawalDetailResponse.builder()
                .id(w.getId().toString())
                .productId(product.getId())
                .productName(product.getName())
                .status(w.getStatus().name())
                .rejectReason(w.getRejectReason())
                .feeAmount(w.getFeeCharged())
                .feePaid(w.getFeeCharged().compareTo(BigDecimal.ZERO) == 0 || w.getFeePaidAt() != null)
                .paymentMethod(w.getFeePaymentMethod() == null ? null : w.getFeePaymentMethod().name())
                .pickupDeadline(w.getPickupDeadline())
                .feePaymentDeadline(w.getFeePaidAt() == null ? w.getFeePaymentDeadline() : null)
                .warehouseName(warehouse.getName())
                .warehouseAddress(warehouse.getAddress())
                .warehouseOpeningHours(warehouse.getOpeningHours())
                .photoUrl(photoUrl)
                .createdAt(w.getCreatedAt())
                .completedAt(w.getCompletedAt())
                .build();
    }

    private ItemWithdrawalResponse toResponse(ItemWithdrawalRequest withdrawal) {
        return ItemWithdrawalResponse.builder()
                .id(withdrawal.getId().toString())
                .productId(withdrawal.getProduct().getId())
                .feeCharged(withdrawal.getFeeCharged())
                .status(withdrawal.getStatus().name())
                .pickupDeadline(withdrawal.getPickupDeadline())
                .feePaymentDeadline(withdrawal.getFeePaymentDeadline())
                .rejectReason(withdrawal.getRejectReason())
                .build();
    }
}
