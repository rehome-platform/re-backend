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
import java.util.List;
import java.util.Map;

/**
 * Các câu tổng hợp cho dashboard Admin (F-158/F-159). Không gắn với một entity nên dùng EntityManager,
 * thay vì nhét vào repository của từng entity. Khoảng thời gian luôn là [from, to), không bao giờ null:
 * Postgres không suy được kiểu của tham số null trong "(:p is null or ...)".
 */
@Repository
public class AdminDashboardQueryRepository {

    // Hàng còn nằm trong kho, cùng định nghĩa với WarehouseAdminServiceImpl.STOCK_STATUSES
    private static final List<ProductStatus> STOCK_STATUSES = List.of(
            ProductStatus.ON_SALE, ProductStatus.RESERVED, ProductStatus.UNCLAIMED,
            ProductStatus.PLATFORM_OWNED, ProductStatus.RETURNED, ProductStatus.WAREHOUSE);

    // Đơn đã thanh toán: bỏ chờ thanh toán và đã hủy
    private static final List<OrderStatus> PAID_STATUSES = List.of(
            OrderStatus.PAID, OrderStatus.PACKING, OrderStatus.SHIPPING, OrderStatus.DELIVERED,
            OrderStatus.COMPLETED, OrderStatus.RETURNED);

    private static final List<WalletTransactionType> PLATFORM_REVENUE_TYPES = List.of(
            WalletTransactionType.COMMISSION_REVENUE, WalletTransactionType.WITHDRAWAL_FEE_REVENUE);

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

    // null nếu chưa có đánh giá nào trong khoảng
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

    public BigDecimal sumPlatformRevenue(Instant from, Instant to) {
        return orZero(em.createQuery("select sum(t.amount) from WalletTransaction t "
                        + "where t.walletType = :walletType and t.type in :types and t.status = :status "
                        + "and t.createdAt >= :from and t.createdAt < :to", BigDecimal.class)
                .setParameter("walletType", WalletType.PLATFORM)
                .setParameter("types", PLATFORM_REVENUE_TYPES)
                .setParameter("status", TransactionStatus.SUCCESS)
                .setParameter("from", from)
                .setParameter("to", to)
                .getSingleResult());
    }

    public BigDecimal sumPlatformFundBalance() {
        return orZero(em.createQuery("select sum(f.balance) from PlatformFund f", BigDecimal.class)
                .getSingleResult());
    }

    // Doanh thu bán và hoa hồng theo kỳ của các món thuộc đơn COMPLETED hoàn thành trong [from, to).
    // Hoa hồng = giá chốt * tỷ lệ hoa hồng đã áp dụng lúc đặt đơn. Món không có termApplied bị bỏ qua.
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

    // Số món còn nằm trong kho theo kỳ hiện tại của sản phẩm; chỉ trả các kỳ thực sự có hàng
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

    /** Một dòng tổng hợp doanh thu bán theo kỳ: tổng giá bán, tổng hoa hồng, số món. */
    public record TermSales(short term, BigDecimal salesRevenue, BigDecimal commission, long itemCount) {}

    // Việc còn trong hàng đợi (xong thì dòng bị gỡ) mà vào hàng đợi trước mốc deadline
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

    // sum() trả null khi không có dòng nào
    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
