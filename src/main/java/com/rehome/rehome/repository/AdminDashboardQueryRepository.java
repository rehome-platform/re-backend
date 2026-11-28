package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AdminDashboardQueryRepository {

    private static final List<ProductStatus> STOCK_STATUSES = List.of(
            ProductStatus.ON_SALE, ProductStatus.RESERVED, ProductStatus.UNCLAIMED,
            ProductStatus.PLATFORM_OWNED, ProductStatus.RETURNED, ProductStatus.WAREHOUSE);

    private static final List<OrderStatus> PAID_STATUSES = List.of(
            OrderStatus.PAID, OrderStatus.PACKING, OrderStatus.PACKED, OrderStatus.SHIPPING, OrderStatus.DELIVERED,
            OrderStatus.COMPLETED, OrderStatus.RETURNED);

    private static final List<OrderStatus> HELD_STATUSES = List.of(
            OrderStatus.PAID, OrderStatus.PACKING, OrderStatus.PACKED, OrderStatus.SHIPPING, OrderStatus.DELIVERED);

    private static final List<WalletTransactionType> PLATFORM_REVENUE_TYPES = List.of(
            WalletTransactionType.COMMISSION_REVENUE, WalletTransactionType.WITHDRAWAL_FEE_REVENUE,
            WalletTransactionType.RETURN_SHIP_BACK_FEE_REVENUE,
            WalletTransactionType.MEMBERSHIP_REVENUE, WalletTransactionType.MEMBERSHIP_PURCHASE);

    @PersistenceContext
    private EntityManager em;

    public BigDecimal sumCompletedOrderAmount(Instant from, Instant to) {
        return sum("select sum(o.totalAmount) from Order o "
                + "where o.status = :status and o.completedAt >= :from and o.completedAt < :to",
                from, to, "status", OrderStatus.COMPLETED);
    }

    public long countPaidOrders(Instant from, Instant to) {
        return em.createQuery("select count(o) from Order o "
                        + "where o.status in :statuses and o.createdAt >= :from and o.createdAt < :to", Long.class)
                .setParameter("statuses", PAID_STATUSES)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
    }

    public long countStockedProducts() {
        return em.createQuery("select count(p) from Product p where p.status in :statuses", Long.class)
                .setParameter("statuses", STOCK_STATUSES)
                .getSingleResult();
    }

    public Double avgServiceRating(Instant from, Instant to) {
        return em.createQuery("select avg(o.serviceRating) from Order o "
                        + "where o.serviceRating is not null and o.ratedAt >= :from and o.ratedAt < :to", Double.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult();
    }

    public BigDecimal sumMemberWalletBalance() {
        return orZero(em.createQuery("select sum(w.balance) from Wallet w", BigDecimal.class)
                .getSingleResult());
    }

    public BigDecimal sumCompletedCommission(Instant from, Instant to) {
        return sumTransactions(WalletType.PLATFORM, WalletTransactionType.COMMISSION_REVENUE, from, to);
    }

    public BigDecimal sumWithdrawalFeeRevenue(Instant from, Instant to) {
        return sumTransactions(WalletType.PLATFORM, WalletTransactionType.WITHDRAWAL_FEE_REVENUE, from, to);
    }

    public BigDecimal sumReturnShipBackFeeRevenue(Instant from, Instant to) {
        return sumTransactions(WalletType.PLATFORM, WalletTransactionType.RETURN_SHIP_BACK_FEE_REVENUE, from, to);
    }

    public BigDecimal sumMembershipRevenue(Instant from, Instant to) {
        return sumTransactions(WalletType.PLATFORM, WalletTransactionType.MEMBERSHIP_REVENUE, from, to)
                .add(sumTransactions(WalletType.PLATFORM, WalletTransactionType.MEMBERSHIP_PURCHASE, from, to));
    }

    public BigDecimal sumDegradedCompensation(Instant from, Instant to) {
        return sumTransactions(WalletType.MEMBER, WalletTransactionType.DEGRADED_COMPENSATION, from, to);
    }

    private BigDecimal sumTransactions(WalletType walletType, WalletTransactionType type, Instant from, Instant to) {
        return orZero(em.createQuery("select sum(t.amount) from WalletTransaction t "
                        + "where t.walletType = :walletType and t.type = :type and t.status = :status "
                        + "and t.createdAt >= :from and t.createdAt < :to", BigDecimal.class)
                .setParameter("walletType", walletType)
                .setParameter("type", type)
                .setParameter("status", TransactionStatus.SUCCESS)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult());
    }

    public BigDecimal sumPlatformFundBalance() {
        return orZero(em.createQuery("select sum(f.balance) from PlatformFund f", BigDecimal.class)
                .getSingleResult());
    }

    public List<TermSales> sumCompletedSalesByTerm(Instant from, Instant to) {
        List<Object[]> rows = em.createQuery(
                        "select oi.termApplied, sum(oi.priceAtOrder), "
                                + "sum(oi.priceAtOrder * oi.commissionRateApplied), count(oi) "
                                + "from OrderItem oi join oi.order o "
                                + "where o.status = :status and o.completedAt >= :from and o.completedAt < :to "
                                + "and oi.termApplied is not null "
                                + "group by oi.termApplied", Object[].class)
                .setParameter("status", OrderStatus.COMPLETED)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        return rows.stream()
                .map(r -> new TermSales(((Number) r[0]).shortValue(),
                        orZero((BigDecimal) r[1]), orZero((BigDecimal) r[2]), ((Number) r[3]).longValue()))
                .toList();
    }

    public Map<Short, Long> countStockedProductsByTerm() {
        List<Object[]> rows = em.createQuery(
                        "select p.currentTerm, count(p) from Product p "
                                + "where p.status in :statuses and p.currentTerm is not null "
                                + "group by p.currentTerm", Object[].class)
                .setParameter("statuses", STOCK_STATUSES)
                .getResultList();
        Map<Short, Long> result = new HashMap<>();
        rows.forEach(r -> result.put(((Number) r[0]).shortValue(), ((Number) r[1]).longValue()));
        return result;
    }

    public record TermSales(short term, BigDecimal salesRevenue, BigDecimal commission, long itemCount) {}

    public Map<ProductStatus, Long> countStockedProductsByStatus() {
        List<Object[]> rows = em.createQuery(
                        "select p.status, count(p) from Product p where p.status in :statuses group by p.status",
                        Object[].class)
                .setParameter("statuses", STOCK_STATUSES)
                .getResultList();
        Map<ProductStatus, Long> result = new LinkedHashMap<>();
        STOCK_STATUSES.forEach(status -> result.put(status, 0L));
        rows.forEach(r -> result.put((ProductStatus) r[0], ((Number) r[1]).longValue()));
        return result;
    }

    public BigDecimal sumHeldOrderAmount() {
        return orZero(em.createQuery("select sum(o.totalAmount) from Order o where o.status in :statuses",
                        BigDecimal.class)
                .setParameter("statuses", HELD_STATUSES)
                .getSingleResult());
    }

    public long countHeldOrders() {
        return em.createQuery("select count(o) from Order o where o.status in :statuses", Long.class)
                .setParameter("statuses", HELD_STATUSES)
                .getSingleResult();
    }

    public List<RevenueEntry> findPlatformRevenueEntries(Instant from, Instant to) {
        List<Object[]> rows = em.createQuery(
                        "select t.createdAt, t.amount from WalletTransaction t "
                                + "where t.walletType = :walletType and t.type in :types and t.status = :status "
                                + "and t.createdAt >= :from and t.createdAt < :to", Object[].class)
                .setParameter("walletType", WalletType.PLATFORM)
                .setParameter("types", PLATFORM_REVENUE_TYPES)
                .setParameter("status", TransactionStatus.SUCCESS)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList();
        return rows.stream()
                .map(r -> new RevenueEntry((Instant) r[0], orZero((BigDecimal) r[1])))
                .toList();
    }

    public record RevenueEntry(Instant createdAt, BigDecimal amount) {}

    public long countQueueItemsCreatedBefore(Instant deadline) {
        return em.createQuery("select count(q) from QueueItem q where q.createdAt < :deadline", Long.class)
                .setParameter("deadline", deadline)
                .getSingleResult();
    }

    private BigDecimal sum(String jpql, Instant from, Instant to, String extraName, Object extraValue) {
        return orZero(em.createQuery(jpql, BigDecimal.class)
                .setParameter(extraName, extraValue)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult());
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
