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
            OrderStatus.PAID, OrderStatus.PACKING, OrderStatus.PACKED, OrderStatus.SHIPPING, OrderStatus.DELIVERED,
            OrderStatus.COMPLETED, OrderStatus.RETURNED);

    // Đơn đã thanh toán nhưng chưa hoàn tất: tiền người mua đã trả, nền tảng đang giữ
    private static final List<OrderStatus> HELD_STATUSES = List.of(
            OrderStatus.PAID, OrderStatus.PACKING, OrderStatus.PACKED, OrderStatus.SHIPPING, OrderStatus.DELIVERED);

    // Các loại bút toán cấu thành doanh thu nền tảng (khớp sumCompletedCommission +
    // sumWithdrawalFeeRevenue + sumMembershipRevenue). MEMBERSHIP_PURCHASE ở đây chỉ khớp dòng
    // đối ứng phía PLATFORM (dương) nhờ walletType = PLATFORM lọc ở WHERE bên dưới - dòng Member
    // âm (walletType = MEMBER) không lọt vào.
    private static final List<WalletTransactionType> PLATFORM_REVENUE_TYPES = List.of(
            WalletTransactionType.COMMISSION_REVENUE, WalletTransactionType.WITHDRAWAL_FEE_REVENUE,
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

    // Hoa hồng nền tảng thu: dòng PLATFORM COMMISSION_REVENUE, ghi lúc trả tiền người bán của đơn (payoutOrder)
    public BigDecimal sumCompletedCommission(Instant from, Instant to) {
        return sumTransactions(WalletType.PLATFORM, WalletTransactionType.COMMISSION_REVENUE, from, to);
    }

    // Phí rút hàng nền tảng thu: dòng PLATFORM WITHDRAWAL_FEE_REVENUE, ghi lúc phí được thanh toán
    public BigDecimal sumWithdrawalFeeRevenue(Instant from, Instant to) {
        return sumTransactions(WalletType.PLATFORM, WalletTransactionType.WITHDRAWAL_FEE_REVENUE, from, to);
    }

    // Doanh thu bán gói thành viên, gộp cả 2 cách trả:
    // - Trả qua cổng (VNPay...): dòng PLATFORM loại MEMBERSHIP_REVENUE (xem MembershipServiceImpl.
    //   recordMembershipRevenue - không có dòng Member đối ứng, tiền đã về từ ngoài).
    // - Trả bằng ví: dòng đối ứng PLATFORM cùng loại MEMBERSHIP_PURCHASE với dòng Member âm (xem
    //   MembershipServiceImpl.recordPlatformCounterpart - counterpart giữ nguyên type của
    //   memberTransaction thay vì MEMBERSHIP_REVENUE).
    public BigDecimal sumMembershipRevenue(Instant from, Instant to) {
        return sumTransactions(WalletType.PLATFORM, WalletTransactionType.MEMBERSHIP_REVENUE, from, to)
                .add(sumTransactions(WalletType.PLATFORM, WalletTransactionType.MEMBERSHIP_PURCHASE, from, to));
    }

    // Tiền đền bù cộng vào ví người bán: dòng phía MEMBER của DEGRADED_COMPENSATION (dương)
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

    // Số món còn nằm trong kho theo từng trạng thái; luôn có đủ mọi trạng thái tồn kho (không có hàng = 0)
    // và đúng thứ tự STOCK_STATUSES để màn hình hiển thị ổn định
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

    // Tổng tiền các đơn đã thanh toán nhưng chưa hoàn tất (tiền nền tảng đang giữ hộ)
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

    // Từng bút toán doanh thu nền tảng trong [from, to) để service gom theo ngày/tuần/tháng giờ VN
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

    /** Một bút toán doanh thu nền tảng: thời điểm ghi và số tiền. */
    public record RevenueEntry(Instant createdAt, BigDecimal amount) {}

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
