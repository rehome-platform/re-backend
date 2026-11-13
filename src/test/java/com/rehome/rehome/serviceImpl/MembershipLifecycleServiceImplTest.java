package com.rehome.rehome.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.rehome.rehome.component.FeaturedListingManager;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.entity.auth.Account;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import com.rehome.rehome.entity.membership.MemberSubscription;
import com.rehome.rehome.entity.membership.MemberVoucher;
import com.rehome.rehome.entity.membership.MembershipPlan;
import com.rehome.rehome.repository.MemberSubscriptionRepository;
import com.rehome.rehome.repository.MemberVoucherRepository;
import com.rehome.rehome.service.NotificationService;
import com.rehome.rehome.service.SystemConfigReader;
import com.rehome.rehome.service.VoucherIssueService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/** Bốn job nền của gói thành viên và voucher (đặc tả §14). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MembershipLifecycleServiceImplTest {

    @Mock private MemberSubscriptionRepository memberSubscriptionRepository;
    @Mock private MemberVoucherRepository memberVoucherRepository;
    @Mock private FeaturedListingManager featuredListingManager;
    @Mock private VoucherIssueService voucherIssueService;
    @Mock private NotificationService notificationService;
    @Mock private SystemConfigReader systemConfigReader;

    @InjectMocks
    private MembershipLifecycleServiceImpl service;

    private Account member;
    private MembershipPlan goldPlan;

    @BeforeEach
    void setUp() {
        member = Account.builder().id(UUID.randomUUID()).email("m@example.com").build();
        goldPlan = MembershipPlan.builder().id(UUID.randomUUID())
                .tier(MembershipTier.GOLD).name("Thành viên Vàng").build();
        when(systemConfigReader.getInt(eq(SystemConfigKeys.MEMBERSHIP_EXPIRY_REMINDER_DAYS), anyInt()))
                .thenReturn(3);
    }

    @Test
    @DisplayName("Job 00:05 - gói quá hạn chuyển EXPIRED, TẮT nhãn Nổi bật trên mọi món, gửi thông báo")
    void expireSubscriptions_clearsFeaturedAndNotifies() {
        MemberSubscription expired = subscription(Instant.now().minus(1, ChronoUnit.HOURS));
        when(memberSubscriptionRepository.findAllByStatusAndExpiresAtBefore(
                eq(SubscriptionStatus.ACTIVE), any())).thenReturn(List.of(expired));

        service.expireSubscriptions();

        assertThat(expired.getStatus()).isEqualTo(SubscriptionStatus.EXPIRED);
        // Hạn mức tự về FREE vì resolver không còn thấy dòng ACTIVE, nhưng featured_until là cột
        // tính sẵn trên products nên PHẢI xóa tay ở đây
        verify(featuredListingManager).clearForMember(member.getId());
        verify(notificationService).notify(eq(member), eq(NotificationType.MEMBERSHIP_EVENT),
                anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("Job 00:05 - không có gói nào quá hạn thì không gọi gì cả")
    void expireSubscriptions_noopWhenNothingExpired() {
        when(memberSubscriptionRepository.findAllByStatusAndExpiresAtBefore(any(), any()))
                .thenReturn(List.of());

        service.expireSubscriptions();

        verify(featuredListingManager, never()).clearForMember(any());
        verify(notificationService, never()).notify(any(), any(), anyString(), anyString(),
                any(), any(), any());
    }

    @Test
    @DisplayName("Job 09:00 - nhắc gia hạn và ghi mốc đã nhắc, để lần chạy sau không gửi lại")
    void remindExpiring_marksReminderSent() {
        MemberSubscription expiringSoon = subscription(Instant.now().plus(2, ChronoUnit.DAYS));
        when(memberSubscriptionRepository.findExpiringSoon(
                eq(SubscriptionStatus.ACTIVE), any(), any())).thenReturn(List.of(expiringSoon));

        service.remindExpiringSubscriptions();

        assertThat(expiringSoon.getExpiryReminderSentAt()).isNotNull();
        verify(notificationService).notify(eq(member), eq(NotificationType.MEMBERSHIP_EVENT),
                anyString(), anyString(), any(), any(), any());
        verify(memberSubscriptionRepository).save(expiringSoon);
    }

    @Test
    @DisplayName("Job 00:15 - phát voucher và gửi VOUCHER_ISSUED chỉ khi thật sự có mã mới")
    void issueMonthlyVouchers_notifiesOnlyWhenSomethingIssued() {
        MemberSubscription a = subscription(Instant.now().plus(20, ChronoUnit.DAYS));
        MemberSubscription b = subscription(Instant.now().plus(20, ChronoUnit.DAYS));
        when(memberSubscriptionRepository.findDueForVoucherIssue(
                eq(SubscriptionStatus.ACTIVE), any(), any())).thenReturn(List.of(a, b));
        when(voucherIssueService.issueForSubscription(a)).thenReturn(3);
        when(voucherIssueService.issueForSubscription(b)).thenReturn(0);

        service.issueMonthlyVouchers();

        verify(voucherIssueService).issueForSubscription(a);
        verify(voucherIssueService).issueForSubscription(b);
        verify(notificationService, times(1)).notify(any(), eq(NotificationType.VOUCHER_ISSUED),
                anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("Job 00:15 - một Member lỗi KHÔNG làm cả lượt phát dừng lại")
    void issueMonthlyVouchers_oneFailureDoesNotStopTheRest() {
        MemberSubscription broken = subscription(Instant.now().plus(20, ChronoUnit.DAYS));
        MemberSubscription healthy = subscription(Instant.now().plus(20, ChronoUnit.DAYS));
        when(memberSubscriptionRepository.findDueForVoucherIssue(any(), any(), any()))
                .thenReturn(List.of(broken, healthy));
        when(voucherIssueService.issueForSubscription(broken))
                .thenThrow(new IllegalStateException("DB tạm lỗi"));
        when(voucherIssueService.issueForSubscription(healthy)).thenReturn(2);

        service.issueMonthlyVouchers();

        verify(voucherIssueService).issueForSubscription(healthy);
        verify(notificationService, times(1)).notify(any(), eq(NotificationType.VOUCHER_ISSUED),
                anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("Job 00:20 - mã UNUSED quá hạn chuyển EXPIRED")
    void expireVouchers_marksUnusedExpiredOnes() {
        MemberVoucher stale = MemberVoucher.builder()
                .id(UUID.randomUUID())
                .code("SHIP-ABC123")
                .member(member)
                .status(MemberVoucherStatus.UNUSED)
                .issuedAt(Instant.now().minus(40, ChronoUnit.DAYS))
                .expiresAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .build();
        when(memberVoucherRepository.findAllByStatusAndExpiresAtBefore(
                eq(MemberVoucherStatus.UNUSED), any())).thenReturn(List.of(stale));

        service.expireVouchers();

        assertThat(stale.getStatus()).isEqualTo(MemberVoucherStatus.EXPIRED);
        verify(memberVoucherRepository).saveAll(List.of(stale));
    }

    private MemberSubscription subscription(Instant expiresAt) {
        return MemberSubscription.builder()
                .id(UUID.randomUUID())
                .member(member)
                .plan(goldPlan)
                .tier(MembershipTier.GOLD)
                .status(SubscriptionStatus.ACTIVE)
                .startedAt(Instant.now().minus(28, ChronoUnit.DAYS))
                .expiresAt(expiresAt)
                .build();
    }
}
