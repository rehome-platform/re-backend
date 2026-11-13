package com.rehome.rehome.serviceImpl;

import com.rehome.rehome.component.FeaturedListingManager;
import com.rehome.rehome.constant.SystemConfigKeys;
import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.NotificationType;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import com.rehome.rehome.entity.membership.MemberSubscription;
import com.rehome.rehome.entity.membership.MemberVoucher;
import com.rehome.rehome.repository.MemberSubscriptionRepository;
import com.rehome.rehome.repository.MemberVoucherRepository;
import com.rehome.rehome.service.MembershipLifecycleService;
import com.rehome.rehome.service.NotificationService;
import com.rehome.rehome.service.SystemConfigReader;
import com.rehome.rehome.service.VoucherIssueService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bốn job nền của gói thành viên và voucher (đặc tả §14). Giờ chạy theo {@code Asia/Ho_Chi_Minh}:
 * hạn mức và mốc "tháng" đều tính theo giờ VN nên job cũng phải chạy theo giờ VN, chạy theo UTC sẽ
 * phát voucher lệch sang ngày hôm trước.
 *
 * <p>Dùng {@code cron} thay vì {@code fixedRate} như các job khác trong repo
 * ({@code OrderAutoCancelServiceImpl}...) vì đây là việc <b>mỗi ngày một lần vào giờ xác định</b>,
 * không phải việc quét liên tục. {@code fixedRate} sẽ chạy lại ngay khi app restart và lệch giờ dần.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MembershipLifecycleServiceImpl implements MembershipLifecycleService {

    private static final String VN_ZONE = "Asia/Ho_Chi_Minh";

    private final MemberSubscriptionRepository memberSubscriptionRepository;
    private final MemberVoucherRepository memberVoucherRepository;
    private final FeaturedListingManager featuredListingManager;
    private final VoucherIssueService voucherIssueService;
    private final NotificationService notificationService;
    private final SystemConfigReader systemConfigReader;

    // ================================================================ 00:05 - hết hạn gói

    @Override
    @Scheduled(cron = "0 5 0 * * *", zone = VN_ZONE)
    @Transactional
    public void expireSubscriptions() {
        List<MemberSubscription> expired = memberSubscriptionRepository
                .findAllByStatusAndExpiresAtBefore(SubscriptionStatus.ACTIVE, Instant.now());
        if (expired.isEmpty()) {
            return;
        }

        for (MemberSubscription s : expired) {
            s.setStatus(SubscriptionStatus.EXPIRED);
            memberSubscriptionRepository.save(s);

            // Hạn mức tự về FREE vì MembershipQuotaResolver không còn thấy dòng ACTIVE nào.
            // Nhưng nhãn "Nổi bật" là cột tính sẵn trên products nên phải xóa tay ở đây (§8.10).
            featuredListingManager.clearForMember(s.getMember().getId());

            notificationService.notify(s.getMember(), NotificationType.MEMBERSHIP_EVENT,
                    "Gói thành viên đã hết hạn",
                    "Hạn mức của bạn đã trở về mức miễn phí. Gia hạn để tiếp tục nhận quyền lợi.",
                    null, null, null);
        }
        log.info(">>>> Het han {} goi thanh vien", expired.size());
    }

    // ================================================================ 09:00 - nhắc gia hạn

    @Override
    @Scheduled(cron = "0 0 9 * * *", zone = VN_ZONE)
    @Transactional
    public void remindExpiringSubscriptions() {
        int reminderDays = systemConfigReader.getInt(
                SystemConfigKeys.MEMBERSHIP_EXPIRY_REMINDER_DAYS,
                SystemConfigKeys.DEFAULT_MEMBERSHIP_EXPIRY_REMINDER_DAYS);

        Instant now = Instant.now();
        List<MemberSubscription> expiringSoon = memberSubscriptionRepository.findExpiringSoon(
                SubscriptionStatus.ACTIVE, now, now.plus(reminderDays, ChronoUnit.DAYS));
        if (expiringSoon.isEmpty()) {
            return;
        }

        for (MemberSubscription s : expiringSoon) {
            notificationService.notify(s.getMember(), NotificationType.MEMBERSHIP_EVENT,
                    "Gói thành viên sắp hết hạn",
                    s.getPlan().getName() + " của bạn hết hạn ngày "
                            + MembershipServiceImpl.formatVnDate(s.getExpiresAt())
                            + ". Gia hạn để không bị ngắt quyền lợi.",
                    null, null, null);

            // Chốt đã nhắc: query findExpiringSoon lọc expiryReminderSentAt is null nên lần chạy
            // sau không gửi lại. Mốc này được reset khi Member gia hạn (xem activate).
            s.setExpiryReminderSentAt(now);
            memberSubscriptionRepository.save(s);
        }
        log.info(">>>> Nhac gia han {} goi thanh vien (truoc {} ngay)", expiringSoon.size(), reminderDays);
    }

    // ================================================================ 00:15 - phát voucher tháng

    @Override
    @Scheduled(cron = "0 15 0 * * *", zone = VN_ZONE)
    public void issueMonthlyVouchers() {
        Instant now = Instant.now();
        // Đủ điều kiện phát khi chưa phát lần nào, hoặc lần phát gần nhất đã cách đây >= 1 tháng.
        // VoucherIssueServiceImpl còn chặn thêm theo "đã phát trong tháng này chưa", nên mốc này
        // chỉ cần lọc thô cho khỏi quét cả bảng.
        Instant cutoff = now.minus(28, ChronoUnit.DAYS);

        List<MemberSubscription> due = memberSubscriptionRepository.findDueForVoucherIssue(
                SubscriptionStatus.ACTIVE, now, cutoff);
        if (due.isEmpty()) {
            return;
        }

        int totalIssued = 0;
        for (MemberSubscription s : due) {
            try {
                // Mỗi subscription một transaction riêng (issueForSubscription là @Transactional và
                // được gọi qua proxy của bean khác): một Member lỗi không làm cả lượt phát dừng lại.
                int issued = voucherIssueService.issueForSubscription(s);
                if (issued > 0) {
                    totalIssued += issued;
                    notificationService.notify(s.getMember(), NotificationType.VOUCHER_ISSUED,
                            "Bạn vừa nhận " + issued + " voucher",
                            "Voucher tháng này của gói thành viên đã vào ví. Mở để xem hạn dùng.",
                            null, null, null);
                }
            } catch (RuntimeException e) {
                log.error(">>>> Loi khi phat voucher cho subscriptionId={}: {}", s.getId(), e.toString());
            }
        }
        log.info(">>>> Phat voucher thang: {} ma cho {} goi", totalIssued, due.size());
    }

    // ================================================================ 00:20 - hết hạn voucher

    @Override
    @Scheduled(cron = "0 20 0 * * *", zone = VN_ZONE)
    @Transactional
    public void expireVouchers() {
        List<MemberVoucher> expired = memberVoucherRepository
                .findAllByStatusAndExpiresAtBefore(MemberVoucherStatus.UNUSED, Instant.now());
        if (expired.isEmpty()) {
            return;
        }
        for (MemberVoucher v : expired) {
            v.setStatus(MemberVoucherStatus.EXPIRED);
        }
        memberVoucherRepository.saveAll(expired);
        log.info(">>>> Het han {} voucher chua dung", expired.size());
    }
}
