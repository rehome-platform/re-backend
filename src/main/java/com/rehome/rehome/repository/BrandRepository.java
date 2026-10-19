package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.Brand;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BrandRepository extends JpaRepository<Brand, UUID> {
}
