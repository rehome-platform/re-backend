package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.marketplace.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    List<Warehouse> findByStatus(CatalogStatus status);
    boolean existsByCode(String code);

    // API 141-143: Admin xem cả kho HIDDEN; tên kho không trùng (không phân biệt hoa thường)
    List<Warehouse> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
    List<Warehouse> findByStatusOrderByNameAsc(CatalogStatus status);

    // OC-02: chọn kho cho chiến dịch, chỉ nhận kho còn ACTIVE
    List<Warehouse> findByIdInAndStatus(Collection<UUID> ids, CatalogStatus status);
}
