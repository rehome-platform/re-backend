package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rehome.rehome.component.AiTryOnQuotaGuard;
import com.rehome.rehome.component.FeaturedListingManager;
import com.rehome.rehome.component.LiveTryOnQuotaGuard;
import com.rehome.rehome.component.MembershipGatewayPaymentBuilder;
import com.rehome.rehome.component.MembershipQuotaResolver;
import com.rehome.rehome.dto.request.SubscribeMembershipRequest;
import com.rehome.rehome.dto.response.SubscribeMembershipResponse;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.BillingCycle;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.OrderPaymentMethod;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.marketplace.PlatformFund;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import com.rehome.rehome.entity.membership.MemberSubscription;
import com.rehome.rehome.entity.membership.MembershipPayment;
import com.rehome.rehome.entity.membership.MembershipPlan;
import com.rehome.rehome.exception.AppException;
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
import com.rehome.rehome.service.NotificationService;
import com.rehome.rehome.service.VoucherIssueService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Luồng mua gói thành viên. Trọng tâm là <b>ba quyết định nghiệp vụ dễ làm sai</b>:
 * <ol>
 *   <li>Mua cùng hạng khi còn hạn thì <b>cộng dồn</b> vào dòng đang có, không tạo dòng mới.</li>
 *   <li>Mua khác hạng khi còn hạn thì gói cũ chuyển {@code REPLACED} và <b>không hoàn phần còn lại</b>.</li>
 *   <li>Cổng báo thành công khi subscription đã rời {@code PENDING_PAYMENT} thì <b>hoàn tiền vào ví</b>,
 *       không kích hoạt - và không hoàn hai lần.</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipServiceImplTest {

    @Mock private MembershipPlanRepository membershipPlanRepository;
    @Mock private MemberSubscriptionRepository memberSubscriptionRepository;
    @Mock private MembershipPaymentRepository membershipPaymentRepository;
    @Mock private VoucherTemplateRepository voucherTemplateRepository;
    @Mock private MemberVoucherRepository memberVoucherRepository;
    @Mock private ConsignmentFormRepository consignmentFormRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private WalletTransactionRepository walletTransactionRepository;
    @Mock private PlatformFundRepository platformFundRepository;
    @Mock private MembershipQuotaResolver quotaResolver;
    @Mock private LiveTryOnQuotaGuard liveTryOnQuotaGuard;
    @Mock private AiTryOnQuotaGuard aiTryOnQuotaGuard;
    @Mock private FeaturedListingManager featuredListingManager;
    @Mock private MembershipGatewayPaymentBuilder gatewayPaymentBuilder;
    @Mock private VoucherIssueService voucherIssueService;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private MembershipServiceImpl service;

    private Account member;
    private MembershipPlan goldPlan;
    private MembershipPlan silverPlan;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        member = Account.builder()
                .id(UUID.randomUUID())
                .email("member@example.com")
                .role(Role.MEMBER)
                .build();

        goldPlan = plan(MembershipTier.GOLD, "Thành viên Vàng", "59000", "590000");
        silverPlan = plan(MembershipTier.SILVER, "Thành viên Bạc", "29000", "290000");

        wallet = Wallet.builder()
                .id(UUID.randomUUID())
                .member(member)
                .balance(new BigDecimal("500000"))
                .lockedBalance(BigDecimal.ZERO)
                .build();

        when(membershipPlanRepository.findById(goldPlan.getId())).thenReturn(Optional.of(goldPlan));
        when(membershipPlanRepository.findById(silverPlan.getId())).thenReturn(Optional.of(silverPlan));
        when(memberSubscriptionRepository.findAllByMember_IdAndStatus(
                member.getId(), SubscriptionStatus.PENDING_PAYMENT)).thenReturn(List.of());
        when(memberSubscriptionRepository.findActiveByMemberIdForUpdate(member.getId()))
                .thenReturn(Optional.empty());
        when(walletRepository.findByMemberIdForUpdate(member.getId())).thenReturn(Optional.of(wallet));
        when(platformFundRepository.findTopByOrderByIdAsc())
                .thenReturn(Optional.of(PlatformFund.builder().id(UUID.randomUUID())
                        .balance(new BigDecimal("1000000")).build()));
        when(walletTransactionRepository.countByTransactionNoStartingWith(anyString())).thenReturn(0L);
        // Lưu subscription: gán id như DB để các bước sau (so sánh dòng cộng dồn) hoạt động
        when(memberSubscriptionRepository.save(any(MemberSubscription.class))).thenAnswer(inv -> {
            MemberSubscription s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
        when(membershipPaymentRepository.save(any(MembershipPayment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(voucherTemplateRepository.findAllByActiveTrue()).thenReturn(List.of());
        when(voucherIssueService.issueForSubscription(any())).thenReturn(2);
    }

    // ================================================================ Trả bằng ví

    @Nested
    class WalletPurchase {

        @Test
        @DisplayName("Chưa có gói: tạo subscription ACTIVE, trừ ví đúng giá, ghi cặp bút toán, phát voucher")
        void firstPurchase_activatesAndDebitsWallet() {
            SubscribeMembershipResponse response = service.subscribe(member, request(
                    goldPlan.getId(), BillingCycle.MONTHLY, OrderPaymentMethod.WALLET));

            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(response.getTier()).isEqualTo(MembershipTier.GOLD);
            assertThat(response.getPayUrl()).isNull();
            assertThat(response.getVouchersIssued()).isEqualTo(2);
            assertThat(response.getExpiresAt()).isAfter(Instant.now());

            // Ví bị trừ đúng giá tháng, không phải giá năm
            assertThat(wallet.getBalance()).isEqualByComparingTo("441000");

            // Một bút toán MEMBER âm + một bút toán đối ứng bên quỹ (F-039)
            ArgumentCaptor<WalletTransaction> txs = ArgumentCaptor.forClass(WalletTransaction.class);
            verify(walletTransactionRepository, times(2)).save(txs.capture());
            assertThat(txs.getAllValues().get(0).getType())
                    .isEqualTo(WalletTransactionType.MEMBERSHIP_PURCHASE);
            assertThat(txs.getAllValues().get(0).getAmount()).isEqualByComparingTo("-59000");
            assertThat(txs.getAllValues().get(1).getAmount()).isEqualByComparingTo("59000");

            verify(voucherIssueService).issueForSubscription(any());
            verify(featuredListingManager).applyForSubscription(any());
        }

        @Test
        @DisplayName("Snapshot hạn mức được chụp từ plan lúc mua, không đọc lại plan về sau")
        void snapshotsLimitsFromPlanAtPurchaseTime() {
            service.subscribe(member, request(goldPlan.getId(), BillingCycle.MONTHLY,
                    OrderPaymentMethod.WALLET));

            ArgumentCaptor<MemberSubscription> saved = ArgumentCaptor.forClass(MemberSubscription.class);
            verify(memberSubscriptionRepository).save(saved.capture());
            MemberSubscription s = saved.getValue();

            assertThat(s.getLiveTryonPerDay()).isEqualTo(10);
            assertThat(s.getLiveTryonMaxSeconds()).isEqualTo(15);
            assertThat(s.getAiTryonPerDay()).isEqualTo(40);
            assertThat(s.getMaxPendingConsignmentForms()).isEqualTo(15);
            assertThat(s.getFeaturedListing()).isTrue();
            assertThat(s.getPricePaid()).isEqualByComparingTo("59000");
            // Trần tháng seed null = không chặn
            assertThat(s.getLiveTryonPerMonth()).isNull();
            assertThat(s.getAiTryonPerMonth()).isNull();
        }

        @Test
        @DisplayName("Ví không đủ số dư khả dụng -> INSUFFICIENT_BALANCE 409, KHÔNG tạo subscription")
        void insufficientBalance_doesNotCreateSubscription() {
            wallet.setBalance(new BigDecimal("10000"));

            assertThatThrownBy(() -> service.subscribe(member, request(
                    goldPlan.getId(), BillingCycle.MONTHLY, OrderPaymentMethod.WALLET)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("INSUFFICIENT_BALANCE");

            verify(memberSubscriptionRepository, never()).save(any());
            verify(walletTransactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Tiền đang khóa cho yêu cầu rút không được tiêu để mua gói")
        void lockedBalance_isNotSpendable() {
            wallet.setBalance(new BigDecimal("100000"));
            wallet.setLockedBalance(new BigDecimal("60000"));   // khả dụng chỉ 40.000 < 59.000

            assertThatThrownBy(() -> service.subscribe(member, request(
                    goldPlan.getId(), BillingCycle.MONTHLY, OrderPaymentMethod.WALLET)))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("INSUFFICIENT_BALANCE");
        }

        @Test
        @DisplayName("Gia hạn CÙNG hạng khi còn hạn: cộng dồn vào expiresAt của dòng đang có, "
                + "KHÔNG tạo dòng mới và KHÔNG mất ngày còn lại")
        void sameTierRenewal_extendsExistingSubscription() {
            Instant currentExpiry = Instant.now().plus(10, ChronoUnit.DAYS);
            MemberSubscription current = activeSubscription(goldPlan, currentExpiry);
            when(memberSubscriptionRepository.findActiveByMemberIdForUpdate(member.getId()))
                    .thenReturn(Optional.of(current));

            SubscribeMembershipResponse response = service.subscribe(member, request(
                    goldPlan.getId(), BillingCycle.MONTHLY, OrderPaymentMethod.WALLET));

            assertThat(response.getSubscriptionId()).isEqualTo(current.getId().toString());
            assertThat(response.getReplacedTier()).isNull();
            // Mốc cộng dồn là expiresAt hiện tại, không phải now -> vẫn còn hơn 10 ngày
            assertThat(current.getExpiresAt()).isAfter(currentExpiry.plus(25, ChronoUnit.DAYS));
            assertThat(current.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        }

        @Test
        @DisplayName("Mua KHÁC hạng khi còn hạn: gói cũ -> REPLACED, gói mới tính đủ từ bây giờ, "
                + "KHÔNG hoàn phần còn lại của gói cũ")
        void differentTier_replacesWithoutRefund() {
            MemberSubscription current = activeSubscription(silverPlan,
                    Instant.now().plus(20, ChronoUnit.DAYS));
            when(memberSubscriptionRepository.findActiveByMemberIdForUpdate(member.getId()))
                    .thenReturn(Optional.of(current));

            SubscribeMembershipResponse response = service.subscribe(member, request(
                    goldPlan.getId(), BillingCycle.MONTHLY, OrderPaymentMethod.WALLET));

            assertThat(current.getStatus()).isEqualTo(SubscriptionStatus.REPLACED);
            assertThat(current.getCancelledAt()).isNotNull();
            assertThat(response.getReplacedTier()).isEqualTo(MembershipTier.SILVER);
            assertThat(response.getTier()).isEqualTo(MembershipTier.GOLD);

            // Ví chỉ bị TRỪ tiền gói mới - không có bút toán hoàn nào cho phần còn lại gói cũ
            assertThat(wallet.getBalance()).isEqualByComparingTo("441000");
            ArgumentCaptor<WalletTransaction> txs = ArgumentCaptor.forClass(WalletTransaction.class);
            verify(walletTransactionRepository, times(2)).save(txs.capture());
            assertThat(txs.getAllValues())
                    .noneMatch(t -> t.getType() == WalletTransactionType.REFUND);
        }

        @Test
        @DisplayName("Gói năm: trừ đúng giá năm, không phải giá tháng × 12")
        void yearlyCycle_chargesYearlyPrice() {
            wallet.setBalance(new BigDecimal("1000000"));

            SubscribeMembershipResponse response = service.subscribe(member, request(
                    goldPlan.getId(), BillingCycle.YEARLY, OrderPaymentMethod.WALLET));

            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(wallet.getBalance()).isEqualByComparingTo("410000");
        }
    }

    // ================================================================ Validate

    @Nested
    class Validation {

        @Test
        @DisplayName("Gói không bán (active = false) -> MEMBERSHIP_PLAN_NOT_PURCHASABLE 409")
        void inactivePlan_isRejected() {
            goldPlan.setActive(false);
            assertAppError(ErrorCode.MEMBERSHIP_PLAN_NOT_PURCHASABLE,
                    request(goldPlan.getId(), BillingCycle.MONTHLY, OrderPaymentMethod.WALLET));
        }

        @Test
        @DisplayName("Hạng FREE (purchasable = false) -> MEMBERSHIP_PLAN_NOT_PURCHASABLE 409")
        void freePlan_cannotBeBought() {
            MembershipPlan free = plan(MembershipTier.FREE, "Thành viên thường", "0", null);
            free.setPurchasable(false);
            when(membershipPlanRepository.findById(free.getId())).thenReturn(Optional.of(free));

            assertAppError(ErrorCode.MEMBERSHIP_PLAN_NOT_PURCHASABLE,
                    request(free.getId(), BillingCycle.MONTHLY, OrderPaymentMethod.WALLET));
        }

        @Test
        @DisplayName("Chọn gói năm mà plan không có pricePerYear -> MEMBERSHIP_PLAN_YEARLY_NOT_AVAILABLE 400")
        void yearlyNotSold_isRejected() {
            goldPlan.setPricePerYear(null);
            assertAppError(ErrorCode.MEMBERSHIP_PLAN_YEARLY_NOT_AVAILABLE,
                    request(goldPlan.getId(), BillingCycle.YEARLY, OrderPaymentMethod.WALLET));
        }

        @Test
        @DisplayName("Không tìm thấy gói -> MEMBERSHIP_PLAN_NOT_FOUND 404")
        void unknownPlan_isRejected() {
            assertAppError(ErrorCode.MEMBERSHIP_PLAN_NOT_FOUND,
                    request(UUID.randomUUID(), BillingCycle.MONTHLY, OrderPaymentMethod.WALLET));
        }

        @Test
        @DisplayName("Đang có giao dịch chờ thanh toán -> MEMBERSHIP_PAYMENT_PENDING 409. "
                + "Cho mở nhiều link cùng lúc sẽ dẫn tới thu tiền hai lần")
        void pendingPayment_blocksNewPurchase() {
            when(memberSubscriptionRepository.findAllByMember_IdAndStatus(
                    member.getId(), SubscriptionStatus.PENDING_PAYMENT))
                    .thenReturn(List.of(new MemberSubscription()));

            assertAppError(ErrorCode.MEMBERSHIP_PAYMENT_PENDING,
                    request(goldPlan.getId(), BillingCycle.MONTHLY, OrderPaymentMethod.WALLET));
        }

        @Test
        @DisplayName("Tài khoản ORGANIZATION không mua được gói (ngoài phạm vi §1.4)")
        void organizationAccount_cannotBuy() {
            Account org = Account.builder().id(UUID.randomUUID())
                    .email("org@example.com").role(Role.ORGANIZATION).build();

            assertThatThrownBy(() -> service.subscribe(org, request(
                    goldPlan.getId(), BillingCycle.MONTHLY, OrderPaymentMethod.WALLET)))
                    .hasMessageContaining("Member");
        }

        private void assertAppError(ErrorCode expected, SubscribeMembershipRequest request) {
            assertThatThrownBy(() -> service.subscribe(member, request))
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(expected);
        }
    }

    // ================================================================ Cổng thanh toán

    @Nested
    class Gateway {

        @Test
        @DisplayName("Trả MoMo: subscription PENDING_PAYMENT, trả payUrl, CHƯA trừ ví và CHƯA phát voucher")
        void momoPurchase_waitsForIpn() {
            when(gatewayPaymentBuilder.createMomoPayment(any(), eq("rehome://result")))
                    .thenReturn("https://momo.example/pay/abc");
            SubscribeMembershipRequest request = request(
                    goldPlan.getId(), BillingCycle.MONTHLY, OrderPaymentMethod.MOMO);
            request.setRedirectUrl("rehome://result");

            SubscribeMembershipResponse response = service.subscribe(member, request);

            assertThat(response.getStatus()).isEqualTo(SubscriptionStatus.PENDING_PAYMENT);
            assertThat(response.getPayUrl()).isEqualTo("https://momo.example/pay/abc");
            assertThat(response.getExpiresAt()).isNull();
            assertThat(response.getVouchersIssued()).isZero();

            assertThat(wallet.getBalance()).isEqualByComparingTo("500000");
            verify(voucherIssueService, never()).issueForSubscription(any());
            // Gói đang chạy KHÔNG bị đụng tới: người mua có thể bỏ giữa đường
            verify(featuredListingManager, never()).applyForSubscription(any());
        }

        @Test
        @DisplayName("Thiếu redirectUrl khi trả qua cổng -> 400")
        void gatewayWithoutRedirectUrl_isRejected() {
            assertThatThrownBy(() -> service.subscribe(member, request(
                    goldPlan.getId(), BillingCycle.MONTHLY, OrderPaymentMethod.VNPAY)))
                    .hasMessageContaining("redirectUrl");
        }

        @Test
        @DisplayName("IPN thành công khi còn PENDING_PAYMENT -> kích hoạt, ghi doanh thu, phát voucher")
        void ipnSuccess_activatesPendingSubscription() {
            MemberSubscription pending = MemberSubscription.builder()
                    .id(UUID.randomUUID())
                    .member(member)
                    .plan(goldPlan)
                    .tier(MembershipTier.GOLD)
                    .billingCycle(BillingCycle.MONTHLY)
                    .pricePaid(new BigDecimal("59000"))
                    .liveTryonMaxSeconds(15)
                    .maxPendingConsignmentForms(15)
                    .featuredListing(true)
                    .status(SubscriptionStatus.PENDING_PAYMENT)
                    .build();
            when(memberSubscriptionRepository.findById(pending.getId())).thenReturn(Optional.of(pending));
            when(membershipPaymentRepository.findAllBySubscription_IdOrderByCreatedAtAsc(pending.getId()))
                    .thenReturn(List.of());

            service.applyGatewayPaymentSuccess(pending.getId(), OrderPaymentMethod.MOMO, "note");

            ArgumentCaptor<MemberSubscription> saved = ArgumentCaptor.forClass(MemberSubscription.class);
            verify(memberSubscriptionRepository, times(1)).save(saved.capture());
            assertThat(saved.getValue().getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
            assertThat(saved.getValue().getExpiresAt()).isAfter(Instant.now());

            // Tiền từ cổng: bên MEMBER KHÔNG có bút toán, chỉ 1 dòng doanh thu bên PLATFORM
            ArgumentCaptor<WalletTransaction> txs = ArgumentCaptor.forClass(WalletTransaction.class);
            verify(walletTransactionRepository, times(1)).save(txs.capture());
            assertThat(txs.getValue().getType()).isEqualTo(WalletTransactionType.MEMBERSHIP_REVENUE);
            assertThat(txs.getValue().getAmount()).isEqualByComparingTo("59000");

            verify(voucherIssueService).issueForSubscription(any());
        }

        @Test
        @DisplayName("IPN về muộn khi subscription đã CANCELLED -> HOÀN tiền vào ví, KHÔNG kích hoạt")
        void lateIpn_onCancelledSubscription_refundsToWallet() {
            MemberSubscription cancelled = MemberSubscription.builder()
                    .id(UUID.randomUUID())
                    .member(member)
                    .plan(goldPlan)
                    .tier(MembershipTier.GOLD)
                    .billingCycle(BillingCycle.MONTHLY)
                    .pricePaid(new BigDecimal("59000"))
                    .liveTryonMaxSeconds(15)
                    .maxPendingConsignmentForms(15)
                    .featuredListing(true)
                    .status(SubscriptionStatus.CANCELLED)
                    .build();
            when(memberSubscriptionRepository.findById(cancelled.getId()))
                    .thenReturn(Optional.of(cancelled));

            service.applyGatewayPaymentSuccess(cancelled.getId(), OrderPaymentMethod.MOMO,
                    "Hoàn tiền MoMo về muộn");

            assertThat(cancelled.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
            assertThat(wallet.getBalance()).isEqualByComparingTo("559000");
            assertThat(cancelled.getCancelReason()).startsWith("[ĐÃ HOÀN]");
            verify(voucherIssueService, never()).issueForSubscription(any());
        }

        @Test
        @DisplayName("IPN lặp lại sau khi đã hoàn tiền -> KHÔNG hoàn lần thứ hai")
        void duplicateLateIpn_doesNotRefundTwice() {
            MemberSubscription cancelled = MemberSubscription.builder()
                    .id(UUID.randomUUID())
                    .member(member)
                    .plan(goldPlan)
                    .tier(MembershipTier.GOLD)
                    .pricePaid(new BigDecimal("59000"))
                    .status(SubscriptionStatus.CANCELLED)
                    .cancelReason("[ĐÃ HOÀN] lần trước")
                    .build();
            when(memberSubscriptionRepository.findById(cancelled.getId()))
                    .thenReturn(Optional.of(cancelled));

            service.applyGatewayPaymentSuccess(cancelled.getId(), OrderPaymentMethod.MOMO, "note");

            assertThat(wallet.getBalance()).isEqualByComparingTo("500000");
            verify(walletTransactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Mã giao dịch không mang tiền tố SUB- -> không phải giao dịch mua gói, trả false "
                + "để MomoServiceImpl thử tiếp loại khác")
        void nonMembershipRef_isNotHandled() {
            assertThat(service.applyMomoResult("8f14e45f-1702312345678", 0, "123")).isFalse();
            assertThat(service.applyVnPayResult("8f14e45f-1702312345678", "00", "123")).isFalse();
        }
    }

    // ================================================================ Helper

    private static SubscribeMembershipRequest request(UUID planId, BillingCycle cycle,
                                                       OrderPaymentMethod method) {
        SubscribeMembershipRequest request = new SubscribeMembershipRequest();
        request.setPlanId(planId);
        request.setBillingCycle(cycle);
        request.setPaymentMethod(method);
        return request;
    }

    private MemberSubscription activeSubscription(MembershipPlan plan, Instant expiresAt) {
        return MemberSubscription.builder()
                .id(UUID.randomUUID())
                .member(member)
                .plan(plan)
                .tier(plan.getTier())
                .billingCycle(BillingCycle.MONTHLY)
                .pricePaid(plan.getPricePerMonth())
                .liveTryonPerDay(plan.getLiveTryonPerDay())
                .liveTryonMaxSeconds(plan.getLiveTryonMaxSeconds())
                .aiTryonPerDay(plan.getAiTryonPerDay())
                .featuredListing(plan.getFeaturedListing())
                .maxPendingConsignmentForms(plan.getMaxPendingConsignmentForms())
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(Instant.now().minus(20, ChronoUnit.DAYS))
                .expiresAt(expiresAt)
                .build();
    }

    private static MembershipPlan plan(MembershipTier tier, String name, String monthly, String yearly) {
        return MembershipPlan.builder()
                .id(UUID.randomUUID())
                .tier(tier)
                .name(name)
                .pricePerMonth(new BigDecimal(monthly))
                .pricePerYear(yearly == null ? null : new BigDecimal(yearly))
                .liveTryonPerDay(tier == MembershipTier.GOLD ? 10 : 5)
                .liveTryonPerMonth(null)
                .liveTryonMaxSeconds(tier == MembershipTier.GOLD ? 15 : 10)
                .aiTryonPerDay(tier == MembershipTier.GOLD ? 40 : 15)
                .aiTryonPerMonth(null)
                .featuredListing(tier != MembershipTier.FREE)
                .maxPendingConsignmentForms(tier == MembershipTier.GOLD ? 15 : 10)
                .purchasable(tier != MembershipTier.FREE)
                .active(true)
                .sortOrder(tier.ordinal())
                .build();
    }
}
