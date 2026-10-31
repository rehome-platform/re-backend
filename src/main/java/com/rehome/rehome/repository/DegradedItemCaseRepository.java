package com.rehome.rehome.repository;

import com.rehome.rehome.entity.marketplace.DegradedItemCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DegradedItemCaseRepository extends JpaRepository<DegradedItemCase, UUID> {
}
