package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.client.MomoApi;
import com.rehome.rehome.client.VnPayApi;
import com.rehome.rehome.dto.request.CreateItemWithdrawalRequest;
import com.rehome.rehome.dto.request.CreateMomoRequest;
import com.rehome.rehome.dto.request.PayItemWithdrawalFeeRequest;
import com.rehome.rehome.dto.response.CreateMomoResponse;
import com.rehome.rehome.dto.response.ItemWithdrawalResponse;
import com.rehome.rehome.dto.response.PayItemWithdrawalFeeResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.ItemWithdrawalStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.enums.WithdrawalFeePaymentMethod;
import com.rehome.rehome.entity.marketplace.ConsignmentReceipt;
import com.rehome.rehome.entity.marketplace.ItemWithdrawalRequest;
import com.rehome.rehome.entity.marketplace.PlatformFund;
import com.rehome.rehome.entity.marketplace.Product;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ItemWithdrawalRequestRepository;
import com.rehome.rehome.repository.PlatformFundRepository;
import com.rehome.rehome.repository.ProductRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import com.rehome.rehome.service.ItemWithdrawalRequestService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ItemWithdrawalRequestServiceImpl implements ItemWithdrawalRequestService {

    private final ProductRepository productRepository;
    private final ItemWithdrawalRequestRepository itemWithdrawalRequestRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PlatformFundRepository platformFundRepository;
    private final MomoApi momoApi;
    private final VnPayApi vnPayApi;

    @Value("${app.item-withdrawal.pickup-deadline-days}")
    private long pickupDeadlineDays;

    @Value("${momo.partner-code}")
    private String momoPartnerCode;

    @Value("${momo.access-key}")
    private String momoAccessKey;

    @Value("${momo.secret-key}")
    private String momoSecretKey;

    @Value("${momo.return-url}")
    private String momoRedirectUrl;

    @Value("${momo.ipn-url}")
    private String momoIpnUrl;

    @Value("${momo.request-type}")
    private String momoRequestType;

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

        if (itemWithdrawalRequestRepository.existsByProduct_IdAndStatus(
                product.getId(), ItemWithdrawalStatus.PENDING_PICKUP)) {
            throw new AppException(ErrorCode.ITEM_WITHDRAWAL_ALREADY_PENDING);
        }

        ItemWithdrawalRequest withdrawal = ItemWithdrawalRequest.builder()
                .product(product)
                .member(account)
                .feeCharged(computeFee(product))
                .status(ItemWithdrawalStatus.PENDING_PICKUP)
                .pickupDeadline(Instant.now().plus(pickupDeadlineDays, ChronoUnit.DAYS))
                .build();

        itemWithdrawalRequestRepository.save(withdrawal);

        // F-032: "so that I can stop selling it" - sản phẩm ngừng bán ngay khi gửi yêu cầu, không
        // chờ tới lúc bàn giao (F-034) mới gỡ. Hàng vẫn nằm đúng vị trí kệ cho tới khi Member thật
        // sự đến lấy - không ghi StockMovementLog ở đây vì hàng chưa rời kho, chỉ đổi trạng thái niêm yết.
        product.setStatus(ProductStatus.WITHDRAWN);
        productRepository.save(product);

        return toResponse(withdrawal);
    }

    // F-032: miễn phí trong freeWithdrawalWindowDays đầu mỗi kỳ (tính từ termStartedAt); sau đó
    // tính phí rút cố định đã snapshot trên biên nhận lúc ký, không đọc cấu hình sống - đổi cấu
    // hình không ảnh hưởng hợp đồng cũ (xem docs/OVERVIEW.md).
    private BigDecimal computeFee(Product product) {
        ConsignmentReceipt receipt = product.getConsignmentReceipt();
        Instant freeWindowEnd = product.getTermStartedAt()
                .plus(receipt.getFreeWithdrawalWindowDays(), ChronoUnit.DAYS);

        return Instant.now().isAfter(freeWindowEnd) ? receipt.getWithdrawalFee() : BigDecimal.ZERO;
    }

    // API 91 (F-033): trả phí rút hàng (nếu có) bằng đúng 1 phương thức - ví, MoMo hoặc VNPay,
    // không chia nhỏ. Ví trừ và chốt ngay trong transaction này; MoMo/VNPay chỉ mở giao dịch,
    // kết quả thật chờ IPN (applyMomoFeePaymentResult / applyVnPayFeePaymentResult).
    @Override
    @Transactional
    public PayItemWithdrawalFeeResponse payFee(
            Account account, UUID id, PayItemWithdrawalFeeRequest request, String clientIp) {

        ItemWithdrawalRequest withdrawal = itemWithdrawalRequestRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ITEM_WITHDRAWAL_NOT_FOUND));

        if (!withdrawal.getMember().getId().equals(account.getId())) {
            throw new AppException(ErrorCode.ITEM_WITHDRAWAL_FORBIDDEN);
        }

        // Miễn phí hoặc đã thanh toán xong trước đó -> không cần làm gì thêm
        if (withdrawal.getFeeCharged().compareTo(BigDecimal.ZERO) == 0 || withdrawal.getFeePaidAt() != null) {
            return toPaidResponse(withdrawal);
        }

        WithdrawalFeePaymentMethod method = parsePaymentMethod(
                request == null ? null : request.getPaymentMethod());

        return switch (method) {
            case WALLET -> payByWallet(account, withdrawal);
            case MOMO -> payByMomo(withdrawal);
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
            throw new BadRequestException("paymentMethod phải là WALLET, MOMO hoặc VNPAY.");
        }
    }

    private PayItemWithdrawalFeeResponse payByWallet(Account account, ItemWithdrawalRequest withdrawal) {

        // Khóa ví trước khi đọc số dư: hai request trả phí song song của cùng người không được
        // cùng thấy đủ tiền rồi trừ thành số dư âm
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

        withdrawal.setFeePaymentMethod(WithdrawalFeePaymentMethod.WALLET);
        withdrawal.setFeePaidAt(Instant.now());
        itemWithdrawalRequestRepository.save(withdrawal);

        return toPaidResponse(withdrawal);
    }

    private PayItemWithdrawalFeeResponse payByMomo(ItemWithdrawalRequest withdrawal) {

        String momoOrderId = "IWR-" + withdrawal.getId() + "-" + System.currentTimeMillis();
        String orderInfo = "Thanh toan phi rut hang: " + withdrawal.getProduct().getId();
        String requestId = UUID.randomUUID().toString();
        String extraData = withdrawal.getId().toString();
        long amount = withdrawal.getFeeCharged().setScale(0, RoundingMode.HALF_UP).longValueExact();

        String rawSignature = String.format(
                "accessKey=%s&amount=%s&extraData=%s&ipnUrl=%s&orderId=%s&orderInfo=%s&partnerCode=%s&redirectUrl=%s&requestId=%s&requestType=%s",
                momoAccessKey, amount, extraData, momoIpnUrl, momoOrderId, orderInfo, momoPartnerCode,
                momoRedirectUrl, requestId, momoRequestType);

        String signature;
        try {
            signature = signHmacSHA256(rawSignature, momoSecretKey);
        } catch (Exception e) {
            log.error(">>>> Co loi khi hash chu ky MoMo cho phi rut hang: " + e);
            throw new BadRequestException("Không thể tạo chữ ký thanh toán");
        }

        CreateMomoRequest momoRequest = CreateMomoRequest.builder()
                .partnerCode(momoPartnerCode)
                .requestType(momoRequestType)
                .ipnUrl(momoIpnUrl)
                .redirectUrl(momoRedirectUrl)
                .orderId(momoOrderId)
                .orderInfo(orderInfo)
                .requestId(requestId)
                .extraData(extraData)
                .amount(amount)
                .signature(signature)
                .lang("vi")
                .build();

        CreateMomoResponse response = momoApi.createMomoQR(momoRequest);

        if (response == null || response.getResultCode() != 0) {
            String message = response != null ? response.getMessage() : "Không nhận được phản hồi từ MoMo";
            throw new BadRequestException("Tạo giao dịch MoMo thất bại: " + message);
        }

        withdrawal.setMomoOrderId(momoOrderId);
        withdrawal.setMomoRequestId(requestId);
        itemWithdrawalRequestRepository.save(withdrawal);

        return PayItemWithdrawalFeeResponse.builder().payUrl(response.getPayUrl()).build();
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

    // Webhook MoMo gọi khi có kết quả giao dịch trả phí rút hàng (dispatch từ MomoServiceImpl.handleIpn
    // khi momoOrderId không khớp Payment nào, vì đây không phải thanh toán đơn hàng)
    @Override
    @Transactional
    public boolean applyMomoFeePaymentResult(String momoOrderId, int resultCode, String transId) {

        ItemWithdrawalRequest withdrawal = itemWithdrawalRequestRepository
                .findByMomoOrderId(momoOrderId).orElse(null);

        if (withdrawal == null) {
            return false;
        }

        if (withdrawal.getFeePaidAt() != null) {
            log.info(">>>> IPN MoMo cho phi rut hang momoOrderId={} da duoc xu ly truoc do, bo qua",
                    momoOrderId);
            return true;
        }

        if (resultCode != 0) {
            log.warn(">>>> MoMo bao thanh toan phi rut hang khong thanh cong: momoOrderId={}, resultCode={}",
                    momoOrderId, resultCode);
            return true;
        }

        withdrawal.setFeePaymentMethod(WithdrawalFeePaymentMethod.MOMO);
        withdrawal.setFeePaidAt(Instant.now());
        itemWithdrawalRequestRepository.save(withdrawal);

        log.info(">>>> Da thanh toan phi rut hang qua MoMo: itemWithdrawalRequestId={}, transId={}",
                withdrawal.getId(), transId);
        return true;
    }

    // Webhook VNPay gọi khi có kết quả giao dịch trả phí rút hàng (dispatch từ VnPayServiceImpl.handleIpn
    // khi vnp_TxnRef không khớp Payment nào)
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
        itemWithdrawalRequestRepository.save(withdrawal);

        log.info(">>>> Da thanh toan phi rut hang qua VNPay: itemWithdrawalRequestId={}, transactionNo={}",
                withdrawal.getId(), transactionNo);
        return true;
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

    // F-039: mỗi bút toán ví Member phải có một bút toán đối ứng ở quỹ nền tảng, ngược dấu
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

    // Sắp xếp field theo tên, bỏ field rỗng, nối "key=value" (value url-encode) bằng "&" - công thức
    // vnp_SecureHash của VNPay, giống hệt VnPayServiceImpl
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

    private String signHmacSHA256(String data, String key) throws Exception {
        Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmacSHA256.init(secretKeySpec);

        byte[] hash = hmacSHA256.doFinal(data.getBytes(StandardCharsets.UTF_8));
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

    private ItemWithdrawalResponse toResponse(ItemWithdrawalRequest withdrawal) {
        return ItemWithdrawalResponse.builder()
                .id(withdrawal.getId().toString())
                .productId(withdrawal.getProduct().getId())
                .feeCharged(withdrawal.getFeeCharged())
                .status(withdrawal.getStatus().name())
                .pickupDeadline(withdrawal.getPickupDeadline())
                .build();
    }
}
