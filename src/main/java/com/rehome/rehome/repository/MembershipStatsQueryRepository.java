package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.MemberVoucherStatus;
import com.rehome.rehome.entity.enums.MembershipTier;
import com.rehome.rehome.entity.enums.PaymentStatus;
import com.rehome.rehome.entity.enums.SubscriptionStatus;
import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.util.VnTimeWindow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/**
 * Truy vấn thống kê gói thành viên và voucher cho màn W-05.
 *
 * <p>Viết thành repository riêng (giống {@code AdminReportQueryRepository}) vì toàn bộ là câu
 * aggregate nhiều bảng, không map được thành derived query của Spring Data.
 *
 * <p>Khoảng thời gian luôn là nửa mở {@code [from, to)} (xem {@code AdminDateRange}). Gom theo
 * tháng được làm <b>trong Java</b> chứ không bằng {@code date_trunc} của Postgres: test chạy trên
 * H2 nên hàm riêng của Postgres sẽ không chạy được, và số dòng payment trong một khoảng báo cáo là
 * nhỏ. Đổi lại phải nhớ quy đổi sang giờ VN ở tầng này.
 */
@Repository
public class MembershipStatsQueryRepository {

    @PersistenceContext
    private EntityManager em;

    /** Một lần thu tiền mua gói: dùng để gom doanh thu theo tháng và theo hạng. */
    public record PaymentRow(Instant paidAt, MembershipTier tier, BigDecimal amount) {}

    public List<PaymentRow> successfulPayments(Instant from, Instant to) {
        return em.createQuery("""
                        select p.paidAt, s.tier, p.amount
                        from MembershipPayment p join p.subscription s
                        where p.status = :status
                          and p.paidAt >= :from and p.paidAt < :to
                          and p.amount > 0
                        """, Object[].class)
                .setParameter("status", PaymentStatus.SUCCESS)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList().stream()
                .map(row -> new PaymentRow((Instant) row[0], (MembershipTier) row[1], (BigDecimal) row[2]))
                .toList();
    }

    /** Gom doanh thu theo (tháng giờ VN, hạng), giữ thứ tự tháng tăng dần. */
    public Map<String, Map<MembershipTier, BigDecimal>> revenueByMonthAndTier(List<PaymentRow> rows) {
        Map<String, Map<MembershipTier, BigDecimal>> byMonth = new LinkedHashMap<>();
        rows.stream()
                .sorted((a, b) -> a.paidAt().compareTo(b.paidAt()))
                .forEach(row -> byMonth
                        .computeIfAbsent(monthKey(row.paidAt()), k -> new EnumMap<>(MembershipTier.class))
                        .merge(row.tier(), row.amount(), BigDecimal::add));
        return byMonth;
    }

    public static String monthKey(Instant instant) {
        return YearMonth.from(instant.atZone(VnTimeWindow.VN_ZONE)).toString();
    }

