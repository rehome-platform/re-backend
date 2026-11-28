package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.Role;
import com.rehome.rehome.entity.enums.TransactionStatus;
import com.rehome.rehome.entity.enums.WalletTransactionType;
import com.rehome.rehome.entity.enums.WalletType;
import com.rehome.rehome.entity.marketplace.Wallet;
import com.rehome.rehome.entity.marketplace.WalletTransaction;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class AdminReportQueryRepository {

    @PersistenceContext
    private EntityManager em;

    public record SoldItemRow(UUID orderItemId, String productId, String productName,
                              UUID orderId, String orderNo,
                              BigDecimal priceAtOrder, BigDecimal commissionRateApplied,
                              Short termApplied, Instant completedAt) {}

    public Page<SoldItemRow> findSoldItems(Instant from, Instant to, Short term, Pageable pageable) {
        String base = "from OrderItem oi join oi.order o join oi.product p "
                + "where o.status = :status and o.completedAt >= :from and o.completedAt < :to"
                + (term != null ? " and oi.termApplied = :term" : "");

        TypedQuery<Long> count = em.createQuery("select count(oi) " + base, Long.class);
        TypedQuery<Object[]> data = em.createQuery(
                "select oi.id, p.id, p.name, o.id, o.orderNo, oi.priceAtOrder, oi.commissionRateApplied, "
                        + "oi.termApplied, o.completedAt " + base
                        + " order by o.completedAt desc, oi.id desc", Object[].class);
        for (TypedQuery<?> q : List.of(count, data)) {
            q.setParameter("status", OrderStatus.COMPLETED);
            q.setParameter("from", from);
            q.setParameter("to", to);
            if (term != null) {
                q.setParameter("term", term);
            }
        }

        long total = count.getSingleResult();
        List<SoldItemRow> rows = data
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList().stream()
                .map(r -> new SoldItemRow((UUID) r[0], (String) r[1], (String) r[2], (UUID) r[3], (String) r[4],
                        (BigDecimal) r[5], (BigDecimal) r[6], (Short) r[7], (Instant) r[8]))
                .toList();
        return new PageImpl<>(rows, pageable, total);
    }

    public List<SoldItemRow> findAllSoldItems(Instant from, Instant to) {
        return em.createQuery(
                        "select oi.id, p.id, p.name, o.id, o.orderNo, oi.priceAtOrder, oi.commissionRateApplied, "
                                + "oi.termApplied, o.completedAt "
                                + "from OrderItem oi join oi.order o join oi.product p "
                                + "where o.status = :status and o.completedAt >= :from and o.completedAt < :to "
                                + "order by o.completedAt desc, oi.id desc", Object[].class)
                .setParameter("status", OrderStatus.COMPLETED)
                .setParameter("from", from)
                .setParameter("to", to)
                .getResultList().stream()
                .map(r -> new SoldItemRow((UUID) r[0], (String) r[1], (String) r[2], (UUID) r[3], (String) r[4],
                        (BigDecimal) r[5], (BigDecimal) r[6], (Short) r[7], (Instant) r[8]))
                .toList();
    }

    public Page<WalletTransaction> findPlatformLedger(Instant from, Instant to, WalletTransactionType type,
                                                      Pageable pageable) {
        String base = "from WalletTransaction t where t.walletType = :walletType "
                + "and t.createdAt >= :from and t.createdAt < :to"
                + (type != null ? " and t.type = :type" : "");

        TypedQuery<Long> count = em.createQuery("select count(t) " + base, Long.class);
        TypedQuery<WalletTransaction> data = em.createQuery(
                "select t "
                        + "from WalletTransaction t "
                        + "left join fetch t.counterpartTransaction "
                        + "left join fetch t.relatedOrder "
                        + "left join fetch t.relatedOrderItem "
                        + "where t.walletType = :walletType and t.createdAt >= :from and t.createdAt < :to"
                        + (type != null ? " and t.type = :type" : "")
                        + " order by t.createdAt desc, t.id desc", WalletTransaction.class);
        for (TypedQuery<?> q : List.of(count, data)) {
            q.setParameter("walletType", WalletType.PLATFORM);
            q.setParameter("from", from);
            q.setParameter("to", to);
            if (type != null) {
                q.setParameter("type", type);
            }
        }

        long total = count.getSingleResult();
        List<WalletTransaction> rows = data
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize())
                .getResultList();
        return new PageImpl<>(rows, pageable, total);
    }

    public Map<UUID, Wallet> findWalletsWithMember(Collection<UUID> walletIds) {
        Map<UUID, Wallet> result = new HashMap<>();
        if (walletIds.isEmpty()) {
            return result;
        }
        em.createQuery("select w from Wallet w join fetch w.member where w.id in :ids", Wallet.class)
                .setParameter("ids", walletIds)
                .getResultList()
                .forEach(w -> result.put(w.getId(), w));
        return result;
    }

    public BigDecimal sumFundBalance() {
        return orZero(em.createQuery("select sum(f.balance) from PlatformFund f", BigDecimal.class)
                .getSingleResult());
    }

    public BigDecimal sumMemberWalletBalance() {
        return orZero(em.createQuery("select sum(w.balance) from Wallet w", BigDecimal.class)
                .getSingleResult());
    }

    private static final List<WalletTransactionType> REVENUE_RECOGNITION_TYPES = List.of(
            WalletTransactionType.COMMISSION_REVENUE,
            WalletTransactionType.WITHDRAWAL_FEE_REVENUE,
            WalletTransactionType.RETURN_SHIP_BACK_FEE_REVENUE,
            WalletTransactionType.MEMBERSHIP_REVENUE,
            WalletTransactionType.VOUCHER_DISCOUNT);

    public BigDecimal sumSuccessfulTransactions(WalletType walletType) {
        return orZero(em.createQuery("select sum(t.amount) from WalletTransaction t "
                        + "where t.walletType = :walletType and t.status = :status "
                        + "and t.type not in :revenueTypes", BigDecimal.class)
                .setParameter("walletType", walletType)
                .setParameter("status", TransactionStatus.SUCCESS)
                .setParameter("revenueTypes", REVENUE_RECOGNITION_TYPES)
                .getSingleResult());
    }

    public long countMemberTransactionsWithoutCounterpart() {
        return em.createQuery("select count(t) from WalletTransaction t "
                        + "where t.walletType = :walletType and t.status = :status "
                        + "and not exists (select 1 from WalletTransaction c where c.counterpartTransaction.id = t.id)",
                Long.class)
                .setParameter("walletType", WalletType.MEMBER)
                .setParameter("status", TransactionStatus.SUCCESS)
                .getSingleResult();
    }

    public long countUnbalancedCounterpartPairs() {
        return em.createQuery("select count(t) from WalletTransaction t join t.counterpartTransaction c "
                        + "where t.walletType = :walletType and t.amount + c.amount <> 0", Long.class)
                .setParameter("walletType", WalletType.PLATFORM)
                .getSingleResult();
    }

    public List<Instant> findAccountCreatedAt(Instant from, Instant to, Role role) {
        TypedQuery<Instant> query = em.createQuery("select a.createdAt from Account a "
                + "where a.createdAt >= :from and a.createdAt < :to"
                + (role != null ? " and a.role = :role" : ""), Instant.class);
        query.setParameter("from", from);
        query.setParameter("to", to);
        if (role != null) {
            query.setParameter("role", role);
        }
        return query.getResultList();
    }

    public long countAccountsCreated(Instant from, Instant to, Role role) {
        TypedQuery<Long> query = em.createQuery("select count(a) from Account a "
                + "where a.createdAt >= :from and a.createdAt < :to"
                + (role != null ? " and a.role = :role" : ""), Long.class);
        query.setParameter("from", from);
        query.setParameter("to", to);
        if (role != null) {
            query.setParameter("role", role);
        }
        return query.getSingleResult();
    }

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
