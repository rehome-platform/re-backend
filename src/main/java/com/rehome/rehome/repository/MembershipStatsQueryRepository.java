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

@Repository
public class MembershipStatsQueryRepository {

    @PersistenceContext
    private EntityManager em;

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

    public Map<MembershipTier, Long> liveTryOnUsageByTier(Instant from, Instant to, Instant now) {
        return tryOnUsageByTier("LiveTryOnSession", from, to, now);
    }

    public Map<MembershipTier, Long> aiTryOnUsageByTier(Instant from, Instant to, Instant now) {
        return tryOnUsageByTier("TryOnImage", from, to, now);
    }

    private Map<MembershipTier, Long> tryOnUsageByTier(String entityName, Instant from, Instant to,
                                                        Instant now) {
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
