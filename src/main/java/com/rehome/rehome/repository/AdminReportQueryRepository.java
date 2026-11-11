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

/**
 * Các câu truy vấn báo cáo của Admin: món đã bán theo kỳ, sổ quỹ nền tảng, đối soát quỹ, đăng ký tài khoản.
 * Khoảng thời gian luôn là [from, to), không bao giờ null (xem AdminDateRange). Bộ lọc tùy chọn khác
 * (term, type, role) được ghép vào JPQL khi có, không dùng "(:p is null or ...)" vì Postgres không suy được
 * kiểu của tham số null.
 */
@Repository
public class AdminReportQueryRepository {

    @PersistenceContext
    private EntityManager em;

    // ==================== Món đã bán theo kỳ ====================

    /** Một món đã bán trong đơn COMPLETED. */
    public record SoldItemRow(UUID orderItemId, String productId, String productName,
                              UUID orderId, String orderNo,
                              BigDecimal priceAtOrder, BigDecimal commissionRateApplied,
                              Short termApplied, Instant completedAt) {}

    // Món thuộc đơn COMPLETED hoàn thành trong [from, to), cùng điều kiện với doanh thu bán / hoa hồng
    // theo kỳ trên dashboard. term = null thì không lọc kỳ. Mới hoàn thành nhất xếp trước.
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

    // ==================== Sổ quỹ nền tảng ====================

    // Dòng PLATFORM của sổ kép (mỗi dòng là đối ứng của một dòng ví Member), mới nhất trước.
    // Nạp luôn dòng đối ứng, đơn và món liên quan để dựng response không phát sinh N+1.
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

    // Ví Member theo id ví, nạp luôn chủ ví: để gắn "tài khoản liên quan" cho các dòng sổ quỹ
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

    // ==================== Đối soát quỹ ====================

    public BigDecimal sumFundBalance() {
        return orZero(em.createQuery("select sum(f.balance) from PlatformFund f", BigDecimal.class)
                .getSingleResult());
    }

    public BigDecimal sumMemberWalletBalance() {
        return orZero(em.createQuery("select sum(w.balance) from Wallet w", BigDecimal.class)
                .getSingleResult());
    }

    // Dòng ghi nhận doanh thu nền tảng: chỉ đánh dấu phần tiền đã nằm sẵn trong quỹ là doanh thu, không đổi số dư
    // quỹ và không có dòng Member đối ứng, nên phải loại khỏi tổng đối soát
    private static final List<WalletTransactionType> REVENUE_RECOGNITION_TYPES = List.of(
            WalletTransactionType.COMMISSION_REVENUE, WalletTransactionType.WITHDRAWAL_FEE_REVENUE);

    // Tổng các bút toán thành công của một loại ví; tiền vào dương, tiền ra âm.
    // Không tính các dòng ghi nhận doanh thu (xem REVENUE_RECOGNITION_TYPES)
    public BigDecimal sumSuccessfulTransactions(WalletType walletType) {
        return orZero(em.createQuery("select sum(t.amount) from WalletTransaction t "
                        + "where t.walletType = :walletType and t.status = :status "
                        + "and t.type not in :revenueTypes", BigDecimal.class)
                .setParameter("walletType", walletType)
                .setParameter("status", TransactionStatus.SUCCESS)
                .setParameter("revenueTypes", REVENUE_RECOGNITION_TYPES)
                .getSingleResult());
    }

    // Bút toán Member thành công mà chưa có dòng đối ứng nào ở quỹ trỏ về nó
    public long countMemberTransactionsWithoutCounterpart() {
        return em.createQuery("select count(t) from WalletTransaction t "
                        + "where t.walletType = :walletType and t.status = :status "
                        + "and not exists (select 1 from WalletTransaction c where c.counterpartTransaction.id = t.id)",
                Long.class)
                .setParameter("walletType", WalletType.MEMBER)
                .setParameter("status", TransactionStatus.SUCCESS)
                .getSingleResult();
    }

    // Cặp đối ứng không triệt tiêu nhau (số tiền hai dòng cộng lại khác 0)
    public long countUnbalancedCounterpartPairs() {
        return em.createQuery("select count(t) from WalletTransaction t join t.counterpartTransaction c "
                        + "where t.walletType = :walletType and t.amount + c.amount <> 0", Long.class)
                .setParameter("walletType", WalletType.PLATFORM)
                .getSingleResult();
    }

    // ==================== Đăng ký tài khoản ====================

    // Thời điểm tạo của các tài khoản đăng ký trong [from, to); role = null thì lấy mọi vai trò.
    // Việc chia ngày/tuần/tháng theo giờ VN làm ở service (JPQL không có hàm cắt ngày theo múi giờ dùng chung
    // được cho cả Postgres và H2 của test).
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

    private static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
