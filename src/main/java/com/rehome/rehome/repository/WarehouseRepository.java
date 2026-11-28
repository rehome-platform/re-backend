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

    List<Warehouse> findAllByOrderByNameAsc();
    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
    List<Warehouse> findByStatusOrderByNameAsc(CatalogStatus status);

    List<Warehouse> findByIdInAndStatus(Collection<UUID> ids, CatalogStatus status);
}
