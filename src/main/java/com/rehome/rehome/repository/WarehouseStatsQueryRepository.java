package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.AccountStatus;
import com.rehome.rehome.entity.enums.OrderStatus;
import com.rehome.rehome.entity.enums.ProductStatus;
import com.rehome.rehome.entity.enums.Role;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Số liệu theo kho cho API 141 (danh sách kho của Admin). Mỗi hàm gom nhóm theo kho trong đúng 1 query
 * cho toàn bộ kho, tránh N+1 khi liệt kê nhiều kho. Kho không có dòng nào thì không có trong Map (coi là 0).
 */
@Repository
public class WarehouseStatsQueryRepository {

    @PersistenceContext
    private EntityManager em;

    // Số món đang nằm trong kho theo các trạng thái tồn kho
    public Map<UUID, Long> countInventoryByWarehouse(Collection<ProductStatus> statuses) {
        List<Object[]> rows = em.createQuery(
                        "select p.warehouse.id, count(p) from Product p "
                                + "where p.status in :statuses group by p.warehouse.id", Object[].class)
                .setParameter("statuses", statuses)
                .getResultList();
        return toMap(rows);
    }

    // Mỗi đơn gắn đúng một kho (orders.warehouse_id) và được đếm bằng count(distinct id),
    // không join order_items nên đơn nhiều món không bị đếm lặp
    public Map<UUID, Long> countActiveOrdersByWarehouse(Collection<OrderStatus> statuses) {
        List<Object[]> rows = em.createQuery(
                        "select o.warehouse.id, count(distinct o.id) from Order o "
                                + "where o.status in :statuses group by o.warehouse.id", Object[].class)
                .setParameter("statuses", statuses)
                .getResultList();
        return toMap(rows);
    }

    // Kho của Moderator nằm ở accounts.warehouse_id, hoặc ở moderator_profiles.warehouse_id với dữ liệu cũ;
    // cùng quy tắc ưu tiên với ModeratorAdminServiceImpl: account trước, profile sau. Chỉ đếm tài khoản ACTIVE.
    public Map<UUID, Long> countActiveModeratorsByWarehouse() {
        List<Object[]> rows = em.createQuery(
                        "select a.warehouse.id, mp.warehouse.id from Account a "
                                + "left join ModeratorProfile mp on mp.accountId = a.id "
                                + "where a.role = :role and a.status = :status", Object[].class)
                .setParameter("role", Role.MODERATOR)
                .setParameter("status", AccountStatus.ACTIVE)
                .getResultList();
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            UUID warehouseId = row[0] != null ? (UUID) row[0] : (UUID) row[1];
            if (warehouseId != null) {
                result.merge(warehouseId, 1L, Long::sum);
            }
        }
        return result;
    }

    private static Map<UUID, Long> toMap(List<Object[]> rows) {
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            result.put((UUID) row[0], ((Number) row[1]).longValue());
        }
        return result;
    }
}
