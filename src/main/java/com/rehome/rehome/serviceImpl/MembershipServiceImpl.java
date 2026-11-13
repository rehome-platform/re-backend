package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.component.AiTryOnQuotaGuard;
import com.rehome.rehome.component.FeaturedListingManager;
import com.rehome.rehome.component.LiveTryOnQuotaGuard;
import com.rehome.rehome.component.MembershipGatewayPaymentBuilder;
import com.rehome.rehome.component.MembershipQuotaResolver;
import com.rehome.rehome.component.MembershipQuotaResolver.EffectiveQuota;
import com.rehome.rehome.dto.request.SubscribeMembershipRequest;
import com.rehome.rehome.dto.response.AiTryOnQuotaResponse;
import com.rehome.rehome.dto.response.MembershipPlanResponse;
import com.rehome.rehome.dto.response.MembershipPurchaseResponse;
import com.rehome.rehome.dto.response.MyMembershipResponse;
import com.rehome.rehome.dto.response.PageResponse;
import com.rehome.rehome.dto.response.SubscribeMembershipResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.BillingCycle;
import com.rehome.rehome.entity.enums.ConsignmentFormStatus;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.PaymentStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.VoucherType;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.marketplace.PlatformFund;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.entity.membership.MemberSubscription;
import com.rehome.rehome.entity.membership.MembershipPayment;
import com.rehome.rehome.entity.membership.MembershipPlan;
import com.rehome.rehome.entity.membership.VoucherTemplate;
import com.rehome.rehome.entity.membership.VoucherTemplateTierRule;
import com.rehome.rehome.exception.AppException;
import com.rehome.rehome.exception.BadRequestException;
import com.rehome.rehome.exception.ConflictException;
import com.rehome.rehome.exception.ErrorCode;
import com.rehome.rehome.repository.ConsignmentFormRepository;
import com.rehome.rehome.repository.MemberSubscriptionRepository;
import com.rehome.rehome.repository.MemberVoucherRepository;
import com.rehome.rehome.repository.MembershipPaymentRepository;
import com.rehome.rehome.repository.MembershipPlanRepository;
import com.rehome.rehome.repository.PlatformFundRepository;
import com.rehome.rehome.repository.VoucherTemplateRepository;
import com.rehome.rehome.repository.WalletRepository;
import com.rehome.rehome.repository.WalletTransactionRepository;
import com.rehome.rehome.service.MembershipService;
import com.rehome.rehome.service.NotificationService;
import com.rehome.rehome.service.VoucherIssueService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.Year;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Mua / xem gói thành viên VIP.
 *
 * <h2>Quy tắc gia hạn và đổi hạng (đặc tả §2.3, chốt ở §17 mục 16)</h2>
 * <ul>
 *   <li>Mỗi Member tại một thời điểm có <b>tối đa 1</b> subscription {@code ACTIVE}.</li>
 *   <li>Mua khi đang còn hạn <b>cùng hạng</b> -> <b>cộng dồn</b> thời gian vào {@code expiresAt}
 *       hiện tại (không tạo dòng mới, để lịch sử hạn mức liền mạch).</li>
 *   <li>Mua khi đang còn hạn <b>khác hạng</b> -> gói cũ chuyển {@code REPLACED} và <b>dừng ngay,
 *       KHÔNG hoàn phần còn lại</b>; gói mới tính đủ từ bây giờ. Màn M-02 bắt buộc cảnh báo điều
 *       này trước khi người mua trả tiền.</li>
 *   <li>Voucher đã phát từ gói cũ <b>vẫn dùng được</b> tới khi hết hạn của chính nó - không thu hồi.</li>
 *   <li>Đổi hạng -> chạy lại set/clear {@code featured_until} theo quyền lợi của hạng mới.</li>
 * </ul>
 *
 * <h2>Pro-rata</h2>
 * Cố ý KHÔNG tính tiền chênh theo ngày khi đổi hạng (§1.4). Nếu sau này cần, chỗ sửa là
 * {@link #activate} - đừng rải logic tính tiền ra chỗ khác.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MembershipServiceImpl implements MembershipService {

    private final MembershipPlanRepository membershipPlanRepository;
    private final MemberSubscriptionRepository memberSubscriptionRepository;
    private final MembershipPaymentRepository membershipPaymentRepository;
    private final VoucherTemplateRepository voucherTemplateRepository;
    private final MemberVoucherRepository memberVoucherRepository;
    private final ConsignmentFormRepository consignmentFormRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final PlatformFundRepository platformFundRepository;
    private final MembershipQuotaResolver quotaResolver;
    private final LiveTryOnQuotaGuard liveTryOnQuotaGuard;
    private final AiTryOnQuotaGuard aiTryOnQuotaGuard;
    private final FeaturedListingManager featuredListingManager;
    private final MembershipGatewayPaymentBuilder gatewayPaymentBuilder;
    private final VoucherIssueService voucherIssueService;
    private final NotificationService notificationService;

    // Mã sắp hết hạn trong bao nhiêu ngày thì tính vào voucherSummary.expiringSoon và hiện badge
    // "Còn N ngày" ở M-04
    private static final int VOUCHER_EXPIRING_SOON_DAYS = 3;

    // ================================================================ §8.1 GET /memberships/plans

    @Override
    public List<MembershipPlanResponse> getPlans() {
        List<MembershipPlan> plans =
                membershipPlanRepository.findAllByActiveTrueAndPurchasableTrueOrderBySortOrderAsc();

        // Nạp một lần toàn bộ mẫu voucher đang bật rồi gom theo hạng, thay vì query cho từng gói
        List<VoucherTemplate> templates = voucherTemplateRepository.findAllByActiveTrue();

        List<MembershipPlanResponse> result = new ArrayList<>(plans.size());
        for (MembershipPlan plan : plans) {
            result.add(toPlanResponse(plan, templates));
        }
        return result;
    }

    private MembershipPlanResponse toPlanResponse(MembershipPlan plan, List<VoucherTemplate> templates) {
        List<MembershipPlanResponse.VoucherBenefit> vouchers = new ArrayList<>();
        for (VoucherTemplate t : templates) {
            for (VoucherTemplateTierRule rule : t.getTierRules()) {
                if (rule.getTier() == plan.getTier()) {
                    vouchers.add(MembershipPlanResponse.VoucherBenefit.builder()
                            .name(t.getName())
                            .type(t.getType())
                            .quantityPerMonth(rule.getQuantityPerMonth())
                            .build());
                }
            }
        }
        // SHIPPING trước ORDER_DISCOUNT cho thứ tự hiển thị ổn định giữa các lần gọi
        vouchers.sort(Comparator.comparing(v -> v.getType() == VoucherType.SHIPPING ? 0 : 1));

        return MembershipPlanResponse.builder()
                .id(plan.getId().toString())
                .tier(plan.getTier())
                .name(plan.getName())
                .description(plan.getDescription())
                .pricePerMonth(plan.getPricePerMonth())
                .pricePerYear(plan.getPricePerYear())
                .yearlySavingPercent(yearlySavingPercent(plan))
                .benefits(MembershipPlanResponse.Benefits.builder()
                        .liveTryonPerDay(plan.getLiveTryonPerDay())
                        .liveTryonPerMonth(plan.getLiveTryonPerMonth())
                        .liveTryonMaxSeconds(plan.getLiveTryonMaxSeconds())
                        .aiTryonPerDay(plan.getAiTryonPerDay())
                        .aiTryonPerMonth(plan.getAiTryonPerMonth())
                        .featuredListing(plan.getFeaturedListing())
                        .maxPendingConsignmentForms(plan.getMaxPendingConsignmentForms())
                        .build())
                .vouchers(vouchers)
                .sortOrder(plan.getSortOrder())
                .build();
    }

    /** Trả theo năm tiết kiệm bao nhiêu % so với trả 12 tháng. null khi gói không bán theo năm. */
    private static BigDecimal yearlySavingPercent(MembershipPlan plan) {
        BigDecimal yearly = plan.getPricePerYear();
        if (yearly == null || plan.getPricePerMonth().signum() <= 0) {
            return null;
        }
        BigDecimal twelveMonths = plan.getPricePerMonth().multiply(BigDecimal.valueOf(12));
        if (twelveMonths.signum() <= 0) {
            return null;
        }
        return twelveMonths.subtract(yearly)
                .multiply(BigDecimal.valueOf(100))
                .divide(twelveMonths, 1, RoundingMode.HALF_UP);
    }

    // ================================================================ §8.2 GET /memberships/me

    @Override
    public MyMembershipResponse getMyMembership(Account account) {
        EffectiveQuota quota = quotaResolver.resolve(account.getId());
        Instant now = Instant.now();

        LiveTryOnQuotaGuard.Usage live = liveTryOnQuotaGuard.usage(account.getId());
        AiTryOnQuotaGuard.Usage ai = aiTryOnQuotaGuard.usage(account.getId());

        long pendingForms = consignmentFormRepository.countByMember_IdAndStatus(
                account.getId(), ConsignmentFormStatus.PENDING);

        // Trạng thái/chu kỳ chỉ có nghĩa khi đang thực sự có gói; FREE thì để null để mobile biết
        // ẩn ngày hết hạn và đổi nút chính thành "Nâng cấp gói".
        MemberSubscription subscription = quota.isFree()
                ? null
                : memberSubscriptionRepository.findById(quota.subscriptionId()).orElse(null);

        return MyMembershipResponse.builder()
                .subscriptionId(quota.subscriptionId() == null ? null : quota.subscriptionId().toString())
                .tier(quota.tier())
                .tierName(quota.tierName())
                .status(subscription == null ? null : subscription.getStatus())
                .billingCycle(subscription == null ? null : subscription.getBillingCycle())
                .startedAt(quota.startedAt())
                .expiresAt(quota.expiresAt())
                .daysRemaining(daysRemaining(quota.expiresAt(), now))
                .quota(MyMembershipResponse.Quota.builder()
                        .liveTryon(MyMembershipResponse.TryOnQuota.builder()
                                .limitPerDay(quota.liveTryonPerDay())
                                .usedToday(live.usedToday())
                                .remainingToday(live.remainingToday())
                                .limitPerMonth(quota.liveTryonPerMonth())
                                .usedThisMonth(live.usedThisMonth())
                                .remainingThisMonth(live.remainingThisMonth())
                                .maxSeconds(quota.liveTryonMaxSeconds())
                                .build())
                        .aiTryon(MyMembershipResponse.TryOnQuota.builder()
                                .limitPerDay(quota.aiTryonPerDay())
                                .usedToday(ai.usedToday())
                                .remainingToday(ai.remainingToday())
                                .limitPerMonth(quota.aiTryonPerMonth())
                                .usedThisMonth(ai.usedThisMonth())
                                .remainingThisMonth(ai.remainingThisMonth())
                                .build())
                        .featuredListing(MyMembershipResponse.FeaturedListing.builder()
                                .enabled(quota.featuredListing())
                                .productCount(featuredListingManager.countFeatured(account.getId()))
                                .build())
                        .consignmentForms(MyMembershipResponse.ConsignmentForms.builder()
                                .limit(quota.maxPendingConsignmentForms())
                                .pending(pendingForms)
                                .remaining((int) Math.max(0,
                                        quota.maxPendingConsignmentForms() - pendingForms))
                                .build())
                        .build())
                .voucherSummary(MyMembershipResponse.VoucherSummary.builder()
                        .unused(memberVoucherRepository.countByMember_IdAndStatus(
                                account.getId(), MemberVoucherStatus.UNUSED))
                        .expiringSoon(memberVoucherRepository
                                .countByMember_IdAndStatusAndExpiresAtBefore(
                                        account.getId(), MemberVoucherStatus.UNUSED,
                                        now.plus(VOUCHER_EXPIRING_SOON_DAYS, ChronoUnit.DAYS)))
                        .build())
                .build();
    }

    /** Làm tròn LÊN: gói hết hạn trong hôm nay vẫn hiện "còn 1 ngày" chứ không phải 0. */
    private static Integer daysRemaining(Instant expiresAt, Instant now) {
        if (expiresAt == null) {
            return null;
        }
        if (!expiresAt.isAfter(now)) {
            return 0;
        }
        long hours = Duration.between(now, expiresAt).toHours();
        return (int) ((hours + 23) / 24);
    }

    // ================================================================ §8.3 POST /memberships/subscribe

    @Override
    @Transactional
    public SubscribeMembershipResponse subscribe(Account account, SubscribeMembershipRequest request) {

        if (account.getRole() != Role.MEMBER) {
            // Tổ chức mua gói là ngoài phạm vi (§1.4). Chặn ở service chứ không chỉ dựa vào
            // @PreAuthorize, để luồng Admin grant sau này gọi vào đây cũng không lách được.
            throw new BadRequestException("Chỉ tài khoản Member mua được gói thành viên.");
        }
        if (request == null || request.getPlanId() == null) {
            throw new BadRequestException("planId là bắt buộc.");
        }
        BillingCycle cycle = request.getBillingCycle() == null ? BillingCycle.MONTHLY : request.getBillingCycle();
        OrderPaymentMethod method = request.getPaymentMethod();
        if (method == null) {
            throw new BadRequestException("paymentMethod là bắt buộc.");
        }
        if (method != OrderPaymentMethod.WALLET && !StringUtils.hasText(request.getRedirectUrl())) {
            throw new BadRequestException("redirectUrl là bắt buộc khi thanh toán qua MoMo/VNPay.");
        }

        MembershipPlan plan = membershipPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new AppException(ErrorCode.MEMBERSHIP_PLAN_NOT_FOUND));            // 404
        if (!Boolean.TRUE.equals(plan.getActive()) || !Boolean.TRUE.equals(plan.getPurchasable())) {
            throw new AppException(ErrorCode.MEMBERSHIP_PLAN_NOT_PURCHASABLE);                        // 409
        }

        BigDecimal price = cycle == BillingCycle.YEARLY ? plan.getPricePerYear() : plan.getPricePerMonth();
        if (cycle == BillingCycle.YEARLY && price == null) {
            throw new AppException(ErrorCode.MEMBERSHIP_PLAN_YEARLY_NOT_AVAILABLE);                   // 400
        }

        // Một giao dịch chờ thanh toán là đủ: cho mở nhiều link cùng lúc sẽ dẫn tới hai lần thu
        // tiền cho hai subscription của cùng một người.
        if (!memberSubscriptionRepository
                .findAllByMember_IdAndStatus(account.getId(), SubscriptionStatus.PENDING_PAYMENT)
                .isEmpty()) {
            throw new AppException(ErrorCode.MEMBERSHIP_PAYMENT_PENDING);                             // 409
        }

        // Khóa dòng ACTIVE trước khi quyết định cộng dồn / thay thế: hai request mua song song
        // phải nối đuôi nhau, nếu không Member sẽ có 2 gói ACTIVE.
        MemberSubscription current = memberSubscriptionRepository
                .findActiveByMemberIdForUpdate(account.getId())
                .filter(s -> s.isEffectiveAt(Instant.now()))
                .orElse(null);

        if (method == OrderPaymentMethod.WALLET) {
            return subscribeByWallet(account, plan, cycle, price, current);
        }
        return subscribeByGateway(account, plan, cycle, price, method, request.getRedirectUrl());
    }

    // ---- Trả bằng ví: thu tiền và kích hoạt trong cùng một transaction ----

    private SubscribeMembershipResponse subscribeByWallet(Account account, MembershipPlan plan,
                                                          BillingCycle cycle, BigDecimal price,
                                                          MemberSubscription current) {
        // Khóa ví trước khi đọc số dư, cùng cơ chế với OrderServiceImpl.payOrders
        Wallet wallet = walletRepository.findByMemberIdForUpdate(account.getId())
                .orElseThrow(() -> new ConflictException(
                        "INSUFFICIENT_BALANCE: ví chưa có số dư.", "INSUFFICIENT_BALANCE"));

        // Tiền đang khóa cho yêu cầu rút không được tiêu tiếp
        BigDecimal available = wallet.getBalance().subtract(wallet.getLockedBalance());
        if (price.compareTo(available) > 0) {
            throw new ConflictException(
                    "INSUFFICIENT_BALANCE: ví không đủ số dư khả dụng để mua gói.", "INSUFFICIENT_BALANCE");
        }

        MembershipTier replacedTier = replacePreviousIfDifferentTier(current, plan);
        MemberSubscription subscription = activate(account, plan, cycle, price, current, null);

        // Chỉ trừ ví SAU khi kích hoạt thành công; price = 0 (gói miễn phí Admin cấu hình) thì
        // không ghi bút toán rỗng vào sổ.
        if (price.signum() > 0) {
            wallet.setBalance(wallet.getBalance().subtract(price));
            walletRepository.save(wallet);

            WalletTransaction purchase = WalletTransaction.builder()
                    .transactionNo(generateTransactionNo())
                    .walletType(WalletType.MEMBER)
                    .walletId(wallet.getId())
                    .type(WalletTransactionType.MEMBERSHIP_PURCHASE)
                    .amount(price.negate())
                    .balanceAfter(wallet.getBalance())
                    .status(TransactionStatus.SUCCESS)
                    .note("Mua " + plan.getName() + " " + cycleLabel(cycle))
                    .build();
            walletTransactionRepository.save(purchase);
            // Dòng đối ứng bên quỹ nền tảng, ngược dấu, để sổ sách luôn cân (F-039).
            // Loại dòng đối ứng chính là MEMBERSHIP_PURCHASE nên đối soát quỹ vẫn tính nó -
            // tiền thật có đi vào quỹ. MEMBERSHIP_REVENUE chỉ dùng cho tiền từ cổng (xem
            // recordMembershipRevenue), nơi KHÔNG có dòng Member nào đối ứng.
            recordPlatformCounterpart(purchase);
        }

        MembershipPayment payment = membershipPaymentRepository.save(MembershipPayment.builder()
                .subscription(subscription)
                .member(account)
                .amount(price)
                .method(OrderPaymentMethod.WALLET)
                .status(PaymentStatus.SUCCESS)
                .paidAt(Instant.now())
                .build());

        int vouchersIssued = voucherIssueService.issueForSubscription(subscription);
        notifyActivated(account, subscription, vouchersIssued);

        log.info(">>>> Mua goi bang vi thanh cong: memberId={}, tier={}, cycle={}, paymentId={}",
                account.getId(), plan.getTier(), cycle, payment.getId());

        return SubscribeMembershipResponse.builder()
                .subscriptionId(subscription.getId().toString())
                .status(subscription.getStatus())
                .tier(subscription.getTier())
                .tierName(plan.getName())
                .expiresAt(subscription.getExpiresAt())
                .payUrl(null)
                .vouchersIssued(vouchersIssued)
                .replacedTier(replacedTier)
                .build();
    }

    // ---- Trả qua cổng: chỉ tạo subscription PENDING_PAYMENT, chờ IPN ----

    private SubscribeMembershipResponse subscribeByGateway(Account account, MembershipPlan plan,
                                                            BillingCycle cycle, BigDecimal price,
                                                            OrderPaymentMethod method, String redirectUrl) {
        // CHƯA đụng tới gói đang chạy: người mua có thể bỏ giữa đường, lúc đó gói cũ phải còn
        // nguyên. Việc chuyển REPLACED/cộng dồn nằm ở applyGatewayPaymentSuccess.
        MemberSubscription pending = memberSubscriptionRepository.save(
                newSubscriptionFromPlan(account, plan, cycle, price, null));

        MembershipPayment payment = MembershipPayment.builder()
                .subscription(pending)
                .member(account)
                .amount(price)
                .method(method)
                .status(PaymentStatus.PENDING)
                .build();

        String payUrl = method == OrderPaymentMethod.MOMO
                ? gatewayPaymentBuilder.createMomoPayment(payment, redirectUrl)
                : gatewayPaymentBuilder.createVnPayPayment(payment, redirectUrl, null);
        membershipPaymentRepository.save(payment);

        return SubscribeMembershipResponse.builder()
                .subscriptionId(pending.getId().toString())
                .status(pending.getStatus())
                .tier(pending.getTier())
                .tierName(plan.getName())
                .expiresAt(null)
                .payUrl(payUrl)
                .vouchersIssued(0)
                .build();
    }

    // ================================================================ Kích hoạt

    /**
     * Dựng dòng subscription mới từ plan, snapshot toàn bộ hạn mức. Chưa set started/expires -
     * {@link #activate} làm việc đó, vì luồng cổng tạo dòng này trước khi biết có thu được tiền hay không.
     */
    private MemberSubscription newSubscriptionFromPlan(Account account, MembershipPlan plan,
                                                        BillingCycle cycle, BigDecimal price,
                                                        Account grantedByAdmin) {
        return MemberSubscription.builder()
                .member(account)
                .plan(plan)
                .tier(plan.getTier())
                .billingCycle(cycle)
                .pricePaid(price == null ? BigDecimal.ZERO : price)
                .liveTryonPerDay(plan.getLiveTryonPerDay())
                .liveTryonPerMonth(plan.getLiveTryonPerMonth())
                .liveTryonMaxSeconds(plan.getLiveTryonMaxSeconds())
                .aiTryonPerDay(plan.getAiTryonPerDay())
                .aiTryonPerMonth(plan.getAiTryonPerMonth())
                .featuredListing(plan.getFeaturedListing())
                .maxPendingConsignmentForms(plan.getMaxPendingConsignmentForms())
                .status(SubscriptionStatus.PENDING_PAYMENT)
                .grantedByAdmin(grantedByAdmin)
                .build();
    }

    /**
     * Chuyển gói cũ sang {@code REPLACED} khi mua gói KHÁC hạng. Không hoàn phần còn lại (§17 mục 16).
     *
     * @return hạng của gói vừa bị thay thế, hoặc null nếu không có gì bị thay
     */
    private MembershipTier replacePreviousIfDifferentTier(MemberSubscription current, MembershipPlan newPlan) {
        if (current == null || current.getTier() == newPlan.getTier()) {
            return null;
        }
        current.setStatus(SubscriptionStatus.REPLACED);
        current.setCancelledAt(Instant.now());
        current.setCancelReason("Thay thế bởi gói " + newPlan.getName());
        memberSubscriptionRepository.save(current);
        log.info(">>>> Doi hang giua ky: memberId={}, {} -> {}, khong hoan phan con lai",
                current.getMember().getId(), current.getTier(), newPlan.getTier());
        return current.getTier();
    }

    /**
     * Kích hoạt gói. Hai nhánh:
     * <ul>
     *   <li><b>Cùng hạng còn hạn</b> -> cộng dồn vào {@code expiresAt} của dòng ĐANG CÓ và trả về
     *       chính dòng đó; không tạo dòng mới. Snapshot hạn mức cũng được làm mới theo plan hiện
     *       tại nếu plan đã tăng - người gia hạn hưởng mức mới ngay.</li>
     *   <li><b>Khác hạng hoặc chưa có gói</b> -> dòng mới bắt đầu từ bây giờ.</li>
     * </ul>
     * Cuối cùng luôn chạy lại set/clear {@code featured_until} theo quyền lợi của hạng đang hiệu lực.
     */
    private MemberSubscription activate(Account account, MembershipPlan plan, BillingCycle cycle,
                                         BigDecimal price, MemberSubscription current,
                                         Account grantedByAdmin) {
        Instant now = Instant.now();
        int months = cycle.getMonths();

        if (current != null && current.getTier() == plan.getTier()
                && current.getStatus() == SubscriptionStatus.ACTIVE) {
            // Cộng dồn: mốc tính là expiresAt hiện tại, không phải now - mua sớm không bị mất ngày
            current.setExpiresAt(plusMonths(current.getExpiresAt(), months));
            current.setBillingCycle(cycle);
            current.setPricePaid(price == null ? BigDecimal.ZERO : price);
            // Nhắc lại ở kỳ mới
            current.setExpiryReminderSentAt(null);
            applyPlanSnapshotIfHigher(current, plan);
            memberSubscriptionRepository.save(current);
            featuredListingManager.applyForSubscription(current);
            return current;
        }

        MemberSubscription fresh = newSubscriptionFromPlan(account, plan, cycle, price, grantedByAdmin);
        fresh.setStatus(SubscriptionStatus.ACTIVE);
        fresh.setStartedAt(now);
        fresh.setExpiresAt(plusMonths(now, months));
        memberSubscriptionRepository.save(fresh);
        featuredListingManager.applyForSubscription(fresh);
        return fresh;
    }

    /**
     * Gia hạn cùng hạng thì làm mới snapshot, nhưng CHỈ theo hướng tăng: Admin đã giảm hạn mức thì
     * giảm "chỉ áp từ kỳ sau" - mà kỳ sau chính là lần gia hạn này, nên ở đây lấy nguyên giá trị
     * mới của plan cho mọi field. Dùng plan hiện tại làm nguồn là đúng ý nghĩa "kỳ mới".
     */
    private static void applyPlanSnapshotIfHigher(MemberSubscription s, MembershipPlan plan) {
        s.setLiveTryonPerDay(plan.getLiveTryonPerDay());
        s.setLiveTryonPerMonth(plan.getLiveTryonPerMonth());
        s.setLiveTryonMaxSeconds(plan.getLiveTryonMaxSeconds());
        s.setAiTryonPerDay(plan.getAiTryonPerDay());
        s.setAiTryonPerMonth(plan.getAiTryonPerMonth());
        s.setFeaturedListing(plan.getFeaturedListing());
        s.setMaxPendingConsignmentForms(plan.getMaxPendingConsignmentForms());
    }

    /**
     * Cộng tháng theo lịch (giờ VN) thay vì cộng 30 ngày, để gói mua ngày 15 luôn hết hạn ngày 15
     * tháng sau. Ngày 31 cộng sang tháng chỉ có 30 ngày thì {@code java.time} tự kẹp về ngày cuối tháng.
     */
    private static Instant plusMonths(Instant from, int months) {
        return from.atZone(com.rehome.rehome.util.VnTimeWindow.VN_ZONE)
                .plusMonths(months)
                .toInstant();
    }

    // ================================================================ §8.4 Lịch sử mua gói

    @Override
    public PageResponse<MembershipPurchaseResponse> getPurchaseHistory(Account account, int page, int size) {
        return PageResponse.from(membershipPaymentRepository
                .findAllByMember_IdOrderByCreatedAtDesc(account.getId(),
                        PageRequest.of(Math.max(page, 0), Math.max(size, 1)))
                .map(p -> {
                    MemberSubscription s = p.getSubscription();
                    return MembershipPurchaseResponse.builder()
                            .id(p.getId().toString())
                            .subscriptionId(s.getId().toString())
                            .tier(s.getTier())
                            .tierName(s.getPlan().getName())
                            .billingCycle(s.getBillingCycle())
                            .amount(p.getAmount())
                            .method(p.getMethod())
                            .status(p.getStatus())
                            .paidAt(p.getPaidAt())
                            .startedAt(s.getStartedAt())
                            .expiresAt(s.getExpiresAt())
                            .build();
                }));
    }

    // ================================================================ §8.8 GET /tryon/quota

    @Override
    public AiTryOnQuotaResponse getAiTryOnQuota(Account account) {
        AiTryOnQuotaGuard.Usage usage = aiTryOnQuotaGuard.usage(account.getId());
        EffectiveQuota quota = usage.quota();
        return AiTryOnQuotaResponse.builder()
                .tier(quota.tier())
                .tierName(quota.tierName())
                .limitPerDay(quota.aiTryonPerDay())
                .usedToday(usage.usedToday())
                .remainingToday(usage.remainingToday())
                .limitPerMonth(quota.aiTryonPerMonth())
                .usedThisMonth(usage.usedThisMonth())
                .remainingThisMonth(usage.remainingThisMonth())
                .canGenerate(usage.canGenerate())
                .upsellTier(aiTryOnQuotaGuard.upsellTier(quota))
                .build();
    }

    // ================================================================ §8.12 Kết quả từ cổng

    @Override
    @Transactional
    public boolean applyMomoResult(String momoOrderId, int resultCode, String transactionId) {
        if (!MembershipGatewayPaymentBuilder.isMembershipRef(momoOrderId)) {
            return false;
        }
        MembershipPayment payment = membershipPaymentRepository.findByMomoOrderId(momoOrderId).orElse(null);
        if (payment == null) {
            log.warn(">>>> IPN mua goi cho momoOrderId={} khong khop dong membership_payments nao", momoOrderId);
            return false;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info(">>>> IPN mua goi cho momoOrderId={} da xu ly truoc do, bo qua", momoOrderId);
            return true;
        }

        if (resultCode != 0) {
            payment.setStatus(PaymentStatus.FAILED);
            membershipPaymentRepository.save(payment);
            failPendingSubscription(payment, "MoMo báo thất bại, resultCode=" + resultCode);
            return true;
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setMomoTransactionId(transactionId);
        payment.setPaidAt(Instant.now());
        membershipPaymentRepository.save(payment);

        applyGatewayPaymentSuccess(payment.getSubscription().getId(), OrderPaymentMethod.MOMO,
                "Hoàn tiền MoMo về sau khi giao dịch mua gói không còn chờ thanh toán");
        return true;
    }

    @Override
    @Transactional
    public boolean applyVnPayResult(String vnpTxnRef, String responseCode, String transactionNo) {
        if (!MembershipGatewayPaymentBuilder.isMembershipRef(vnpTxnRef)) {
            return false;
        }
        MembershipPayment payment = membershipPaymentRepository.findByVnpTxnRef(vnpTxnRef).orElse(null);
        if (payment == null) {
            log.warn(">>>> IPN mua goi cho vnp_TxnRef={} khong khop dong membership_payments nao", vnpTxnRef);
            return false;
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.info(">>>> IPN mua goi cho vnp_TxnRef={} da xu ly truoc do, bo qua", vnpTxnRef);
            return true;
        }

        if (!"00".equals(responseCode)) {
            payment.setStatus(PaymentStatus.FAILED);
            membershipPaymentRepository.save(payment);
            failPendingSubscription(payment, "VNPay báo thất bại, responseCode=" + responseCode);
            return true;
        }

        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setVnpTransactionNo(transactionNo);
        payment.setPaidAt(Instant.now());
        membershipPaymentRepository.save(payment);

        applyGatewayPaymentSuccess(payment.getSubscription().getId(), OrderPaymentMethod.VNPAY,
                "Hoàn tiền VNPay về sau khi giao dịch mua gói không còn chờ thanh toán");
        return true;
    }

    /**
     * Cổng báo đã nhận tiền. Quy tắc giống đơn hàng (xem {@code OrderService.applyGatewayPaymentSuccess}):
     * <b>cổng báo thành công không tự kích hoạt</b>.
     *
     * <ul>
     *   <li>Còn {@code PENDING_PAYMENT} -> kích hoạt (cộng dồn hoặc thay thế gói cũ như luồng ví).</li>
     *   <li>Đã rời {@code PENDING_PAYMENT} (bị hủy, hoặc đã kích hoạt bằng đường khác) -> tiền là
     *       tiền thật nên <b>hoàn vào ví Member</b>; cờ chống hoàn hai lần là chính trạng thái
     *       {@code CANCELLED} + {@code cancelReason} đã ghi "đã hoàn".</li>
     * </ul>
     */
    @Override
    @Transactional
    public void applyGatewayPaymentSuccess(UUID subscriptionId, OrderPaymentMethod method, String note) {
        MemberSubscription subscription = memberSubscriptionRepository.findById(subscriptionId).orElse(null);
        if (subscription == null) {
            log.warn(">>>> Ket qua thanh toan thanh cong cho subscriptionId={} khong con dong tuong ung",
                    subscriptionId);
            return;
        }

        if (subscription.getStatus() == SubscriptionStatus.PENDING_PAYMENT) {
            Account account = subscription.getMember();
            MembershipPlan plan = subscription.getPlan();

            MemberSubscription current = memberSubscriptionRepository
                    .findActiveByMemberIdForUpdate(account.getId())
                    .filter(s -> s.isEffectiveAt(Instant.now()))
                    .orElse(null);
            int months = subscription.getBillingCycle().getMonths();
            MemberSubscription effective;

            if (current != null && current.getTier() == plan.getTier()) {
                // Gia hạn CÙNG hạng: cộng dồn vào dòng đang chạy (mốc là expiresAt hiện tại, mua
                // sớm không mất ngày), rồi đóng dòng PENDING lại - nó không còn vai trò gì.
                // Payment được trỏ sang dòng thật để lịch sử mua gói hiện đúng gói nào đã gia hạn.
                current.setExpiresAt(plusMonths(current.getExpiresAt(), months));
                current.setBillingCycle(subscription.getBillingCycle());
                current.setPricePaid(subscription.getPricePaid());
                current.setExpiryReminderSentAt(null);
                applyPlanSnapshotIfHigher(current, plan);
                memberSubscriptionRepository.save(current);
                featuredListingManager.applyForSubscription(current);

                subscription.setStatus(SubscriptionStatus.REPLACED);
                subscription.setCancelledAt(Instant.now());
                subscription.setCancelReason("Gộp vào gói đang dùng khi gia hạn cùng hạng");
                memberSubscriptionRepository.save(subscription);
                for (MembershipPayment p : membershipPaymentRepository
                        .findAllBySubscription_IdOrderByCreatedAtAsc(subscription.getId())) {
                    p.setSubscription(current);
                    membershipPaymentRepository.save(p);
                }
                effective = current;
            } else {
                // Khác hạng -> gói cũ dừng ngay, không hoàn phần còn lại (§17 mục 16).
                // Kích hoạt CHÍNH dòng PENDING này, không tạo dòng mới: dòng đó đã mang snapshot
                // hạn mức và giá đã trả từ lúc mở giao dịch, và membership_payments đang trỏ vào nó.
                replacePreviousIfDifferentTier(current, plan);

                Instant now = Instant.now();
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                subscription.setStartedAt(now);
                subscription.setExpiresAt(plusMonths(now, months));
                memberSubscriptionRepository.save(subscription);
                featuredListingManager.applyForSubscription(subscription);
                effective = subscription;
            }

            // Tiền đi từ ngoài vào ví quỹ: bên MEMBER KHÔNG có bút toán nào (đúng convention hiện
            // tại của thanh toán cổng), chỉ ghi 1 dòng doanh thu bên PLATFORM.
            recordMembershipRevenue(subscription.getPricePaid(),
                    "Doanh thu bán " + plan.getName() + " (" + method + ")");

            int vouchersIssued = voucherIssueService.issueForSubscription(effective);
            notifyActivated(account, effective, vouchersIssued);

            log.info(">>>> Kich hoat goi sau khi {} bao thanh cong: memberId={}, tier={}",
                    method, account.getId(), plan.getTier());
            return;
        }

        // Đã hoàn tiền trước đó rồi thì không hoàn thêm lần nữa
        if (subscription.getCancelReason() != null
                && subscription.getCancelReason().startsWith(REFUNDED_MARKER)) {
            log.warn(">>>> Giao dich mua goi {} da duoc hoan tien truoc do, bo qua ket qua muon tu {}",
                    subscriptionId, method);
            return;
        }

        refundToWallet(subscription, note);
        log.warn(">>>> Tien {} ve muon cho giao dich mua goi {} dang o trang thai {} - da hoan {} vao vi",
                method, subscriptionId, subscription.getStatus(), subscription.getPricePaid());
    }

    /** Tiền tố ghi vào {@code cancel_reason} để chống hoàn tiền hai lần. */
    private static final String REFUNDED_MARKER = "[ĐÃ HOÀN]";

    private void refundToWallet(MemberSubscription subscription, String note) {
        BigDecimal amount = subscription.getPricePaid();
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        Account member = subscription.getMember();
        Wallet wallet = walletRepository.findByMemberIdForUpdate(member.getId())
                .orElseGet(() -> walletRepository.save(Wallet.builder().member(member).build()));
        wallet.setBalance(wallet.getBalance().add(amount));
        walletRepository.save(wallet);

        WalletTransaction refund = WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.MEMBER)
                .walletId(wallet.getId())
                .type(WalletTransactionType.REFUND)
                .amount(amount)
                .balanceAfter(wallet.getBalance())
                .status(TransactionStatus.SUCCESS)
                .note(note)
                .build();
        walletTransactionRepository.save(refund);
        recordPlatformCounterpart(refund);

        subscription.setCancelReason(REFUNDED_MARKER + " " + note);
        memberSubscriptionRepository.save(subscription);
    }

    private void failPendingSubscription(MembershipPayment payment, String reason) {
        MemberSubscription subscription = payment.getSubscription();
        if (subscription.getStatus() != SubscriptionStatus.PENDING_PAYMENT) {
            return;
        }
        subscription.setStatus(SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(Instant.now());
        subscription.setCancelReason(reason);
        memberSubscriptionRepository.save(subscription);
        log.info(">>>> Huy giao dich mua goi cho subscriptionId={}: {}", subscription.getId(), reason);
    }

    // ================================================================ Ghi sổ ví

    /**
     * Doanh thu bán gói khi tiền đến từ cổng thanh toán: 1 dòng PLATFORM dương,
     * <b>không có dòng Member đối ứng và không đổi số dư quỹ</b> - giống hệt cách
     * {@code COMMISSION_REVENUE} đang làm ở {@code OrderServiceImpl}. Đối soát quỹ bỏ qua loại dòng
     * này (xem {@code AdminReportQueryRepository.REVENUE_RECOGNITION_TYPES}).
     *
     * <p>Lưu ý: mua bằng VÍ thì KHÔNG đi qua đây - lúc đó đã có cặp bút toán
     * {@code MEMBERSHIP_PURCHASE} (Member, âm) + đối ứng (PLATFORM, dương) nên ghi thêm dòng doanh
     * thu nữa là ghi nhận trùng.
     */
    private void recordMembershipRevenue(BigDecimal amount, String note) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        PlatformFund fund = platformFundRepository.findTopByOrderByIdAsc()
                .orElseGet(() -> platformFundRepository.save(PlatformFund.builder().build()));

        walletTransactionRepository.save(WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.PLATFORM)
                .walletId(fund.getId())
                .type(WalletTransactionType.MEMBERSHIP_REVENUE)
                .amount(amount)
                .balanceAfter(fund.getBalance())
                .status(TransactionStatus.SUCCESS)
                .note(note)
                .build());
    }

    // F-039: mỗi bút toán ví Member phải có một bút toán đối ứng ở quỹ nền tảng, ngược dấu
    private void recordPlatformCounterpart(WalletTransaction memberTransaction) {
        PlatformFund fund = platformFundRepository.findTopByOrderByIdAsc()
                .orElseGet(() -> platformFundRepository.save(PlatformFund.builder().build()));

        BigDecimal counterpartAmount = memberTransaction.getAmount().negate();
        fund.setBalance(fund.getBalance().add(counterpartAmount));
        platformFundRepository.save(fund);

        walletTransactionRepository.save(WalletTransaction.builder()
                .transactionNo(generateTransactionNo())
                .walletType(WalletType.PLATFORM)
                .walletId(fund.getId())
                .type(memberTransaction.getType())
                .amount(counterpartAmount)
                .balanceAfter(fund.getBalance())
                .counterpartTransaction(memberTransaction)
                .status(TransactionStatus.SUCCESS)
                .note(memberTransaction.getNote())
                .build());
    }

    private String generateTransactionNo() {
        String prefix = "WT-" + Year.now(ZoneOffset.UTC).getValue() + "-";
        long seq = walletTransactionRepository.countByTransactionNoStartingWith(prefix) + 1;
        return prefix + String.format("%06d", seq);
    }

    // ================================================================ Thông báo

    private void notifyActivated(Account account, MemberSubscription subscription, int vouchersIssued) {
        notificationService.notify(account, NotificationType.MEMBERSHIP_EVENT,
                "Đăng ký gói thành công",
                "Bạn đang dùng " + subscription.getPlan().getName() + ", hiệu lực đến "
                        + formatVnDate(subscription.getExpiresAt()) + ".",
                null, null, null);

        if (vouchersIssued > 0) {
            notificationService.notify(account, NotificationType.VOUCHER_ISSUED,
                    "Bạn vừa nhận " + vouchersIssued + " voucher",
                    "Mở ví voucher để xem các mã vừa được tặng theo gói thành viên.",
                    null, null, null);
        }
    }

    static String formatVnDate(Instant instant) {
        if (instant == null) {
            return "—";
        }
        return instant.atZone(com.rehome.rehome.util.VnTimeWindow.VN_ZONE)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private static String cycleLabel(BillingCycle cycle) {
        return cycle == BillingCycle.YEARLY ? "1 năm" : "1 tháng";
    }
}