    /** Subscription đang ACTIVE và còn hạn, theo hạng. Ảnh chụp hiện tại, không theo khoảng báo cáo. */
    public Map<MembershipTier, Long> activeCountByTier(Instant now) {
        Map<MembershipTier, Long> counts = new EnumMap<>(MembershipTier.class);
        for (Object[] row : em.createQuery("""
                        select s.tier, count(s) from MemberSubscription s
                        where s.status = :status and s.expiresAt > :now
                        group by s.tier
                        """, Object[].class)
                .setParameter("status", SubscriptionStatus.ACTIVE)
                .setParameter("now", now)
                .getResultList()) {
            counts.put((MembershipTier) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * Gói hết hạn trong khoảng mà Member <b>không</b> có subscription nào bắt đầu sau đó.
     *
     * <p>"Không gia hạn" phải xét theo MEMBER, không theo dòng subscription: người đổi sang hạng
     * khác vẫn là người còn ở lại, chỉ là dòng cũ chuyển {@code REPLACED}. Vì vậy điều kiện là
     * "không có dòng nào của member này có {@code startedAt} muộn hơn".
     */
    public long notRenewedCount(Instant from, Instant to) {
        Long count = em.createQuery("""
                        select count(s) from MemberSubscription s
                        where s.status in :endedStatuses
                          and s.expiresAt >= :from and s.expiresAt < :to
                          and not exists (
                              select 1 from MemberSubscription later
                              where later.member.id = s.member.id
                                and later.startedAt is not null
                                and later.startedAt > s.expiresAt
                          )
                        """, Long.class)
                .setParameter("endedStatuses",
                        List.of(SubscriptionStatus.EXPIRED, SubscriptionStatus.CANCELLED))
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return count == null ? 0L : count;
    }

    /**
     * Chi phí khuyến mãi đã thực sự chi trong khoảng, theo hạng của gói đã phát ra mã.
     *
     * <p>Lấy từ dòng sổ {@code VOUCHER_DISCOUNT} (ghi lúc đơn hoàn tất) chứ không từ mệnh giá mã
     * đã phát: mã không ai dùng thì không tốn đồng nào. Dòng sổ mang số âm nên trả về số dương ở
     * đây cho dễ đọc trên biểu đồ.
     */
    public Map<MembershipTier, BigDecimal> voucherCostByTier(Instant from, Instant to) {
        Map<MembershipTier, BigDecimal> costs = new EnumMap<>(MembershipTier.class);
        for (Object[] row : em.createQuery("""
                        select s.tier, sum(t.amount) from WalletTransaction t
                          join t.relatedOrder o
                          join MemberVoucher v on v.id = o.voucher.id
                          join v.sourceSubscription s
                        where t.type = :type and t.status = :txStatus
                          and t.createdAt >= :from and t.createdAt < :to
                        group by s.tier
                        """, Object[].class)
                .setParameter("type", WalletTransactionType.VOUCHER_DISCOUNT)
                .setParameter("txStatus", TransactionStatus.SUCCESS)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList()) {
            BigDecimal amount = (BigDecimal) row[1];
            costs.put((MembershipTier) row[0], amount == null ? BigDecimal.ZERO : amount.abs());
        }
        return costs;
    }

    /** Tổng chi phí khuyến mãi trong khoảng, số dương. */
    public BigDecimal totalVoucherSpent(Instant from, Instant to) {
        BigDecimal sum = em.createQuery("""
                        select sum(t.amount) from WalletTransaction t
                        where t.type = :type and t.status = :txStatus
                          and t.createdAt >= :from and t.createdAt < :to
                        """, BigDecimal.class)
                .setParameter("type", WalletTransactionType.VOUCHER_DISCOUNT)
                .setParameter("txStatus", TransactionStatus.SUCCESS)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
        return sum == null ? BigDecimal.ZERO : sum.abs();
    }

    /**
     * Số lượt thử đồ đã dùng trong khoảng, theo hạng <b>đang có hiệu lực của Member tại thời điểm
     * gọi API</b> - không phải hạng lúc dùng lượt đó.
     *
     * <p>Đánh đổi có chủ ý: lưu lại hạng tại từng lượt thử đồ sẽ phải thêm cột vào
     * {@code live_tryon_sessions} / {@code try_on_images}, mà con số này chỉ dùng để Admin ước
     * lượng chi phí AI theo hạng, không phải để đối soát tiền.
     */
    public Map<MembershipTier, Long> liveTryOnUsageByTier(Instant from, Instant to, Instant now) {
        return tryOnUsageByTier("LiveTryOnSession", from, to, now);
    }

    public Map<MembershipTier, Long> aiTryOnUsageByTier(Instant from, Instant to, Instant now) {
        return tryOnUsageByTier("TryOnImage", from, to, now);
    }

    private Map<MembershipTier, Long> tryOnUsageByTier(String entityName, Instant from, Instant to,
                                                        Instant now) {
        // entityName là hằng do chính class này truyền vào (hai giá trị cố định), không phải input
        // của người dùng - nối chuỗi ở đây không mở đường cho injection.
        Map<MembershipTier, Long> usage = new EnumMap<>(MembershipTier.class);
        for (Object[] row : em.createQuery("""
                        select s.tier, count(e) from %s e
                          join MemberSubscription s on s.member.id = e.member.id
                        where e.createdAt >= :from and e.createdAt < :to
                          and s.status = :status and s.expiresAt > :now
                        group by s.tier
                        """.formatted(entityName), Object[].class)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("status", SubscriptionStatus.ACTIVE)
                .setParameter("now", now)
                .getResultList()) {
            usage.put((MembershipTier) row[0], (Long) row[1]);
        }
        return usage;
    }

    /** Số mã theo (mẫu, trạng thái), phát ra trong khoảng. */
    public record TemplateStatusCount(UUID templateId, MemberVoucherStatus status, long count) {}

    public List<TemplateStatusCount> voucherCountsByTemplate(Instant from, Instant to) {
        List<TemplateStatusCount> result = new ArrayList<>();
        for (Object[] row : em.createQuery("""
                        select v.voucherTemplate.id, v.status, count(v) from MemberVoucher v
                        where v.issuedAt >= :from and v.issuedAt < :to
                        group by v.voucherTemplate.id, v.status
                        """, Object[].class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList()) {
            result.add(new TemplateStatusCount(
                    (UUID) row[0], (MemberVoucherStatus) row[1], (Long) row[2]));
        }
        return result;
    }
}
