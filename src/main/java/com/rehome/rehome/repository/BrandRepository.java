package com.rehome.rehome.repository;

import com.rehome.rehome.entity.enums.CatalogStatus;
import com.rehome.rehome.entity.marketplace.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
    boolean existsByNameIgnoreCase(String name);
    List<Brand> findByStatusOrderByNameAsc(CatalogStatus status);
    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
